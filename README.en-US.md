# MC Audio Share (Fabric 26.2, experimental Windows build)

Press J in-game to open the audio-sharing screen. Choose all system audio (excluding Minecraft) or a specific application, adjust the share volume, and start sharing. Audio is mixed into Simple Voice Chat's microphone frames, so speaking and sharing can happen together. Closing the screen does not stop sharing.

This release supports Windows x64 only (Windows 10 build 20348 or later). It uses WASAPI Process Loopback and includes its native component inside the mod JAR. No separate helper process or server plugin is required. macOS support has not been implemented.

Install Minecraft 26.2, Fabric Loader 0.19.3 or later, Fabric API 0.156.0+26.2 or later, and a compatible Simple Voice Chat mod (API 2.6.20 or later). Simple Voice Chat must be connected to the server. This mod does not bypass push-to-talk, mute, or server rules; push-to-talk may require you to hold the talk key for the mixed audio to transmit.

The English and Chinese JARs have the same mod ID (`mc_audio_share`). Install **only one** of them. The English JAR uses English UI text regardless of the Minecraft language setting. Both use the newly supplied sharing and stopped-sharing icons, placed to the right of the unchanged Simple Voice Chat HUD icon.

This release was compiled and checked without launching Minecraft, as requested. Please report the results of your own in-game test.
