package cn.codex.minecraft.audioshare;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

public final class AudioSharePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() { return AudioShareClient.MOD_ID; }

    @Override
    public void initialize(VoicechatApi api) {
        AudioShareClient.LOGGER.info("Simple Voice Chat audio merge registered");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class,
                event -> AudioShareClient.CONTROLLER.setVoiceConnected(event.isConnected()));
        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = AudioShareClient.CONTROLLER.nextFrame();
            if (frame != null) event.mergeAudio(frame);
        });
    }
}
