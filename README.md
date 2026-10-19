# MC Audio Share

A Windows x64 client-side Fabric mod for Minecraft 26.2. It captures all system audio (excluding Minecraft) or audio from a selected application and mixes it into [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) alongside the microphone. No separate helper program or server plugin is needed.

**Status:** experimental. The 0.1.4 packages were compiled and inspected, but not tested in-game. Windows 10 build 20348 or newer is required; macOS is not supported yet.

[English guide](README.en-US.md) · [中文说明](README.zh-CN.md)

## Installation

Install Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API 0.156.0+26.2+, and a compatible Simple Voice Chat mod (API 2.6.20+). Put **one** MC Audio Share JAR in the client `mods` folder: Chinese (`-zh`) or English (`-en`). They share the same mod ID and cannot be installed together. Press **J** to open the sharing screen; the key can be changed in Minecraft's Controls settings.

The English JAR uses English UI text even when Minecraft is set to another language. Both JARs include the same icons and native Windows audio-capture component.

## Build

The project targets Java 25 and uses the Gradle wrapper. The native Windows x64 DLL is checked in so ordinary Java builds do not require a C++ compiler.

```powershell
./gradlew.bat -p versions/26.2 build englishJar
```

The regular Loom `remapJar` output is the Chinese package. `englishJar` repackages that remapped JAR with English-only text and keybinding resources. The files are produced under `versions/26.2/build/libs/`.

Native bridge source: [`native/windows/loopback.cpp`](native/windows/loopback.cpp). It uses the Windows WASAPI Process Loopback API.

## Limitations

- Audio is transmitted only while Simple Voice Chat is connected and its push-to-talk, mute, and server rules allow it.
- All-system-audio mode intentionally excludes Minecraft to avoid game-audio feedback.
- The HUD sharing icon is placed to the right of the existing Simple Voice Chat icon without moving that icon.
- This is an experimental Windows-only release; please report results from real game testing.

Licensed under MIT; see [LICENSE](LICENSE).

