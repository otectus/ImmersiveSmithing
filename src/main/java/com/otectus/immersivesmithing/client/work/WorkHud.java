package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The compact work status: what the piece is, its heat in words and bars (with the workable and ideal bands marked),
 * progress, the next step, and the player's actual key bindings. Shown beside the work while focused, and as a small
 * readout under the crosshair in World view. Heat and readiness are always named in words; colour only adds to them.
 */
public final class WorkHud {
    private static final int PANEL = 0xC0101010;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DIM = 0xFFA0A0A0;
    private static final int GOOD = 0xFF90E090;
    private static final int WARN = 0xFFFFB050;

    /** One line: text, or a bar with optional heat bands. */
    private record Line(Component text, int color, float bar, int barColor, @Nullable ThermalProfile bands, int[] heat) {
        static Line text(Component text, int color) {
            return new Line(text, color, -1F, 0, null, new int[0]);
        }
    }

    public static void renderFocused(GuiGraphics g, Font font, FocusController.Focus focus, @Nullable WorkTargets.Target target, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<Line> lines = lines(mc.level, focus.pos, focus.kind, target, true);
        int panelWidth = panelWidth(width);
        int x = ClientConfig.get(ClientConfig.CAMERA_SIDE) == ClientConfig.Side.LEFT ? width - panelWidth - 8 : 8;
        // Controls have a stable home at the bottom; changing heat/status never makes the key legend jump.
        List<Line> controls = hints(focus.kind, mc.level, focus.pos);
        int controlWidth = width < 480 ? width - 16 : panelWidth;
        int controlHeight = panelHeight(font, controls, controlWidth);
        int controlY = Math.max(8, height - 42 - controlHeight);
        draw(g, font, controls, width < 480 ? 8 : x, controlY, controlWidth, controlHeight);
        draw(g, font, lines, x, 8, panelWidth, Math.max(20, controlY - 14));
    }

    private static int panelWidth(int width) {
        return Math.min(196, Math.max(118, width / 3));
    }

    /** The horizontal span beside a work view's status panel, {left, right}, for other centred text. */
    public static int[] freeColumn(int width) {
        int panel = panelWidth(width) + 16;
        if (width - panel < 160) return new int[]{0, width};
        return ClientConfig.get(ClientConfig.CAMERA_SIDE) == ClientConfig.Side.LEFT ? new int[]{0, width - panel} : new int[]{panel, width};
    }

    /** On small screens the key legend spans the bottom; notices must finish above it. */
    public static int noticeBottom(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        FocusController.Focus focus = FocusController.current();
        if (width >= 480 || focus == null || mc.level == null) return height - 58;
        return height - 48 - panelHeight(mc.font, hints(focus.kind, mc.level, focus.pos), width - 16);
    }

    /** World view: a few lines under the crosshair while looking at a station with work on it. */
    public static void renderWorld(GuiGraphics g, Font font, Level level, BlockPos pos, WorkLeases.Station kind, int width, int height) {
        List<Line> lines = lines(level, pos, kind, null, false);
        if (lines.isEmpty()) return;
        int panelWidth = Math.min(170, width / 2 - 22);
        // Above and to the right of the crosshair: station messages stack upwards from the hotbar, below it.
        int h = Math.min(panelHeight(font, lines, panelWidth), height / 2 - 10);
        draw(g, font, lines, width / 2 + 14, Math.max(4, height / 2 - 6 - h), panelWidth, h);
    }

    private static int lineHeight(Font font, Line line, int panelWidth) {
        int text = font.split(line.text, panelWidth - 12).size() * 10;
        if (line.bands != null) return text + 6 * line.heat.length + 2;
        return line.bar >= 0 ? text + 7 : text;
    }

    private static int panelHeight(Font font, List<Line> lines, int panelWidth) {
        int h = 6;
        for (Line line : lines) h += lineHeight(font, line, panelWidth) + 1;
        return h;
    }

