package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.minigame.AnvilMinigame;
import com.otectus.immersivesmithing.minigame.AnvilPattern;
import com.otectus.immersivesmithing.minigame.AnvilRun;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.AnvilStrikePacket;
import com.otectus.immersivesmithing.network.packet.AnvilStrikeResultPacket;
import com.otectus.immersivesmithing.network.packet.CancelSessionPacket;
import com.otectus.immersivesmithing.network.packet.OpenAnvilScreenPacket;
import com.otectus.immersivesmithing.network.packet.SessionResultPacket;
import com.otectus.immersivesmithing.quality.SmithingQuality;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Anvil screen on a vanilla container panel: strike targets appear over a hot silhouette of the workpiece in a
 * dark work display, and the sidebar shows time, strikes and the projected efficacy. A shrinking ring shows the ideal
 * moment. Strikes are predicted locally and corrected by the server, which alone decides the score and whether the
 * timer ran out (Faulty).
 */
public class AnvilMinigameScreen extends WorkshopScreen {
    private enum Stage { READY, PLAYING, RESULT }

    private static final float OVERLAY_Z = 300F;

    private final OpenAnvilScreenPacket session;
    private final AnvilPattern pattern;
    private final AnvilRun run;
    private final long clientStartMs;
    private final RandomSource shakeRandom = RandomSource.create();
    private final Map<Integer, int[]> predictions = new HashMap<>();
    private final List<Spark> sparks = new ArrayList<>();
    private Stage stage = Stage.READY;
    private Button doneButton;
    private Button helpButton;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int area;
    private int sequence;
    private int cuedIndex = -1;
    private int cuedAttempt = -1;
    private float lastQuality = -1F;
    private long lastFeedbackMs;
    private long earlyMs = Long.MIN_VALUE;
    private long shakeUntil;
    private long flashUntil;
    private int resultScore;
    private int resultElapsed;
    private boolean resultFaulty;
    private boolean closedByServer;

    private record Spark(float x, float y, float vx, float vy, long born) {}

    public AnvilMinigameScreen(OpenAnvilScreenPacket session) {
        super(Component.translatable("screen.immersive_smithing.anvil"), 320, 240);
        this.session = session;
        this.pattern = session.pattern();
        this.run = new AnvilRun(pattern, session.seed());
        this.clientStartMs = Util.getMillis() + session.readyDelayMs();
    }

    public int sessionId() {
        return session.sessionId();
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(336, canvasWidth - 8);
        panelHeight = Math.min(236, canvasHeight - 4);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        area = Math.max(96, Math.min(176, Math.min(panelHeight - 50, panelWidth - 16 - 10 - 100)));
        int sx = sidebarX();
        doneButton = VanillaGui.button(sx, top + panelHeight - 28, sidebarWidth(), 20, Component.translatable("gui.done"), b -> onClose());
        doneButton.visible = stage == Stage.RESULT;
        addRenderableWidget(doneButton);
        helpButton = VanillaGui.button(left + panelWidth - 22, top + 4, 16, 14, Component.literal("?"), b -> {});
        helpButton.setTooltip(Tooltip.create(Component.translatable("screen.immersive_smithing.anvil.help")));
        addRenderableWidget(helpButton);
    }

    private int areaX() {
        return left + 10;
    }

    private int areaY() {
        return top + 24;
    }

    private int sidebarX() {
        return left + 10 + area + 10;
    }

    private int sidebarWidth() {
        return panelWidth - (sidebarX() - left) - 8;
    }

    private int elapsed() {
        return (int) (Util.getMillis() - clientStartMs);
    }

    // ------------------------------------------------------------------ server events

    public void onStrikeResult(AnvilStrikeResultPacket packet) {
        if (packet.sessionId() != session.sessionId()) return;
        int[] predicted = predictions.remove(packet.sequence());
        boolean matches = predicted != null && predicted[0] == packet.index() && predicted[1] == packet.attempt()
                && predicted[2] == packet.spawnMs() && predicted[3] == packet.samples();
        if (!matches) {
            run.correct(packet.index(), packet.attempt(), packet.spawnMs(), packet.sum(), packet.samples());
            predictions.keySet().removeIf(seq -> seq > packet.sequence());
        }
        if (!packet.ignored()) {
            lastQuality = packet.hit() ? packet.quality() : 0F;
        }
    }

    public void onResult(SessionResultPacket packet) {
        if (packet.sessionId() != session.sessionId()) return;
        resultScore = packet.score();
        resultFaulty = packet.timedOut();
        resultElapsed = Math.max(0, elapsed());
        stage = Stage.RESULT;
        doneButton.visible = true;
    }

