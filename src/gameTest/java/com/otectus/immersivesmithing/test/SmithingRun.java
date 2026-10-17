package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.minigame.AnvilMinigame;
import com.otectus.immersivesmithing.minigame.AnvilPattern;
import com.otectus.immersivesmithing.minigame.AnvilRun;
import com.otectus.immersivesmithing.minigame.ForgeMinigame;
import com.otectus.immersivesmithing.minigame.ForgePattern;
import com.otectus.immersivesmithing.minigame.ForgeRun;
import com.otectus.immersivesmithing.minigame.ForgeSession;
import com.otectus.immersivesmithing.minigame.PatternRegistry;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.minigame.SmithingSession;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.List;

/**
 * A complete, server-validated smithing run for one recipe: molten metal in a Smith's Forge, a forge session with
 * perfect presses, an anvil session with perfect strikes, and a real quench at a Smith's Trough. Components are put
 * in the smith's inventory first (a reworked piece can be given explicitly). Returns the finished item as it landed in
 * the inventory, so tests see exactly what a player would.
 */
final class SmithingRun {
    static final BlockPos FORGE = new BlockPos(1, 1, 1);
    static final BlockPos ANVIL = new BlockPos(3, 1, 1);
    static final BlockPos TROUGH = new BlockPos(1, 1, 3);

    static ItemStack smith(GameTestHelper h, FakePlayer player, SmithingRecipe recipe, ItemStack reworked) {
        h.setBlock(FORGE, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(FORGE));
        WorkpieceData stock = new WorkpieceData(recipe.result().copy(), recipe.getId(), recipe.family(), recipe.metalUnits(),
                0, -1, false, WorkpieceState.FORGED, recipe.anvilPattern());
        if (forge.acceptWorkpiece(stock) != SmithsForgeBlockEntity.Result.OK) throw new IllegalStateException("forge refused the metal");
        player.getInventory().clearContent();
        int slot = 9;
        for (AuxiliaryIngredient aux : recipe.auxiliary()) {
            ItemStack stack = aux.consumesEquipment() && !reworked.isEmpty() ? reworked.copy() : aux.displayStack();
            player.getInventory().setItem(slot++, stack);
        }
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.NETHERITE));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);

        SessionManager.openForge(player, forge, InteractionHand.MAIN_HAND, SmithingTier.NETHERITE);
        SmithingSession opened = SessionManager.byPlayer(player.getUUID()).orElseThrow(() -> new IllegalStateException("forge offered nothing"));
        SessionManager.selectRecipe(player, opened.id, recipe.getId());
        ForgeSession session = (ForgeSession) SessionManager.byPlayer(player.getUUID())
                .orElseThrow(() -> new IllegalStateException("selection refused for " + recipe.getId()));
        ForgePattern pattern = PatternRegistry.forge(recipe.forgePattern());
        List<ForgeMinigame.Phase> phases = ForgeMinigame.phases(pattern, session.seed, recipe.metalUnits());
        ForgeRun shadow = new ForgeRun(phases, pattern.transitionMs());
        for (int phase = 0; phase < phases.size(); phase++) {
            int t = shadow.phaseStartMs() + perfectMoment(phases.get(phase));
            shadow.press(t);
            SessionManager.forgeAction(player, session.id, phase, t, session.startMs() + t);
        }
        WorkpieceData forged = WorkpieceCodec.getHeld(player.getMainHandItem()).orElseThrow(() -> new IllegalStateException("no workpiece"));

        h.setBlock(ANVIL, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ANVIL));
        anvil.setWorkpiece(forged);
        WorkpieceCodec.clearHeld(player.getMainHandItem());
        ItemStack heldTongs = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.hammer(SmithingTier.NETHERITE)));
        SessionManager.openAnvil(player, anvil, InteractionHand.MAIN_HAND, SmithingTier.NETHERITE);
        SmithingSession strikes = SessionManager.byPlayer(player.getUUID()).orElseThrow(() -> new IllegalStateException("anvil refused"));
        AnvilPattern anvilPattern = PatternRegistry.anvil(anvil.workpiece().anvilPattern());
        AnvilRun run = new AnvilRun(anvilPattern, strikes.seed);
        int seq = 0;
        while (!run.isComplete()) {
            AnvilMinigame.Target target = run.currentTarget();
            int t = run.spawnMs() + AnvilMinigame.idealMs(anvilPattern);
            run.strike(t, target.x(), target.y());
            SessionManager.anvilStrike(player, strikes.id, ++seq, target.x(), target.y(), t, strikes.startMs() + t);
        }
        WorkpieceData shaped = anvil.takeWorkpiece();
        if (shaped == null || !shaped.isShaped()) throw new IllegalStateException("not shaped");

        WorkpieceCodec.setHeld(heldTongs, shaped);
        player.setItemInHand(InteractionHand.MAIN_HAND, heldTongs);
        h.setBlock(TROUGH, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(TROUGH));
        trough.addBucket();
        BlockPos pos = h.absolutePos(TROUGH);
        h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.99), Direction.SOUTH, pos, false));
        if (WorkpieceCodec.isHolding(player.getMainHandItem())) throw new IllegalStateException("quench failed");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(recipe.resultItem())) return stack;
        }
        throw new IllegalStateException("finished " + recipe.resultItem() + " not in the inventory");
    }

    static ResourceLocation patternOf(SmithingRecipe recipe) {
        return recipe.anvilPattern();
    }

    static int perfectMoment(ForgeMinigame.Phase phase) {
        for (int ms = 0; ms < 20_000; ms++) {
            if (ForgeMinigame.accuracy(phase, ForgeMinigame.position(phase, ms / 1000F)) >= 1F) return ms;
        }
        throw new IllegalStateException("No perfect moment");
    }

    private SmithingRun() {}
}