    private static void draw(GuiGraphics g, Font font, List<Line> lines, int x, int y, int panelWidth, int maxHeight) {
        List<Integer> heights = new ArrayList<>();
        for (Line line : lines) heights.add(lineHeight(font, line, panelWidth));
        int h = Math.min(panelHeight(font, lines, panelWidth), maxHeight);
        g.fill(x, y, x + panelWidth, y + h, PANEL);
        g.fill(x, y, x + panelWidth, y + 1, 0x806C6C6C);
        g.enableScissor(x + 4, y + 2, x + panelWidth - 4, y + h - 2);
        int cy = y + 4;
        for (int i = 0; i < lines.size() && cy < y + h - 6; i++) {
            Line line = lines.get(i);
            int textHeight = font.split(line.text, panelWidth - 12).size() * 10;
            int ly = cy;
            for (var seq : font.split(line.text, panelWidth - 12)) {
                g.drawString(font, seq, x + 6, ly, line.color, false);
                ly += 10;
            }
            if (line.bands != null) {
                int by = cy + textHeight + 1;
                for (int heat : line.heat) {
                    heatBar(g, x + 6, by, panelWidth - 12, 4, heat, line.bands);
                    by += 6;
                }
            } else if (line.bar >= 0) {
                int bx = x + 6, by = cy + textHeight + 1, bw = panelWidth - 12;
                g.fill(bx, by, bx + bw, by + 4, 0xFF303030);
                int fill = Math.round(bw * Math.max(0F, Math.min(1F, line.bar)));
                g.fill(bx, by, bx + fill, by + 4, line.barColor);
                if (fill > 0) g.fill(bx, by, bx + fill, by + 1, 0x40FFFFFF);
            }
            cy += heights.get(i) + 1;
        }
        g.disableScissor();
    }

    /** A heat bar with the workable band shaded and the ideal band outlined, and a marker at the current heat. */
    private static void heatBar(GuiGraphics g, int x, int y, int w, int h, int heat, ThermalProfile p) {
        g.fill(x, y, x + w, y + h, 0xFF282828);
        int wMin = x + w * p.workableMin() / ThermalProfile.SCALE, wMax = x + w * p.workableMax() / ThermalProfile.SCALE;
        int oMin = x + w * p.optimalMin() / ThermalProfile.SCALE, oMax = x + w * p.optimalMax() / ThermalProfile.SCALE;
        g.fill(wMin, y, wMax, y + h, 0xFF4A3A28);
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        g.fill(oMin, y, oMax, y + h, contrast ? 0xFFFFFFFF : 0xFF7A6030);
        int filled = x + Math.round(w * ThermalProfile.fraction(heat));
        int color = HeatColors.color(ThermalProfile.fraction(heat), 0x8090A0) | 0xFF000000;
        g.fill(x, y + 1, filled, y + h - 1, color);
        g.fill(filled - 1, y - 1, filled + 1, y + h + 1, contrast ? 0xFF000000 : 0xFFFFFFFF);
    }