    public void closeByServer() {
        closedByServer = true;
        onClose();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (stage == Stage.PLAYING && button == 0) {
            double nx = (mouseX - areaX()) / area;
            double ny = (mouseY - areaY()) / area;
            if (nx >= 0 && nx <= 1 && ny >= 0 && ny <= 1) {
                strike((float) nx, (float) ny);
                return true;
            }
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Strikes are aimed with the mouse; Space or Enter must never press a focused button mid-session.
        boolean actionKey = key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (actionKey && stage != Stage.RESULT) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void strike(float x, float y) {
        int now = elapsed();
        if (now < 0 || now > session.allowedMs() || run.isComplete()) return;
        AnvilMinigame.Target target = run.currentTarget();
        AnvilRun.StrikeResult result = run.strike(now, x, y);
        if (result == null) { // between targets or too fast: nothing is sent
            // A click before the next target appears is explained; the 80 ms double-click guard stays silent.
            if (now < run.spawnMs()) earlyMs = Util.getMillis();
            return;
        }
        int seq = ++sequence;
        predictions.put(seq, new int[]{run.index(), run.attempt(), run.spawnMs(), run.samples()});
        ModNetwork.CHANNEL.sendToServer(new AnvilStrikePacket(session.sessionId(), seq, x, y, now));
        lastQuality = result.hit() ? result.quality() : 0F;
        lastFeedbackMs = Util.getMillis();
        if (result.hit()) {
            boolean perfect = result.quality() >= 0.9F;
            SmithingGui.playUi(perfect ? ModSounds.ANVIL_STRIKE_PERFECT.get() : ModSounds.ANVIL_STRIKE.get(), 0.9F + result.quality() * 0.2F, 0.9F);
            if (!ClientConfig.get(ClientConfig.REDUCED_SCREEN_SHAKE)) shakeUntil = Util.getMillis() + 120;
            if (perfect && !ClientConfig.get(ClientConfig.REDUCED_FLASHES)) flashUntil = Util.getMillis() + 140;
            int count = ClientConfig.scaleParticles(perfect ? 8 : 5);
            for (int i = 0; i < count; i++) {
                float angle = shakeRandom.nextFloat() * (float) Math.PI * 2F;
                float speed = 30F + shakeRandom.nextFloat() * 50F;
                sparks.add(new Spark(areaX() + target.x() * area, areaY() + target.y() * area,
                        (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 20F, Util.getMillis()));
            }
        } else {
            SmithingGui.playUi(ModSounds.ANVIL_MISS.get(), 1.0F, 0.7F);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        if (stage == Stage.READY && elapsed() >= 0) stage = Stage.PLAYING;
        long now = Util.getMillis();
        sparks.removeIf(s -> now - s.born() > 300);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (stage != Stage.RESULT && !closedByServer) {
            ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(session.sessionId()));
        }
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long wall = Util.getMillis();
        float shakeX = 0F;
        float shakeY = 0F;
        if (wall < shakeUntil) {
            float amount = (shakeUntil - wall) / 120F * 2F;
            shakeX = (shakeRandom.nextFloat() - 0.5F) * amount;
            shakeY = (shakeRandom.nextFloat() - 0.5F) * amount;
        }
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = Component.translatable("screen.immersive_smithing.anvil.title", session.target().getHoverName());
        int titleWidth = panelWidth - 40;
        g.drawString(font, VanillaGui.clipped(font, title, titleWidth), left + 8, top + 7, VanillaGui.LABEL, false);

        int ax = areaX();
        int ay = areaY();
        VanillaGui.display(g, ax - 2, ay - 2, area + 4, area + 4);
        // Quiet scored lines suggest a work surface without competing with the targets.
        for (int i = 1; i < 8; i++) {
            int step = area * i / 8;
            g.fill(ax + step, ay, ax + step + 1, ay + area, 0x18FFFFFF);
            g.fill(ax, ay + step, ax + area, ay + step + 1, 0x18FFFFFF);
        }
        renderSilhouette(g, ax, ay, shakeX, shakeY);
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);

        switch (stage) {
            case READY -> {
                int remaining = -elapsed();
                g.fill(ax, ay, ax + area, ay + area, 0x90000000);
                VanillaGui.centered(g, font, Component.translatable("screen.immersive_smithing.ready", Math.max(1, (remaining + 999) / 1000)),
                        ax + area / 2, ay + area / 2 - 12, 0xFFFFD66B);
                VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.anvil.instructions"),
                        ax + area / 2, ay + area / 2 + 2, area - 16, VanillaGui.DISPLAY_TEXT, false);
            }
            case PLAYING -> {
                scissor(g, ax, ay, ax + area, ay + area);
                renderTargets(g, ax, ay);
                g.disableScissor();
            }
            case RESULT -> renderResult(g, ax, ay);
        }
        renderSparks(g, wall);
        if (wall < flashUntil) {
            int alpha = (int) (0x50 * (flashUntil - wall) / 140F);
            g.fill(ax, ay, ax + area, ay + area, (alpha << 24) | 0xFFF0C0);
        }
        g.pose().popPose();
        renderSidebar(g);
        if (stage != Stage.RESULT) {
            VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, Component.translatable("screen.immersive_smithing.anvil.timing"), area)),
                    ax + area / 2, ay + area + 6, VanillaGui.LABEL_DIM);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (VanillaGui.isClipped(font, title, titleWidth) && mouseX >= left + 8 && mouseX < left + 8 + titleWidth
                && mouseY >= top + 5 && mouseY < top + 17) {
            tooltip(title);
        }
    }

    private void renderSilhouette(GuiGraphics g, int ax, int ay, float shakeX, float shakeY) {
        float scale = (area - 16) / 16F;
        g.pose().pushPose();
        g.pose().translate(ax + 8 + shakeX, ay + 8 + shakeY, 0);
        g.pose().scale(scale, scale, 1F);
        g.setColor(0.62F, 0.3F, 0.16F, 1.0F);
        g.renderItem(session.target(), 0, 0);
        g.setColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
        // GUI items render at z ~150; everything meant to sit on top of the silhouette goes above that.
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        g.fill(ax, ay, ax + area, ay + area, 0x66140600);
        g.pose().popPose();
    }

    private void renderTargets(GuiGraphics g, int ax, int ay) {
        int now = elapsed();
        if (now > session.allowedMs() || run.isComplete()) {
            VanillaGui.centered(g, font, Component.translatable(now > session.allowedMs() ? "screen.immersive_smithing.times_up" : "screen.immersive_smithing.finishing"),
                    ax + area / 2, ay + area / 2, VanillaGui.DISPLAY_TEXT);
            return;
        }
        run.advanceTo(now);
        float radius = pattern.radius() * area;
        // The visible hit boundary always matches the server's radius. Accessibility enlarges the
        // centre marker and approaching ring, without suggesting that misses outside it will count.
        float drawRadius = radius;
        // Faint preview of the next target helps the smith read the rhythm.
        if (run.index() + 1 < pattern.strikes()) {
            AnvilMinigame.Target next = AnvilMinigame.target(pattern, session.seed(), run.index() + 1, 0);
            SmithingGui.circle(g, ax + next.x() * area, ay + next.y() * area, drawRadius, true, 0x50FFFFFF);
        }
        int local = now - run.spawnMs();
        if (local < 0) return;
        AnvilMinigame.Target target = run.currentTarget();
        float cx = ax + target.x() * area;
        float cy = ay + target.y() * area;
        int ideal = AnvilMinigame.idealMs(pattern);
        float fade = local > ideal ? 1F - (local - ideal) / (float) Math.max(1, pattern.lifetimeMs() - ideal) * 0.6F : 1F;
        int alpha = Math.max(150, Math.round(255 * fade));
        // A dark halo keeps the target readable over any silhouette.
        SmithingGui.circle(g, cx, cy, drawRadius + 3, false, (Math.round(alpha * 0.7F) << 24));
        SmithingGui.circle(g, cx, cy, drawRadius, false, (Math.round(alpha * 0.8F) << 24) | (SmithingGui.targetColor() & 0xFFFFFF));
        SmithingGui.circle(g, cx, cy, drawRadius * 0.35F, false, (alpha << 24) | (SmithingGui.perfectColor() & 0xFFFFFF));
        // Approach ring: closes onto the target at the ideal moment.
        float approach = local < ideal ? 1F + 2F * SmithingGui.markerScale() * (1F - local / (float) ideal) : 1F - 0.3F * (local - ideal) / (float) Math.max(1, pattern.lifetimeMs() - ideal);
        SmithingGui.circle(g, cx, cy, drawRadius * approach + 1, true, (alpha << 24));
        SmithingGui.circle(g, cx, cy, drawRadius * approach, true, (alpha << 24) | 0xFFFFFF);
        int cross = Math.round(2 * SmithingGui.markerScale());
        int centerX = Math.round(cx), centerY = Math.round(cy);
        g.fill(centerX - cross, centerY, centerX + cross + 1, centerY + 1, 0xFF172027);
        g.fill(centerX, centerY - cross, centerX + 1, centerY + cross + 1, 0xFF172027);
        if (local >= ideal && (cuedIndex != run.index() || cuedAttempt != run.attempt())) {
            cuedIndex = run.index();
            cuedAttempt = run.attempt();
            SmithingGui.playCue();
        }
    }

    private void renderResult(GuiGraphics g, int ax, int ay) {
        g.fill(ax, ay, ax + area, ay + area, 0xB0000000);
        int cx = ax + area / 2;
        int cursor = ay + area / 3;
        if (resultFaulty) {
            cursor += 10 * VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.anvil.faulty"),
                    cx, cursor, area - 16, SmithingGui.missColor(), false) + 6;
            cursor += 10 * VanillaGui.wrappedCentered(g, font, SmithingQuality.FAULTY.displayName(), cx, cursor, area - 16, SmithingGui.missColor(), false);
        } else {
            cursor += 10 * VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.anvil.complete"),
                    cx, cursor, area - 16, 0xFFFFD66B, false) + 6;
            Component quality = SmithingQuality.fromScore(resultScore).displayName();
            if (ClientConfig.get(ClientConfig.SHOW_NUMERIC_QUALITY_SCORES)) quality = Component.translatable("screen.immersive_smithing.quality_with_score", quality, resultScore);
            cursor += 10 * VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.anvil.efficacy", quality),
                    cx, cursor, area - 16, VanillaGui.DISPLAY_TEXT, false);
        }
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.anvil.next"), cx, cursor + 10, area - 16,
                VanillaGui.DISPLAY_TEXT_DIM, false);
    }

    private void renderSidebar(GuiGraphics g) {
        int sx = sidebarX();
        int sw = sidebarWidth();
        int now = stage == Stage.RESULT ? resultElapsed : Math.max(0, elapsed());
        float remaining = stage == Stage.READY ? 1F : 1F - now / (float) Math.max(1, session.allowedMs());
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_smithing.anvil.step"), sw), sx, top + 24, VanillaGui.LABEL, false);

        g.drawString(font, Component.translatable("screen.immersive_smithing.anvil.time"), sx, top + 40, VanillaGui.LABEL, false);
        Component seconds = Component.translatable("screen.immersive_smithing.seconds_short", Math.max(0, (session.allowedMs() - now + 999) / 1000));
        g.drawString(font, seconds, sx + sw - font.width(seconds), top + 40, VanillaGui.LABEL, false);
        SmithingGui.timeBar(g, sx + 1, top + 52, sw - 2, 5, remaining);

        int completed = stage == Stage.RESULT && !resultFaulty ? pattern.strikes() : Math.min(run.index(), pattern.strikes());
        g.drawString(font, Component.translatable("screen.immersive_smithing.anvil.strikes_label"), sx, top + 66, VanillaGui.LABEL, false);
        Component strikes = Component.translatable("screen.immersive_smithing.anvil.strikes", completed, pattern.strikes());
        g.drawString(font, strikes, sx + sw - font.width(strikes), top + 66, VanillaGui.LABEL, false);
        int count = pattern.strikes();
        for (int i = 0; i < count; i++) {
            int x = sx + i * sw / count;
            int end = sx + (i + 1) * sw / count - 1;
            VanillaGui.inset(g, x, top + 78, Math.max(2, end - x), 6);
            if (i < completed) g.fill(x + 1, top + 79, Math.max(x + 1, end - 1), top + 83, 0xFF5A5A5A);
        }

        g.drawString(font, Component.translatable("screen.immersive_smithing.anvil.quality_label"), sx, top + 92, VanillaGui.LABEL, false);
        Component quality = stage == Stage.RESULT ? (resultFaulty ? SmithingQuality.FAULTY : SmithingQuality.fromScore(resultScore)).displayName()
                : run.samples() > 0 ? SmithingQuality.fromScore(run.score()).displayName() : Component.literal("-");
        VanillaGui.display(g, sx, top + 103, sw, 18);
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, quality, sw - 8)), sx + sw / 2, top + 108, VanillaGui.DISPLAY_TEXT);

        if (stage != Stage.RESULT) {
            long wall = Util.getMillis();
            int labelMs = ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS);
            boolean early = earlyMs > lastFeedbackMs && wall - earlyMs < labelMs;
            boolean feedback = !early && lastQuality >= 0 && wall - lastFeedbackMs < labelMs;
            Component label = early ? Component.translatable("screen.immersive_smithing.anvil.too_early")
                    : feedback ? SmithingGui.rating(lastQuality) : Component.translatable("screen.immersive_smithing.anvil.aim");
            int lines = font.split(label, sw - 8).size();
            VanillaGui.display(g, sx, top + 128, sw, 8 + lines * 10);
            VanillaGui.wrappedCentered(g, font, label, sx + sw / 2, top + 133, sw - 8,
                    early ? 0xFFFFD66B : feedback ? SmithingGui.ratingColor(lastQuality) : VanillaGui.DISPLAY_TEXT_DIM, false);
        }
    }

    private void renderSparks(GuiGraphics g, long wall) {
        for (Spark s : sparks) {
            float t = (wall - s.born()) / 1000F;
            if (t > 0.3F) continue;
            int x = Math.round(s.x() + s.vx() * t);
            int y = Math.round(s.y() + s.vy() * t + 120F * t * t);
            int alpha = Math.round(255 * (1F - t / 0.3F));
            g.fill(x, y, x + 2, y + 2, (alpha << 24) | 0xFFD070);
        }
    }
}
