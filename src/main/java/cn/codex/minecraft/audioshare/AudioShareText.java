package cn.codex.minecraft.audioshare;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

public final class AudioShareText {
    private static final Properties TEXT = load();

    private AudioShareText() { }

    private static Properties load() {
        Properties result = new Properties();
        try (var input = AudioShareText.class.getResourceAsStream("/mc_audio_share_text.properties")) {
            if (input == null) throw new IllegalStateException("Missing audio-share text resources");
            result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load audio-share text resources", error);
        }
        return result;
    }

    public static String get(String key) {
        String value = TEXT.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing audio-share text: " + key);
        return value;
    }

    public static String format(String key, Object... arguments) {
        return String.format(Locale.ROOT, get(key), arguments);
    }
}
