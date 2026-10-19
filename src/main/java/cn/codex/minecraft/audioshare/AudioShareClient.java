package cn.codex.minecraft.audioshare;

import cn.codex.minecraft.audioshare.client.AudioShareScreen;
import cn.codex.minecraft.audioshare.client.AudioShareHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AudioShareClient implements ClientModInitializer {
    public static final String MOD_ID = "mc_audio_share";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final AudioShareController CONTROLLER = new AudioShareController();
    private long lastStatisticsLog;

    @Override
    public void onInitializeClient() {
        AudioShareHud.register();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        KeyMapping openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_audio_share.open", GLFW.GLFW_KEY_BACKSLASH, category));
        KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_audio_share.toggle", GLFW.GLFW_KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new AudioShareScreen(null));
                }
            }
            while (toggleKey.consumeClick()) {
                if (client.player != null) CONTROLLER.toggleQuick();
            }
            long now = System.currentTimeMillis();
            if (CONTROLLER.active() && now - lastStatisticsLog >= 5000) {
                CONTROLLER.logStatistics();
                lastStatisticsLog = now;
            }
        });
        LOGGER.info("MC Audio Share initialized; open key=backslash, quick share key=F8");
    }
}
