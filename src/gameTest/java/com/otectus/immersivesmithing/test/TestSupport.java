package com.otectus.immersivesmithing.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.authlib.GameProfile;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

final class TestSupport {
    static final BlockPos STATION = new BlockPos(2, 1, 2);

    /**
     * Swaps the effective server config (the world file, or the shared file in shared mode) for an in-memory copy and
     * returns what to put back with {@link #restoreServerConfig}. Options changed in between are never saved, so Forge's
     * config file watcher, which reloads the file on another thread after every save, cannot undo one of them while a test
     * rebuilds with it.
     */
    static ServerConfigSource.Snapshot isolateServerConfig() {
        ServerConfigSource.Snapshot original = ServerConfigSource.snapshot();
        CommentedConfig active = java.util.Objects.requireNonNull(original.active(), "no server config is loaded");
        // A TOML round-trip deep-copies nested tables and comments without retaining file-backed subconfigs.
        ServerConfigSource.bindDetached(new TomlParser().parse(new TomlWriter().writeToString(active)));
        return original;
    }

    static void restoreServerConfig(ServerConfigSource.Snapshot original) {
        ServerConfigSource.restore(original);
    }

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "SmithTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(STATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    /** Right-clicks the station at the given height (0..1 within the block) with the item in the main hand. */
    static InteractionResult use(GameTestHelper h, FakePlayer player, double heightInBlock) {
        BlockPos pos = h.absolutePos(STATION);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + heightInBlock, pos.getZ() + 0.99);
        return h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }

    /** A fake player whose next swing is fully charged, as after waiting between swings. */
    static final class TestPlayer extends FakePlayer {
        TestPlayer(net.minecraft.server.level.ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "SmithFighter"));
        }

        @Override
        public boolean hasDisconnected() {
            return false;
        }

        void charge() {
            this.attackStrengthTicker = 10_000;
            this.setOnGround(true);
            this.fallDistance = 0F;
            this.setSprinting(false);
        }
    }

    static TestPlayer fighter(GameTestHelper h) {
        TestPlayer player = new TestPlayer(h.getLevel());
        BlockPos pos = h.absolutePos(STATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    /**
     * Puts the stacks in the player's hands the way a ticking player would see them: the held items' attribute
     * modifiers on the player (a fake player never ticks, so vanilla would not apply them).
     */
    static void hold(net.minecraft.world.entity.player.Player player, ItemStack main, ItemStack off) {
        var attributes = player.getAttributes();
        attributes.removeAttributeModifiers(player.getMainHandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        attributes.removeAttributeModifiers(player.getOffhandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        attributes.addTransientAttributeModifiers(main.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        attributes.addTransientAttributeModifiers(off.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
    }

    /** A training target with no armour and plenty of health, so every hit is measured in full. */
    static net.minecraft.world.entity.LivingEntity dummy(GameTestHelper h) {
        var pig = h.spawn(net.minecraft.world.entity.EntityType.PIG, STATION.above());
        pig.setNoAi(true);
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        return pig;
    }

    /** Health the target lost to one fully charged, non-critical hit. */
    static float damageOf(TestPlayer player, net.minecraft.world.entity.LivingEntity target, Runnable attack) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.setHealth(target.getMaxHealth());
        player.charge();
        float before = target.getHealth();
        attack.run();
        return before - target.getHealth();
    }

    static WorkpieceData workpiece(ItemStack target, int forge, int anvil, boolean faulty, WorkpieceState state) {
        return new WorkpieceData(target, new ResourceLocation("immersive_smithing", "test"), new ResourceLocation("minecraft", "iron"),
                target.getItem() == net.minecraft.world.item.Items.IRON_PICKAXE ? 27 : 18,
                forge, anvil, faulty, state, EquipmentClassifier.classify(target));
    }

    private TestSupport() {}
}
