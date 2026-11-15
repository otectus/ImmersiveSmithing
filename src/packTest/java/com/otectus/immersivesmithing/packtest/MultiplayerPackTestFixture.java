package com.otectus.immersivesmithing.packtest;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server half of the opt-in two-client check: remote tool animations, then an anvil pickup both clients must see
 * (the workpiece shown before, gone after, the leader's tongs loaded) on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = PackTestAgent.MOD_ID)
public final class MultiplayerPackTestFixture {
    private static boolean staged;
    private static boolean anvilStaged;
    private static boolean anvilPicked;
    static final BlockPos ANVIL = new BlockPos(2, 84, 1);
    private static boolean hammerStaged;
    static final BlockPos UPGRADE_FORGE = new BlockPos(1, 84, -1);
    private static int upgradeStage;
    private static int upgradeWait;
    private static ItemStack reservedBase;
    private static ResourceLocation upgradeRecipe;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("immersive_smithing.packtest.multiplayer") || event.phase != TickEvent.Phase.END) return;
        if (staged) {
            ServerPlayer leader = event.getServer().getPlayerList().getPlayerByName("VisualLeader");
            stageHammerWhenReady(leader);
            anvilPhase(event.getServer().overworld(), leader);
            upgradePhase(event.getServer().overworld(), leader);
            return;
        }
        if (event.getServer().getPlayerList().getPlayerCount() < 2) return;
        ServerLevel level = event.getServer().overworld();
        ServerPlayer leader = event.getServer().getPlayerList().getPlayerByName("VisualLeader");
        ServerPlayer peer = event.getServer().getPlayerList().getPlayerByName("VisualPeer");
        if (leader == null || peer == null) return;
        for (int x = -4; x <= 9; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 83, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (int y = 84; y <= 88; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.setBlockAndUpdate(new BlockPos(0, 86, -1), Blocks.LIGHT.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(4, 86, -1), Blocks.LIGHT.defaultBlockState());
        ItemStack loadedTongs = new ItemStack(ModItems.tongs(SmithingTier.NETHERITE));
        WorkpieceCodec.setHeld(loadedTongs, new WorkpieceData(new ItemStack(Items.IRON_SWORD), ImmersiveSmithing.id("packtest"),
                new ResourceLocation("minecraft", "iron"), 18, 90, 90, false, WorkpieceState.SHAPED,
                ImmersiveSmithing.id("sword")));
        leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, loadedTongs);
        leader.setGameMode(GameType.CREATIVE);
        peer.setGameMode(GameType.CREATIVE);
        // The disposable flat-server fixture can initially report zero client light. Vanilla night
        // vision makes the arm/tool silhouettes inspectable; ordinary station lighting is tested separately.
        leader.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2400, 0, false, false));
        peer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2400, 0, false, false));
        leader.teleportTo(level, .5, 84, .5, -90F, 0F);
        peer.teleportTo(level, 4.5, 84, 3.5, 126.87F, 5F);
        staged = true;
        try {
            Path shared = Path.of(System.getProperty("immersive_smithing.packtest.shared"));
            Files.createDirectories(shared);
            // Before server-ready, so a client that sees the marker always finds the recipe count.
            Files.writeString(shared.resolve("server-recipes"), "count=" + event.getServer().getRecipeManager().getRecipes().size()
                    + "\nsummary=" + SmithingData.current().report().summary() + "\n", StandardCharsets.UTF_8);
            Files.writeString(shared.resolve("server-ready"), "two players staged\n", StandardCharsets.UTF_8);
        } catch (Exception failure) {
            ImmersiveSmithing.LOGGER.error("PACKTEST multiplayer fixture could not write ready marker", failure);
        }
    }

    private static void stageHammerWhenReady(ServerPlayer leader) {
        if (hammerStaged || leader == null) return;
        try {
            Path shared = Path.of(System.getProperty("immersive_smithing.packtest.shared"));
            if (!Files.exists(shared.resolve("tongs-observed"))) return;
            leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.hammer(SmithingTier.NETHERITE)));
            hammerStaged = true;
            Files.writeString(shared.resolve("hammer-ready"), "remote hammer staged\n", StandardCharsets.UTF_8);
        } catch (Exception failure) {
            ImmersiveSmithing.LOGGER.error("PACKTEST multiplayer fixture could not stage hammer", failure);
        }
    }

    /** Places a workpiece on an anvil once both clients finished the swing checks, then picks it up with the leader's tongs. */
    private static void anvilPhase(ServerLevel level, ServerPlayer leader) {
        if (leader == null || anvilPicked) return;
        try {
            Path shared = Path.of(System.getProperty("immersive_smithing.packtest.shared"));
            if (!anvilStaged && Files.exists(shared.resolve("leader-swings-done")) && Files.exists(shared.resolve("peer-swings-done"))) {
                level.setBlockAndUpdate(ANVIL, com.otectus.immersivesmithing.registry.ModBlocks.SMITHS_ANVIL.get().defaultBlockState()
                        .setValue(com.otectus.immersivesmithing.block.SmithsAnvilBlock.FACING, net.minecraft.core.Direction.SOUTH));
                ((com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity) level.getBlockEntity(ANVIL)).setWorkpiece(
                        new WorkpieceData(new ItemStack(Items.IRON_SWORD), ImmersiveSmithing.id("packtest"),
                                new ResourceLocation("minecraft", "iron"), 18, 80, -1, false, WorkpieceState.FORGED,
                                ImmersiveSmithing.id("sword")));
                leader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
                anvilStaged = true;
                Files.writeString(shared.resolve("anvil-placed"), "workpiece on the anvil, leader holds empty tongs\n", StandardCharsets.UTF_8);
            }
            if (anvilStaged && Files.exists(shared.resolve("leader-anvil-seen")) && Files.exists(shared.resolve("peer-anvil-seen"))) {
                level.getBlockState(ANVIL).use(level, leader, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ANVIL),
                                net.minecraft.core.Direction.UP, ANVIL, false));
                anvilPicked = true;
                boolean empty = level.getBlockEntity(ANVIL) instanceof com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity anvil
                        && !anvil.hasWorkpiece();
                Files.writeString(shared.resolve("anvil-picked"), "server_empty=" + empty + " tongs_loaded="
                        + WorkpieceCodec.isHolding(leader.getMainHandItem()) + "\n", StandardCharsets.UTF_8);
            }
        } catch (Exception failure) {
            ImmersiveSmithing.LOGGER.error("PACKTEST multiplayer fixture could not run the anvil phase", failure);
        }
    }

    private MultiplayerPackTestFixture() {}

    /** Two real packet senders contend for a committed decorated base, then reload while its owner holds the job. */
    private static void upgradePhase(ServerLevel level, ServerPlayer leader) {
        if (leader == null || !net.minecraftforge.fml.ModList.get().isLoaded("magistuarmory") || upgradeStage == 5) return;
        try {
            Path shared = Path.of(System.getProperty("immersive_smithing.packtest.shared"));
            ServerPlayer peer = level.getServer().getPlayerList().getPlayerByName("VisualPeer");
            if (peer == null) return;
            if (upgradeStage == 0) {
                if (!Files.exists(shared.resolve("leader-anvil-done")) || !Files.exists(shared.resolve("peer-anvil-done"))) return;
                var recipe = SmithingData.current().recipesProducing(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        new ResourceLocation("magistuarmory", "knight_chestplate"))).stream().findFirst().orElseThrow();
                reservedBase = recipe.auxiliary().stream().filter(com.otectus.immersivesmithing.recipe.AuxiliaryIngredient::consumesEquipment)
                        .findFirst().orElseThrow().displayStack();
                reservedBase.setHoverName(net.minecraft.network.chat.Component.literal("The Reserved Oath"));
                reservedBase.setDamageValue(19);
                reservedBase.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
                new com.otectus.immersivesmithing.quality.QualityData(73, 86, false).apply(reservedBase);
                com.otectus.immersivesmithing.quality.MakersMark.stamp(reservedBase, leader);
                reservedBase.getOrCreateTagElement(com.otectus.immersivesmithing.quality.QualityData.ROOT).putDouble(
                        com.otectus.immersivesmithing.quality.QualityData.CARRY, .375);
                for (ServerPlayer player : java.util.List.of(leader, peer)) {
                    player.getInventory().clearContent();
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
                }
                leader.getInventory().setItem(9, reservedBase.copy());
                level.setBlockAndUpdate(UPGRADE_FORGE, com.otectus.immersivesmithing.registry.ModBlocks.SMITHS_FORGE.get().defaultBlockState());
                var forge = (com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity) level.getBlockEntity(UPGRADE_FORGE);
                forge.refundCast(recipe.metalUnits(), recipe.family());
                com.otectus.immersivesmithing.minigame.SessionManager.openForge(leader, forge, net.minecraft.world.InteractionHand.MAIN_HAND, SmithingTier.NETHERITE);
                var session = com.otectus.immersivesmithing.minigame.SessionManager.byPlayer(leader.getUUID()).orElseThrow();
                upgradeRecipe = recipe.getId();
                Files.writeString(shared.resolve("upgrade-offered"), session.id + "\n" + recipe.getId() + "\n");
                upgradeStage = 1;
            } else if (upgradeStage == 1) {
                // The leader chose the upgrade in the book (which pins it) and committed it at the tray.
                var forge = (com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity) level.getBlockEntity(UPGRADE_FORGE);
                var job = forge.job();
                if (job == null) return;
                require(job.owner.equals(leader.getUUID()), "The job belongs to the smith who committed it");
                require(job.escrow.size() == 1 && ItemStack.matches(reservedBase, job.escrow.get(0)), "Complete base escrow");
                require(leader.getInventory().countItem(reservedBase.getItem()) == 0, "Base removed exactly once");
                require(job.units == 45 && forge.reservedUnits() == 45 && forge.availableUnits() == 0, "The job reserves exactly its metal");
                Files.writeString(shared.resolve("upgrade-reserved"), "reserved=true\n");
                upgradeStage = 2;
            } else if (upgradeStage == 2) {
                if (!Files.exists(shared.resolve("peer-upgrade-attempt")) || ++upgradeWait < 10) return;
                var forge = (com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity) level.getBlockEntity(UPGRADE_FORGE);
                var job = forge.job();
                require(job != null && job.owner.equals(leader.getUUID()) && job.escrow.size() == 1
                        && peer.getInventory().countItem(reservedBase.getItem()) == 0,
                        "Foreign use, book, draw and cancel packets cannot steal the reservation");
                reload = level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
                upgradeStage = 3;
            } else if (upgradeStage == 3) {
                if (!reload.isDone()) return;
                reload.join();
                var forge = (com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity) level.getBlockEntity(UPGRADE_FORGE);
                var job = forge.job();
                require(job != null && job.owner.equals(leader.getUUID()), "Reload preserves the committed job");
                require(job.units == 45 && job.recipeId.equals(upgradeRecipe) && job.target.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        new ResourceLocation("magistuarmory", "knight_chestplate"))), "The job keeps its frozen recipe, cost and result");
                Files.writeString(shared.resolve("upgrade-reloaded"), "count=" + level.getServer().getRecipeManager().getRecipes().size() + "\n");
                upgradeStage = 4;
            } else if (upgradeStage == 4) {
                if (!Files.exists(shared.resolve("leader-upgrade-cancelled")) || !Files.exists(shared.resolve("peer-upgrade-synced"))) return;
                var forge = (com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity) level.getBlockEntity(UPGRADE_FORGE);
                if (forge.job() != null) return;
                require(leader.getInventory().countItem(reservedBase.getItem()) == 1 && peer.getInventory().countItem(reservedBase.getItem()) == 0
                        && forge.reservedUnits() == 0 && forge.moltenUnits() == 45, "Refund exactly once to owner with paid metal retained");
                boolean identical = false;
                for (int i = 0; i < leader.getInventory().getContainerSize(); i++) identical |= ItemStack.matches(reservedBase, leader.getInventory().getItem(i));
                require(identical, "Refund retains complete name, wear, enchantment and maker");
                Files.writeString(shared.resolve("upgrade-result"), "pass=true\nreservation=true\nreload=true\nrefund=true\n");
                upgradeStage = 5;
            }
        } catch (Exception failure) {
            upgradeStage = 5;
            ImmersiveSmithing.LOGGER.error("PACKTEST multiplayer upgrade failed", failure);
            try { Files.writeString(Path.of(System.getProperty("immersive_smithing.packtest.shared"), "upgrade-result"), "pass=false\n" + failure); }
            catch (Exception ignored) {}
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }
}