    private static List<Line> lines(Level level, BlockPos pos, WorkLeases.Station kind, @Nullable WorkTargets.Target target, boolean focused) {
        List<Line> out = new ArrayList<>();
        boolean numbers = ClientConfig.get(ClientConfig.EXPANDED_STATUS);
        BlockEntity be = level.getBlockEntity(pos);
        switch (kind) {
            case FORGE -> {
                if (!(be instanceof SmithsForgeBlockEntity forge)) break;
                ForgeJob job = forge.job();
                if (job != null) {
                    out.add(Line.text(Component.translatable("hud.immersive_smithing.billet", job.target.getHoverName()), TEXT));
                    ThermalProfile.HeatBand band = job.thermal.band(ThermalRules.coolest(job.heat));
                    MutableComponent heat = Component.translatable("hud.immersive_smithing.heat", HeatColors.bandName(band));
                    if (numbers) heat.append(" (" + percent(ThermalRules.mean(job.heat)) + ")");
                    out.add(new Line(heat, HeatColors.bandColor(band), -1, 0, job.thermal, job.heat.clone()));
                    int spread = ThermalRules.spread(job.heat);
                    if (spread > ThermalRules.EVEN_SPREAD) out.add(Line.text(Component.translatable("hud.immersive_smithing.uneven"), WARN));
                    if (job.drawable()) out.add(Line.text(Component.translatable("hud.immersive_smithing.ready_to_draw"), GOOD));
                    if (ThermalRules.coolest(job.heat) > job.thermal.workableMax()) out.add(Line.text(Component.translatable("hud.immersive_smithing.too_hot"), WARN));
                } else {
                    var recipe = forge.plan() == null ? null : level.getRecipeManager().byKey(forge.plan()).map(r -> (Recipe<?>) r).orElse(null);
                    out.add(Line.text(recipe != null ? Component.translatable("hud.immersive_smithing.plan", recipe.getResultItem(level.registryAccess()).getHoverName())
                            : Component.translatable("hud.immersive_smithing.no_plan"), TEXT));
                    out.add(Line.text(Component.translatable("hud.immersive_smithing.molten", MaterialUnits.describe(forge.moltenUnits())), DIM));
                }
                out.add(new Line(Component.translatable("hud.immersive_smithing.air"), TEXT, forge.air() / 1000F, 0xFF9AC8FF, null, new int[0]));
                boolean lit = forge.getBlockState().hasProperty(com.otectus.immersivesmithing.block.SmithsForgeBlock.LIT)
                        && forge.getBlockState().getValue(com.otectus.immersivesmithing.block.SmithsForgeBlock.LIT);
                if (!lit && (job != null || forge.rest() != null)) out.add(Line.text(Component.translatable("hud.immersive_smithing.fire_out"), WARN));
                WorkpieceData rest = forge.rest();
                if (rest != null) {
                    ThermalProfile.HeatBand band = rest.process().thermal.band(ThermalRules.coolest(rest.process().heat));
                    out.add(Line.text(Component.translatable("hud.immersive_smithing.on_rest", rest.target().getHoverName(), HeatColors.bandName(band)),
                            HeatColors.bandColor(band)));
                }
            }
            case ANVIL -> {
                if (!(be instanceof SmithsAnvilBlockEntity anvil)) break;
                if (anvil.hasOutput()) {
                    QualityData q = QualityData.get(anvil.output()).orElse(null);
                    out.add(Line.text(Component.translatable("hud.immersive_smithing.finished", anvil.output().getHoverName(),
                            q != null ? q.quality().displayName() : Component.empty()), GOOD));
                    break;
                }
                WorkpieceData piece = anvil.workpiece();
                if (piece == null) break;
                ProcessState p = piece.process();
                out.add(Line.text(Component.translatable("hud.immersive_smithing.piece", piece.target().getHoverName(),
                        Component.translatable("stage.immersive_smithing." + p.stage.key())), TEXT));
                if (p.stage.shapeable()) {
                    int[] heat = anvil.currentHeat();
                    ThermalProfile.HeatBand band = p.thermal.band(ThermalRules.coolest(heat));
                    MutableComponent label = Component.translatable("hud.immersive_smithing.heat", HeatColors.bandName(band));
                    if (numbers) label.append(" (" + percent(ThermalRules.mean(heat)) + ")");
                    out.add(new Line(label, HeatColors.bandColor(band), -1, 0, p.thermal, heat));
                    if (band == ThermalProfile.HeatBand.COLD) out.add(Line.text(Component.translatable("hud.immersive_smithing.reheat"), WARN));
                    out.add(new Line(Component.translatable("hud.immersive_smithing.progress", p.progress() / 10), TEXT, p.progress() / 1000F, 0xFFE0C080, null, new int[0]));
                    int next = ShapingRules.nextOperation(p);
                    if (next >= 0) {
                        ProcessProfile.Operation o = p.profile.operations().get(next);
                        String key = o.face().matches(p.flipped) ? "hud.immersive_smithing.next" : "hud.immersive_smithing.next_flip";
                        out.add(Line.text(Component.translatable(key, AnvilActions.operationName(o)), GOOD));
                    } else {
                        out.add(Line.text(Component.translatable("hud.immersive_smithing.shaped"), GOOD));
                    }
                    if (focused && numbers) {
                        for (int i = 0; i < p.profile.operations().size(); i++) {
                            ProcessProfile.Operation o = p.profile.operations().get(i);
                            String mark = p.complete(i) ? "✔ " : o.face().matches(p.flipped) ? "• " : "↻ ";
                            MutableComponent text = Component.literal(mark).append(AnvilActions.operationName(o));
                            if (numbers) text.append(" " + p.progress(i) / 10 + "%");
                            if (p.marks[i] >= 200) text.append(Component.translatable("hud.immersive_smithing.marked").withStyle(ChatFormatting.GOLD));
                            out.add(Line.text(text, p.complete(i) ? DIM : TEXT));
                        }
                    }
                } else if (p.stage.quenched()) {
                    if (p.needsFitting()) {
                        out.add(Line.text(Component.translatable("hud.immersive_smithing.fit", p.socketsLeft()), GOOD));
                        for (ProcessState.Socket s : p.sockets) {
                            out.add(Line.text(Component.literal(s.attached() ? "✔ " : "• ")
                                    .append(Component.translatableWithFallback("socket.immersive_smithing." + s.name(), s.name()))
                                    .append(" — ").append(s.display().getHoverName()), s.attached() ? DIM : TEXT));
                        }
                    } else {
                        out.add(Line.text(Component.translatable("message.immersive_smithing.workpiece_to_trough"), TEXT));
                    }
                } else {
                    out.add(Line.text(Component.translatable("message.immersive_smithing.finish_quench_first"), WARN));
                }
            }
            case TROUGH -> {
                if (!(be instanceof SmithsTroughBlockEntity trough)) break;
                WorkpieceData piece = trough.quenchPiece();
                if (piece == null) {
                    out.add(Line.text(Component.translatable("message.immersive_smithing.trough_status", trough.quenchesLeft()), TEXT));
                    break;
                }
                out.add(Line.text(Component.translatable("hud.immersive_smithing.quenching", piece.target().getHoverName()), TEXT));
                if (!trough.immersed()) {
                    out.add(Line.text(Component.translatable("hud.immersive_smithing.lowering"), DIM));
                } else {
                    int progress = QuenchRules.progress(piece.process(), trough.quenchDuration());
                    out.add(new Line(Component.translatable("hud.immersive_smithing.hiss"), TEXT, progress / 1000F, progress >= 1000 ? 0xFF90E090 : 0xFFC0D8FF, null, new int[0]));
                    out.add(Line.text(Component.translatable(progress >= 1000 ? "hud.immersive_smithing.lift_now" : "hud.immersive_smithing.hold"),
                            progress >= 1000 ? GOOD : TEXT));
                }
            }
            case GRINDSTONE -> {
                if (!(be instanceof SmithsGrindstoneBlockEntity stone) || stone.piece().isEmpty()) break;
                out.add(Line.text(Component.translatable("hud.immersive_smithing.grinding", stone.piece().getHoverName()), TEXT));
                QualityData q = QualityData.get(stone.piece()).orElse(null);
                Component grade = q == null ? Component.translatable("quality.immersive_smithing.standard")
                        : (stone.efficacy() ? q.efficacyQuality() : q.durabilityQuality()).displayName();
                out.add(Line.text(Component.translatable("hud.immersive_smithing.aspect",
                        com.otectus.immersivesmithing.work.GrindstoneActions.aspectName(stone.efficacy()), grade), TEXT));
                if (stone.contact() != null) {
                    out.add(new Line(Component.translatable("hud.immersive_smithing.pass"), TEXT, stone.contactTicks() / (float) Math.max(1, stone.passTicks()),
                            0xFFFFD080, null, new int[0]));
                }
            }
        }
        // Keep the selected part above optional diagnostic/checklist lines, including at the smallest GUI size.
        if (target != null && focused) out.add(Math.min(1, out.size()), Line.text(Component.translatable("hud.immersive_smithing.target", target.label()), 0xFFFFF0A0));
        return out;
    }

