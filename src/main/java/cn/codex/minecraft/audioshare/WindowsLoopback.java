package cn.codex.minecraft.audioshare;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

final class WindowsLoopback {
    private static final String RESOURCE = "/native/windows-x64/mc_audio_share.dll";
    private static volatile AudioShareController owner;
    private static volatile boolean loaded;

    static synchronized void load() throws Exception {
        if (loaded) return;
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!os.contains("win") || !(arch.equals("amd64") || arch.equals("x86_64"))) {
            throw new IllegalStateException(AudioShareText.get("error.windows_only"));
        }
        byte[] bytes;
        try (InputStream input = WindowsLoopback.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException(AudioShareText.get("error.missing_dll"));
            bytes = input.readAllBytes();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        String hash = HexFormat.of().formatHex(digest).substring(0, 16);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "mc-audio-share", hash);
        Files.createDirectories(directory);
        Path library = directory.resolve("mc_audio_share.dll");
        if (!Files.exists(library) || Files.size(library) != bytes.length) Files.write(library, bytes);
        System.load(library.toAbsolutePath().toString());
        loaded = true;
    }

    static void run(AudioShareController controller, int pid, boolean exclude) throws Exception {
        load();
        if (!controller.active()) return;
        owner = controller;
        try { capture(pid, exclude); }
        finally { owner = null; }
    }

    private static void onAudio(short[] samples) {
        AudioShareController target = owner;
        if (target != null) target.accept(samples);
    }

    static void stop() {
        if (loaded) stopCapture();
    }

    private static native void capture(int pid, boolean exclude);
    private static native void stopCapture();
}
