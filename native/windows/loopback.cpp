// Embedded Windows WASAPI process-loopback bridge. No external service is used.
#define _WIN32_WINNT 0x0A00
#include <windows.h>
#include <initguid.h>
#include <mmdeviceapi.h>
#include <audioclient.h>
#include <propidl.h>
#include <jni.h>
#include <atomic>
#include <cstdio>
#include <cstring>

// These definitions are part of the Windows 10 SDK but absent from some MinGW
// header releases. Their layout is the public audioclientactivationparams.h ABI.
struct LoopbackParams { DWORD targetProcessId; int mode; };
struct ActivationParams { int type; LoopbackParams process; };
static constexpr wchar_t PROCESS_DEVICE[] = L"VAD\\Process_Loopback";
static std::atomic<bool> running{false};

class Completion final : public IActivateAudioInterfaceCompletionHandler {
    std::atomic<ULONG> refs{1};
public:
    HANDLE done = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    HRESULT result = E_FAIL;
    IAudioClient* client = nullptr;

    ~Completion() {
        if (client) client->Release();
        if (done) CloseHandle(done);
    }
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID iid, void** object) override {
        if (!object) return E_POINTER;
        if (iid == IID_IUnknown || iid == IID_IActivateAudioInterfaceCompletionHandler || iid == IID_IAgileObject) {
            *object = static_cast<IActivateAudioInterfaceCompletionHandler*>(this);
            AddRef();
            return S_OK;
        }
        *object = nullptr;
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++refs; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG count = --refs;
        if (!count) delete this;
        return count;
    }
    HRESULT STDMETHODCALLTYPE ActivateCompleted(IActivateAudioInterfaceAsyncOperation* operation) override {
        IUnknown* unknown = nullptr;
        HRESULT activation = E_FAIL;
        result = operation->GetActivateResult(&activation, &unknown);
        if (SUCCEEDED(result)) result = activation;
        if (SUCCEEDED(result)) result = unknown->QueryInterface(IID_IAudioClient, reinterpret_cast<void**>(&client));
        if (unknown) unknown->Release();
        SetEvent(done);
        return S_OK;
    }
};

static void fail(JNIEnv* env, const char* action, HRESULT hr) {
    char message[180];
    std::snprintf(message, sizeof(message), "%s (Windows error 0x%08lX)", action, static_cast<unsigned long>(hr));
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_codex_minecraft_audioshare_WindowsLoopback_stopCapture(JNIEnv*, jclass) {
    running.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_codex_minecraft_audioshare_WindowsLoopback_capture(JNIEnv* env, jclass cls, jint pid, jboolean exclude) {
    if (running.exchange(true)) {
        fail(env, "Capture is already running", E_ABORT);
        return;
    }
    HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    if (FAILED(hr)) {
        running.store(false);
        fail(env, "COM initialization failed", hr);
        return;
    }
    Completion* completion = new Completion();
    IActivateAudioInterfaceAsyncOperation* operation = nullptr;
    IAudioCaptureClient* capture = nullptr;
    HANDLE ready = nullptr;
    bool started = false;
    const char* action = "Process audio activation failed";

    if (!completion->done) { hr = HRESULT_FROM_WIN32(GetLastError()); goto cleanup; }
    {
        ActivationParams params = {1, {static_cast<DWORD>(pid), exclude ? 1 : 0}};
        PROPVARIANT variant = {};
        variant.vt = VT_BLOB;
        variant.blob.cbSize = sizeof(params);
        variant.blob.pBlobData = reinterpret_cast<BYTE*>(&params);
        hr = ActivateAudioInterfaceAsync(PROCESS_DEVICE, IID_IAudioClient, &variant, completion, &operation);
    }
    if (FAILED(hr)) goto cleanup;
    {
        DWORD wait = WaitForSingleObject(completion->done, 15000);
        if (wait != WAIT_OBJECT_0) {
            // The activation callback may still arrive after a timeout. Retain
            // one reference rather than risk freeing its COM callback target.
            completion->AddRef();
            hr = wait == WAIT_TIMEOUT ? HRESULT_FROM_WIN32(ERROR_TIMEOUT) : HRESULT_FROM_WIN32(GetLastError());
            goto cleanup;
        }
    }
    hr = completion->result;
    if (FAILED(hr)) goto cleanup;
    if (!running.load()) { hr = S_OK; goto cleanup; }
    {
        WAVEFORMATEX format = {};
        format.wFormatTag = WAVE_FORMAT_PCM;
        format.nChannels = 2;
        format.nSamplesPerSec = 48000;
        format.wBitsPerSample = 16;
        format.nBlockAlign = 4;
        format.nAvgBytesPerSec = 192000;
        ready = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (!ready) { hr = HRESULT_FROM_WIN32(GetLastError()); goto cleanup; }
        action = "Audio format initialization failed";
        hr = completion->client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                AUDCLNT_STREAMFLAGS_LOOPBACK | AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM,
                0, 0, &format, nullptr);
        if (FAILED(hr)) goto cleanup;
    }
    action = "Audio capture service failed";
    hr = completion->client->GetService(IID_IAudioCaptureClient, reinterpret_cast<void**>(&capture));
    if (FAILED(hr)) goto cleanup;
    hr = completion->client->SetEventHandle(ready);
    if (FAILED(hr)) goto cleanup;
    hr = completion->client->Start();
    if (FAILED(hr)) goto cleanup;
    started = true;

    {
        jmethodID onAudio = env->GetStaticMethodID(cls, "onAudio", "([S)V");
        if (!onAudio) { hr = E_FAIL; goto cleanup; }
        jshort frame[960] = {};
        int used = 0;
        action = "Audio capture stream failed";
        while (running.load()) {
            WaitForSingleObject(ready, 40);
            UINT32 available = 0;
            while (running.load()) {
                hr = capture->GetNextPacketSize(&available);
                if (FAILED(hr) || available == 0) break;
                BYTE* data = nullptr;
                UINT32 count = 0;
                DWORD flags = 0;
                hr = capture->GetBuffer(&data, &count, &flags, nullptr, nullptr);
                if (FAILED(hr)) break;
                auto* samples = reinterpret_cast<const jshort*>(data);
                for (UINT32 i = 0; i < count; ++i) {
                    frame[used++] = (flags & AUDCLNT_BUFFERFLAGS_SILENT)
                            ? 0 : static_cast<jshort>((static_cast<int>(samples[i * 2]) + samples[i * 2 + 1]) / 2);
                    if (used == 960) {
                        jshortArray output = env->NewShortArray(960);
                        if (output) {
                            env->SetShortArrayRegion(output, 0, 960, frame);
                            env->CallStaticVoidMethod(cls, onAudio, output);
                            env->DeleteLocalRef(output);
                        }
                        used = 0;
                    }
                }
                HRESULT releaseResult = capture->ReleaseBuffer(count);
                if (FAILED(releaseResult)) hr = releaseResult;
                if (FAILED(hr) || env->ExceptionCheck()) break;
            }
            if (FAILED(hr) || env->ExceptionCheck()) break;
        }
    }

cleanup:
    running.store(false);
    if (started) completion->client->Stop();
    if (capture) capture->Release();
    if (ready) CloseHandle(ready);
    if (operation) operation->Release();
    completion->Release();
    CoUninitialize();
    if (FAILED(hr) && !env->ExceptionCheck()) fail(env, action, hr);
}