    private static List<Line> hints(WorkLeases.Station kind, Level level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Component primary = WorkKeys.name(mc.options.keyAttack);
        Component secondary = WorkKeys.name(mc.options.keyUse);
        List<Line> out = new ArrayList<>();
        String base = switch (kind) {
            case FORGE -> "hud.immersive_smithing.keys.forge";
            case ANVIL -> level.getBlockEntity(pos) instanceof SmithsAnvilBlockEntity a && a.workpiece() != null && a.workpiece().stage().quenched()
                    ? "hud.immersive_smithing.keys.fitting" : "hud.immersive_smithing.keys.anvil";
            case TROUGH -> "hud.immersive_smithing.keys.trough";
            case GRINDSTONE -> "hud.immersive_smithing.keys.grindstone";
        };
        out.add(Line.text(Component.translatable(base, primary, secondary, WorkKeys.name(WorkKeys.FLIP), WorkKeys.name(WorkKeys.HEAVY)), DIM));
        out.add(Line.text(Component.translatable("hud.immersive_smithing.keys.common", WorkKeys.name(WorkKeys.PREVIOUS), WorkKeys.name(WorkKeys.NEXT),
                WorkKeys.name(WorkKeys.ACT)), DIM));
        return out;
    }

    private static String percent(int heat) {
        return Math.round(heat / 100F) + "%";
    }

    /** Spoken status for narration: the first lines in plain words. */
    public static Component narration(Level level, BlockPos pos, WorkLeases.Station kind, @Nullable WorkTargets.Target target) {
        MutableComponent all = Component.empty();
        for (Line line : lines(level, pos, kind, target, true)) {
            if (!all.getString().isEmpty()) all.append(". ");
            all.append(line.text);
        }
        return all;
    }

    private WorkHud() {}
}
