package cn.codex.minecraft.audioshare;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

public final class AudioShareController {
    private final Deque<short[]> frames = new ArrayDeque<>();
    private volatile Thread worker;
    private volatile boolean active;
    private volatile float gain = 1.0f;
    private volatile String statusKey = "status.not_started";
    private volatile String statusDetail;
    private volatile int level;
    private volatile boolean voiceConnected;
    private final AtomicLong capturedFrames = new AtomicLong();
    private final AtomicLong audibleFrames = new AtomicLong();
    private final AtomicLong mergedFrames = new AtomicLong();
    private final AtomicLong droppedFrames = new AtomicLong();
    private volatile long lastAudibleAt;

    public synchronized void start(int pid, boolean excludeMinecraft) {
        if (worker != null) return;
        capturedFrames.set(0);
        audibleFrames.set(0);
        mergedFrames.set(0);
        droppedFrames.set(0);
        lastAudibleAt = 0;
        level = 0;
        active = true;
        statusKey = "status.starting";
        statusDetail = null;
        AudioShareClient.LOGGER.info("Starting process loopback: pid={}, mode={}", pid,
                excludeMinecraft ? "all-except-minecraft" : "selected-application");
        worker = new Thread(() -> {
            try {
                WindowsLoopback.run(this, pid, excludeMinecraft);
                statusKey = active ? "status.capture_stopped" : "status.stopped";
            } catch (Throwable error) {
                String message = error.getMessage();
                statusDetail = message == null ? error.getClass().getSimpleName() : message;
                statusKey = "status.failed";
                AudioShareClient.LOGGER.error("Audio capture failed", error);
            } finally {
                active = false;
                synchronized (frames) { frames.clear(); }
                worker = null;
            }
        }, "MC Audio Share Capture");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        active = false;
        WindowsLoopback.stop();
        synchronized (frames) { frames.clear(); }
        statusKey = "status.stopped";
        statusDetail = null;
        level = 0;
    }

    void accept(short[] input) {
        if (!active) return;
        capturedFrames.incrementAndGet();
        int peak = 0;
        short[] output = new short[input.length];
        float volume = gain;
        for (int i = 0; i < input.length; i++) {
            int value = Math.round(input[i] * volume);
            value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
            output[i] = (short) value;
            peak = Math.max(peak, Math.abs(value));
        }
        level = Math.min(100, peak * 100 / 32768);
        // Process loopback also produces silent buffers. Sending them causes
        // Simple Voice Chat's talking icon to flash without audible sound.
        if (peak <= 32) return;
        audibleFrames.incrementAndGet();
        lastAudibleAt = System.currentTimeMillis();
        synchronized (frames) {
            if (frames.size() >= 15) {
                frames.removeFirst();
                droppedFrames.incrementAndGet();
            }
            frames.addLast(output);
        }
    }

    public short[] nextFrame() {
        synchronized (frames) {
            short[] frame = frames.pollFirst();
            if (frame != null) mergedFrames.incrementAndGet();
            return frame;
        }
    }

    public void setGain(float gain) { this.gain = Math.max(0f, Math.min(2f, gain)); }
    public float gain() { return gain; }
    public boolean active() { return active; }
    public String status() {
        if (!active) return "status.failed".equals(statusKey)
                ? AudioShareText.format(statusKey, statusDetail) : AudioShareText.get(statusKey);
        if (!voiceConnected) return AudioShareText.get("status.voice_disconnected");
        if (capturedFrames.get() > 30 && audibleFrames.get() == 0) return AudioShareText.get("status.silent");
        if (lastAudibleAt > 0 && System.currentTimeMillis() - lastAudibleAt > 3000) return AudioShareText.get("status.no_audio");
        return AudioShareText.get("status.capturing");
    }
    public int level() { return level; }
    public boolean voiceConnected() { return voiceConnected; }
    public void setVoiceConnected(boolean connected) {
        voiceConnected = connected;
        if (!connected) synchronized (frames) { frames.clear(); }
    }
    public String statistics() {
        return AudioShareText.format("status.statistics", capturedFrames.get(), audibleFrames.get(),
                mergedFrames.get(), droppedFrames.get());
    }
    public void logStatistics() {
        if (active) AudioShareClient.LOGGER.info("Audio share stats: captured={}, audible={}, merged={}, dropped={}, peak={}%, connected={}",
                capturedFrames.get(), audibleFrames.get(), mergedFrames.get(), droppedFrames.get(), level, voiceConnected);
    }
}
