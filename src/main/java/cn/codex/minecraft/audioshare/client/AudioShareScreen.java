package cn.codex.minecraft.audioshare.client;

import cn.codex.minecraft.audioshare.AudioShareClient;
import cn.codex.minecraft.audioshare.AudioShareController;
import cn.codex.minecraft.audioshare.AudioShareText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class AudioShareScreen extends Screen {
    private record Application(long pid, String name) { }

    private final Screen parent;
    private EditBox filter;
    private Button modeButton;
    private Button applicationButton;
    private Button startButton;
    private boolean allSound = true;
    private List<Application> applications = List.of();
    private int selected;
    private String lastFilter = "";

    public AudioShareScreen(Screen parent) {
        super(Component.literal(AudioShareText.get("screen.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 40);
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 110;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            allSound = !allSound;
            updateButtons();
        }).bounds(left, top, panelWidth, 22).build());
        filter = new EditBox(font, left, top + 39, panelWidth - 83, 22, Component.literal(AudioShareText.get("screen.filter_label")));
        filter.setHint(Component.literal(AudioShareText.get("screen.filter_hint")));
        filter.setMaxLength(80);
        addRenderableWidget(filter);
        addRenderableWidget(Button.builder(Component.literal(AudioShareText.get("screen.refresh")), button -> refresh())
                .bounds(left + panelWidth - 79, top + 39, 79, 22).build());
        applicationButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (!applications.isEmpty()) selected = (selected + 1) % applications.size();
            updateButtons();
        }).bounds(left, top + 69, panelWidth, 22).build());
        addRenderableWidget(new GainSlider(left, top + 102, panelWidth, 20));
        startButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggle())
                .bounds(left, top + 134, panelWidth, 23).build());
        refresh();
        updateButtons();
    }

    private void refresh() {
        String query = filter == null ? "" : filter.getValue().strip().toLowerCase(Locale.ROOT);
        lastFilter = query;
        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            applications = processes
                    .filter(process -> process.pid() != ProcessHandle.current().pid())
                    .map(process -> {
                        String command = process.info().command().orElse("");
                        if (command.isBlank()) return null;
                        String name = Path.of(command).getFileName().toString();
                        return new Application(process.pid(), name);
                    })
                    .filter(app -> app != null && app.name().toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(Application::name, String.CASE_INSENSITIVE_ORDER)
                            .thenComparingLong(Application::pid))
                    .toList();
        } catch (Exception error) {
            applications = List.of();
            AudioShareClient.LOGGER.warn("Cannot enumerate processes", error);
        }
        selected = 0;
        updateButtons();
    }

    private void updateButtons() {
        if (modeButton == null) return;
        modeButton.setMessage(Component.literal(AudioShareText.get(allSound
                ? "screen.mode_all" : "screen.mode_app")));
        applicationButton.active = !allSound && !applications.isEmpty();
        if (applications.isEmpty()) {
            applicationButton.setMessage(Component.literal(AudioShareText.get("screen.no_apps")));
        } else {
            selected = Math.min(selected, applications.size() - 1);
            Application app = applications.get(selected);
            applicationButton.setMessage(Component.literal(AudioShareText.format("screen.app_button",
                    selected + 1, applications.size(), app.name(), app.pid())));
        }
        startButton.setMessage(Component.literal(AudioShareText.get(AudioShareClient.CONTROLLER.active()
                ? "screen.stop" : "screen.start")));
    }

    private void toggle() {
        AudioShareController controller = AudioShareClient.CONTROLLER;
        if (controller.active()) controller.stop();
        else if (allSound) controller.start((int) ProcessHandle.current().pid(), true);
        else if (!applications.isEmpty()) controller.start((int) applications.get(selected).pid(), false);
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (filter != null && !filter.getValue().strip().toLowerCase(Locale.ROOT).equals(lastFilter)) refresh();
        updateButtons();
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int panelWidth = Math.min(460, width - 40);
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 110;
        graphics.centeredText(font, title, width / 2, top - 24, 0xFFFFFF);
        graphics.centeredText(font, AudioShareClient.CONTROLLER.status(), width / 2, top + 171, 0xDDDDDD);
        graphics.centeredText(font, AudioShareText.format("screen.level", AudioShareClient.CONTROLLER.level()),
                width / 2, top + 188, 0xAAAAAA);
        graphics.centeredText(font, AudioShareClient.CONTROLLER.voiceConnected()
                ? AudioShareText.get("screen.voice_connected") : AudioShareText.get("screen.voice_disconnected"),
                width / 2, top + 205,
                AudioShareClient.CONTROLLER.voiceConnected() ? 0x88CC88 : 0xFF8888);
        graphics.centeredText(font, AudioShareClient.CONTROLLER.statistics(), width / 2, top + 222, 0x888888);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static final class GainSlider extends AbstractSliderButton {
        GainSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), AudioShareClient.CONTROLLER.gain() / 2D);
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal(AudioShareText.format("screen.gain", Math.round(value * 200))));
        }

        @Override protected void applyValue() {
            AudioShareClient.CONTROLLER.setGain((float) value * 2F);
        }
    }
}
