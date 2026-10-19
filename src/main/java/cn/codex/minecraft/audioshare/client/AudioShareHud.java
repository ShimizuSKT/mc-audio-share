package cn.codex.minecraft.audioshare.client;

import cn.codex.minecraft.audioshare.AudioShareClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AudioShareHud {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(AudioShareClient.MOD_ID, "sharing_status");
    private static final Identifier SHARING = Identifier.fromNamespaceAndPath(AudioShareClient.MOD_ID, "textures/gui/sharing.png");
    private static final Identifier STOPPED = Identifier.fromNamespaceAndPath(AudioShareClient.MOD_ID, "textures/gui/sharing_disabled.png");
    private static final int SOURCE_SIZE = 480;
    private static final int GAP = 4;
    private static final VoicechatHudLayout VOICECHAT_HUD = new VoicechatHudLayout();

    private AudioShareHud() { }

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        VoicechatHudLayout.Settings settings = VOICECHAT_HUD.read();
        int iconSize = Math.max(4, (int) Math.round(16D * settings.scale()));
        // SVC anchors negative coordinates to the right/bottom of the GUI.
        // Keep its own icon untouched, then place ours immediately to its right.
        int voiceX = settings.x() < 0 ? width + settings.x() - iconSize : settings.x();
        int voiceY = settings.y() < 0 ? height + settings.y() - iconSize : settings.y();
        int x = voiceX + iconSize + GAP;
        int y = voiceY;
        Identifier texture = AudioShareClient.CONTROLLER.active() ? SHARING : STOPPED;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0F, 0F,
                iconSize, iconSize, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE);
    }

    /**
     * Read SVC's current HUD layout without changing its config. This keeps
     * the icons aligned even when the user moves/scales the SVC icon.
     */
    private static final class VoicechatHudLayout {
        private record Settings(int x, int y, double scale) { }

        private Field configField;
        private Field xField;
        private Field yField;
        private Field scaleField;
        private Method getMethod;
        private boolean unavailable;

        private boolean resolve() {
            if (unavailable) return false;
            if (configField != null) return true;
            try {
                Class<?> clientClass = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
                Class<?> configClass = Class.forName("de.maxhenkel.voicechat.config.ClientConfig");
                Class<?> entryClass = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");
                configField = clientClass.getField("CLIENT_CONFIG");
                xField = configClass.getField("hudIconPosX");
                yField = configClass.getField("hudIconPosY");
                scaleField = configClass.getField("hudIconScale");
                getMethod = entryClass.getMethod("get");
                return true;
            } catch (ReflectiveOperationException | LinkageError error) {
                unavailable = true;
                AudioShareClient.LOGGER.warn("Cannot read Simple Voice Chat HUD layout; using its default position", error);
                return false;
            }
        }

        Settings read() {
            if (!resolve()) return new Settings(16, -16, 1D);
            try {
                Object config = configField.get(null);
                if (config == null) return new Settings(16, -16, 1D);
                int x = ((Number) getMethod.invoke(xField.get(config))).intValue();
                int y = ((Number) getMethod.invoke(yField.get(config))).intValue();
                double scale = ((Number) getMethod.invoke(scaleField.get(config))).doubleValue();
                return new Settings(x, y, scale);
            } catch (ReflectiveOperationException error) {
                return new Settings(16, -16, 1D);
            }
        }
    }
}
