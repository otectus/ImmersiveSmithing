package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Optional refinement at the grindstone, as visible work: rest a finished piece at the wheel, choose the aspect (shown with
 * its exact price and step), and hold its edge to the turning wheel for one pass. Nothing is charged on approach or focus;
 * the XP and the score change are committed together when a pass completes, after revalidating the piece and the smith's
 * levels. The configured step, cost formula, cap, neutral start for ungraded eligible pieces and the Faulty restriction are
 * unchanged from the click-to-confirm grindstone. Refinement never fires a crafting reward.
 */
public final class GrindstoneActions {

    public static void use(ServerPlayer player, SmithsGrindstoneBlockEntity stone, InteractionHand hand, ItemStack held) {
        if (!ServerConfig.get(ServerConfig.ENABLE_SMITH_GRINDSTONE)) {
            Feedback.fail(player, "grindstone_disabled");
            return;
        }
        if (stone.piece().isEmpty()) {
            if (held.isEmpty()) Feedback.info(player, "grindstone_help");
            else place(player, stone, hand);
            return;
        }
        if (!held.isEmpty()) {
            Feedback.fail(player, "grindstone_occupied");
            return;
        }
        if (player.isSecondaryUseActive()) take(player, stone);
        else hold(player, stone);
    }

    /** Rests a finished, eligible, non-Faulty piece at the wheel; the price for each aspect is shown at once. */
    public static void place(ServerPlayer player, SmithsGrindstoneBlockEntity stone, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        QualityData quality = quality(held);
        if (quality == null) {
            Feedback.info(player, "grindstone_help");
            return;
        }
        if (quality.faulty()) {
            Feedback.fail(player, "grindstone_faulty");
            return;
        }
        if (WorkLeases.acquire(player, stone.getBlockPos(), WorkLeases.Station.GRINDSTONE, false) == null) return;
        stone.setPiece(held.split(1));
        player.level().playSound(null, stone.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
        WorkEvents.send(player.serverLevel(), stone.getBlockPos(), WorkEvents.PLACE, player, 0, StationLayout.GRINDSTONE_REST, 1F, 0);
        quote(player, stone);
    }

    public static void take(ServerPlayer player, SmithsGrindstoneBlockEntity stone) {
        if (stone.piece().isEmpty()) {
            Feedback.fail(player, "grindstone_help");
            return;
        }
        if (stone.contact() != null && !stone.contact().equals(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, stone.takePiece());
        WorkEvents.send(player.serverLevel(), stone.getBlockPos(), WorkEvents.TAKE, player, 0, StationLayout.GRINDSTONE_REST, 1F, 0);
    }

    public static void aspect(ServerPlayer player, SmithsGrindstoneBlockEntity stone, boolean efficacy) {
        stone.setEfficacy(efficacy);
        if (!stone.piece().isEmpty()) quote(player, stone);
    }

    /** Holds the edge to the wheel (and keeps holding while requests keep coming). Checks, but does not charge, the price. */
    public static void hold(ServerPlayer player, SmithsGrindstoneBlockEntity stone) {
        if (!ServerConfig.get(ServerConfig.ENABLE_SMITH_GRINDSTONE)) {
            Feedback.fail(player, "grindstone_disabled");
            return;
        }
        ItemStack piece = stone.piece();
        QualityData quality = quality(piece);
        if (quality == null) {
            Feedback.fail(player, "grindstone_help");
            return;
        }
        if (quality.faulty()) {
            Feedback.fail(player, "grindstone_faulty");
            return;
        }
        int score = stone.efficacy() ? quality.anvilScore() : quality.forgeScore();
        if (score >= 100) {
            Feedback.fail(player, "grindstone_maxed", aspectName(stone.efficacy()));
            return;
        }
        boolean starting = stone.contact() == null;
        int cost = SmithsGrindstoneBlock.levelCost(player, piece, score);
        if (starting && !player.getAbilities().instabuild && player.experienceLevel < cost) {
            Feedback.fail(player, "grindstone_no_xp", cost);
            return;
        }
        if (!stone.holdContact(player)) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        WorkLeases.keepAlive(player);
    }

    public static void stop(ServerPlayer player, SmithsGrindstoneBlockEntity stone) {
        if (player.getUUID().equals(stone.contact())) stone.stopContact();
    }

    /** Sparks and the scrape while an edge is on the wheel: only from accepted contact. */
    public static void contactEffects(ServerLevel level, SmithsGrindstoneBlockEntity stone) {
        WorkEvents.send(level, stone.getBlockPos(), WorkEvents.GRIND, null, 0, StationLayout.WHEEL_CONTACT,
                Math.min(1F, stone.contactTicks() / (float) Math.max(1, stone.passTicks())), stone.efficacy() ? 1 : 0);
    }

    /**
     * A pass held for its full time. The smith, the piece, the score and the price are checked again now; the levels are
     * taken and the refined score written in the same step. Anything that fails ends the pass with nothing charged.
     */
    public static void finishPass(ServerLevel level, SmithsGrindstoneBlockEntity stone) {
        ServerPlayer player = stone.contactPlayer();
        if (player == null && stone.contact() != null && level.getServer() != null) player = level.getServer().getPlayerList().getPlayer(stone.contact());
        stone.stopContact();
        if (player == null || !player.isAlive() || player.level() != level || !WorkLeases.inReach(player, stone.getBlockPos())) return;
        ItemStack piece = stone.piece();
        QualityData quality = QualityData.get(piece).orElse(null);
        boolean neutralStart = false;
        if (quality == null) {
            if (!QualityEligibility.canCarryQuality(piece)) return;
            quality = QualityEligibility.neutral();
            neutralStart = true;
        }
        if (quality.faulty()) return;
        boolean efficacy = stone.efficacy();
        int score = efficacy ? quality.anvilScore() : quality.forgeScore();
        if (score >= 100) return;
        int cost = SmithsGrindstoneBlock.levelCost(player, piece, score);
        if (!player.getAbilities().instabuild && player.experienceLevel < cost) {
            Feedback.fail(player, "grindstone_no_xp", cost);
            return;
        }
        int next = Math.min(100, score + ServerConfig.get(ServerConfig.GRINDSTONE_SCORE_STEP));
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-cost);
        QualityData refined = efficacy ? quality.withAnvilScore(next) : quality.withForgeScore(next);
        refined.apply(piece);
        stone.pieceChanged();
        level.playSound(null, stone.getBlockPos(), ModSounds.GRINDSTONE_REFINE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        WorkEvents.send(level, stone.getBlockPos(), WorkEvents.REFINED, player, 0, StationLayout.WHEEL_CONTACT, 1F, efficacy ? 1 : 0);
        Feedback.info(player, neutralStart ? "grindstone_refined_neutral" : "grindstone_refined", aspectName(efficacy),
                (efficacy ? refined.efficacyQuality() : refined.durabilityQuality()).displayName());
    }

    /** Tells the smith exactly what one pass of the chosen aspect costs and changes. */
    public static void quote(ServerPlayer player, SmithsGrindstoneBlockEntity stone) {
        QualityData quality = quality(stone.piece());
        if (quality == null) return;
        boolean efficacy = stone.efficacy();
        int score = efficacy ? quality.anvilScore() : quality.forgeScore();
        if (score >= 100) {
            Feedback.info(player, "grindstone_maxed", aspectName(efficacy));
            return;
        }
        int step = Math.min(100, score + ServerConfig.get(ServerConfig.GRINDSTONE_SCORE_STEP)) - score;
        Feedback.info(player, "grindstone_quote", aspectName(efficacy), step, SmithsGrindstoneBlock.levelCost(player, stone.piece(), score));
    }

    @Nullable
    static QualityData quality(ItemStack stack) {
        if (stack.isEmpty()) return null;
        QualityData quality = QualityData.get(stack).orElse(null);
        if (quality == null && QualityEligibility.canCarryQuality(stack)) quality = QualityEligibility.neutral();
        return quality;
    }

    public static Component aspectName(boolean efficacy) {
        return Component.translatable(efficacy ? "quality.immersive_smithing.aspect.efficacy" : "quality.immersive_smithing.aspect.durability");
    }

    private GrindstoneActions() {}
}
