package com.otectus.immersivesmithing.test;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.compat.epicknights.LoadedEquipment;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EpicNativeGameTests {
    @GameTest(template = "empty")
    public static void allPinnedArtilleryLaunchBoundariesTransform(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("artillery_addon")) { h.succeed(); return; }
        // Class initialization applies each strict injection, including guns not fired by this fixture.
        for (String name : List.of(
                "ArquebusRightclickProcedure",
                "BronzegunneRightclickedProcedure",
                "CarbineRightclickProcedure",
                "ChuKoNuShootProcedure",
                "DualWheellockCarbineRightclickProcedure",
                "DualWheellockPistolRightclickProcedure",
                "FireLanceRightclickProcedure",
                "HackbutMatchlockRightclickProcedure",
                "HandcannonRightclickProcedure",
                "HarquebusRightclick2Procedure",
                "MarkmenRightclickProcedure",
                "MatchlockPistolRightclickProcedure",
                "MatchlockRifleRightclickProcedure",
                "MinipistolaRightclickedProcedure",
                "NobleGonneRightclickedProcedure",
                "TestgunRightclickedProcedure",
                "TillergunRightclickedProcedure",
                "ToccolaRightclickedProcedure",
                "ToradarRightclickProcedure",
                "WheellockBreechloadingRilfeRightclickProcedure",
                "WheellockHandMortarRightclickProcedure",
                "WheellockHuntingRilfeRightclickProcedure",
                "WheellockMusketRightclickProcedure",
                "WheellockPistolRightclickProcedure",
                "WheellockWallgunRightclickProcedure")) {
            try { Class.forName("net.mcreator.artilleryaddon.procedures." + name).getDeclaredMethods(); }
            catch (Throwable failure) { throw new IllegalStateException(name + ": " + failure, failure); }
        }
        h.succeed();
    }

    private static ItemStack stack(String id) {
        var key = new ResourceLocation(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing fixture " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    static void personalize(ItemStack stack, FakePlayer player) {
        new QualityData(73, 86, false).apply(stack);
        MakersMark.stamp(stack, player);
        MakersMark.sign(stack, "Oathkeeper", List.of("Sworn to the forge"));
        stack.getOrCreateTagElement(QualityData.ROOT).putDouble(QualityData.CARRY, 0.375);
        stack.setDamageValue(17);
        stack.enchant(Enchantments.UNBREAKING, 2);
        stack.getOrCreateTagElement("display").putInt("color", 0x13579b);
        CompoundTag trim = stack.getOrCreateTagElement("Trim");
        trim.putString("material", "minecraft:gold"); trim.putString("pattern", "minecraft:sentry");
        stack.getOrCreateTag().putInt("RepairCost", 7);
    }

    /** Calls the addon's real packet handler, with a real Forge context whose sender is the test player. */
    static void swap(GameTestHelper h, FakePlayer player, int requestedPlayer, String target) throws Exception {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        connection.setListener(new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player));
        var constructor = NetworkEvent.Context.class.getDeclaredConstructor(Connection.class, NetworkDirection.class, int.class);
        constructor.setAccessible(true);
        var context = constructor.newInstance(connection, NetworkDirection.PLAY_TO_SERVER, 0);
        Class<?> packet = Class.forName("com.ice_the_noob.quick_swap.network.SwapPacket");
        Object message = packet.getConstructor(int.class, String.class).newInstance(requestedPlayer, target);
        packet.getMethod("handle", Supplier.class).invoke(message, (Supplier<NetworkEvent.Context>) () -> context);
        h.assertTrue(context.getPacketHandled(), "Actual packet marked handled");
    }

    @GameTest(template = "empty")
    public static void realVisorPacketPreservesStateAndValidatesSender(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("quick_swap") || !ModList.get().isLoaded("epicknights_open_helms")) { h.succeed(); return; }
        FakePlayer player = TestSupport.player(h);
        ItemStack closed = stack("magistuarmory:armet");
        personalize(closed, player);
        player.setItemSlot(EquipmentSlot.HEAD, closed.copy());
        for (int i = 0; i < 8; i++) {
            swap(h, player, player.getId(), "epicknights_open_helms:open_armet_helmet");
            h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(stack("epicknights_open_helms:open_armet_helmet").getItem()), "Native configured open helmet");
            swap(h, player, player.getId(), "magistuarmory:armet");
        }
        h.assertTrue(ItemStack.isSameItemSameTags(closed, player.getItemBySlot(EquipmentSlot.HEAD)), "Eight actual packet loops retain the complete saved state");
        swap(h, player, player.getId() + 1, "epicknights_open_helms:open_armet_helmet");
        swap(h, player, player.getId(), "minecraft:diamond_block");
        h.assertTrue(ItemStack.isSameItemSameTags(closed, player.getItemBySlot(EquipmentSlot.HEAD)), "Foreign entity IDs and arbitrary target items are rejected");
        // Configured pairs are read from the native map, including custom pairs with unequal maximum durability.
        var original = TestSupport.isolateServerConfig();
        try {
            com.otectus.immersivesmithing.config.ServerConfig.EPIC_KNIGHTS_HELMET_PAIRS.set(List.of("minecraft:iron_helmet-minecraft:diamond_helmet"));
            ItemStack iron = new ItemStack(Items.IRON_HELMET); personalize(iron, player);
            player.setItemSlot(EquipmentSlot.HEAD, iron);
            swap(h, player, player.getId(), "minecraft:diamond_helmet");
            ItemStack diamond = player.getItemBySlot(EquipmentSlot.HEAD);
            int expected = (17 * diamond.getMaxDamage() + iron.getMaxDamage() - 1) / iron.getMaxDamage();
            h.assertTrue(diamond.getDamageValue() == expected, "Different limits round proportional wear up");
            // Simulate the player's persisted equipment on reconnect, then use the reverse native mapping.
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.of(diamond.save(new CompoundTag())));
            swap(h, player, player.getId(), "minecraft:iron_helmet");
            h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() >= 17, "Reconnect/reverse conversion cannot repair damage");
            h.assertTrue(MakersMark.canSign(player.getItemBySlot(EquipmentSlot.HEAD), player, true), "Original signing rights survive");
        } finally { TestSupport.restoreServerConfig(original); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void paviseUsesItsNativePlacementSaveAndDrop(GameTestHelper h) {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        FakePlayer player = TestSupport.player(h);
        ItemStack pavise = stack("magistuarmory:steel_pavese"); personalize(pavise, player);
        pavise.getOrCreateTagElement("BlockEntityTag").putInt("Base", 4);
        CompoundTag pattern = new CompoundTag(); pattern.putString("Pattern", "bs"); pattern.putInt("Color", 1);
        ListTag patterns = new ListTag(); patterns.add(pattern);
        pavise.getOrCreateTagElement("BlockEntityTag").put("Patterns", patterns);
        ItemStack expected = pavise.copy();
        BlockPos pos = h.absolutePos(TestSupport.STATION);
        h.setBlock(TestSupport.STATION.below(), Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, pavise); player.setShiftKeyDown(true);
        var result = pavise.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
        h.assertTrue(result.consumesAction() && pavise.isEmpty(), "Native placement consumes one pavise");
        BlockEntity entity = h.getLevel().getBlockEntity(pos);
        h.assertTrue(entity != null && entity.getClass().getName().contains("Pavise"), "Native pavise block entity placed");
        CompoundTag saved = entity.saveWithFullMetadata();
        var state = h.getLevel().getBlockState(pos);
        h.getLevel().removeBlockEntity(pos);
        BlockEntity restored = BlockEntity.loadStatic(pos, state, saved);
        h.getLevel().setBlockEntity(restored);
        ItemStack picked = state.getBlock().getCloneItemStack(h.getLevel(), pos, state);
        h.assertTrue(ItemStack.isSameItemSameTags(expected, picked), "Native pickup after block-entity save/load preserves complete stack");
        state.getBlock().playerDestroy(h.getLevel(), player, pos, state, restored, ItemStack.EMPTY);
        ItemStack dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1)).stream()
                .map(ItemEntity::getItem).filter(s -> s.is(expected.getItem())).findFirst().orElseThrow();
        h.assertTrue(ItemStack.isSameItemSameTags(expected, dropped), "Native break/drop retains damage, banner, enchantment, name and maker");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thirtyYearsWarActualHitscanUsesFiringStack(GameTestHelper h) {
        if (!ModList.get().isLoaded("thirtyyearswar")) { h.succeed(); return; }
        float standard = shot(h, 50), low = shot(h, 0), high = shot(h, 100);
        h.assertTrue(standard > 0 && Math.abs(low / standard - 0.85) < 0.001 && Math.abs(high / standard - 1.15) < 0.001,
                "Actual hitscan damage scales once at its armor-piercing boundary: " + low + "/" + standard + "/" + high);
        h.succeed();
    }

    private static float shot(GameTestHelper h, int score) {
        FakePlayer player = TestSupport.player(h);
        ItemStack gun = stack("thirtyyearswar:baroque_wheellock_pistol");
        new QualityData(50, score, false).apply(gun);
        gun.getOrCreateTag().putBoolean("Charged", true);
        player.setItemInHand(InteractionHand.OFF_HAND, gun);
        ItemStack unrelated = stack("thirtyyearswar:baroque_matchlock_musket");
        new QualityData(50, 0, false).apply(unrelated);
        player.setItemInHand(InteractionHand.MAIN_HAND, unrelated);
        player.setYRot(0); player.setXRot(0);
        var target = TestSupport.dummy(h);
        target.setPos(player.getX(), player.getY() + 1, player.getZ() + 1.2);
        float before = target.getHealth();
        gun.getItem().use(h.getLevel(), player, InteractionHand.OFF_HAND);
        float damage = before - target.getHealth();
        h.assertTrue(!gun.getTag().getBoolean("Charged"), "Native shot consumes its loaded charge");
        target.discard();
        return damage;
    }

    @GameTest(template = "empty")
    public static void fletcheryActualLaunchKeepsAmmoAndDamageRules(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("fletchery")) { h.succeed(); return; }
        double baseline = bolt(h, 50), low = bolt(h, 0), high = bolt(h, 100);
        h.assertTrue(baseline > 0 && Math.abs(low / baseline - 0.85) < 0.001 && Math.abs(high / baseline - 1.15) < 0.001,
                "Native bolt procedure scales actual firing stack once: " + low + "/" + baseline + "/" + high);
        h.succeed();
    }

    private static double bolt(GameTestHelper h, int score) throws Exception {
        FakePlayer player = TestSupport.player(h);
        ItemStack crossbow = stack("fletchery:cranequin_crossbow");
        new QualityData(50, score, false).apply(crossbow);
        CompoundTag tag = crossbow.getOrCreateTag();
        tag.putDouble("loaded", 1); tag.putDouble("ammo", 1); tag.putDouble("damage", 2.75);
        tag.putDouble("bowmaxpull", 5.6); tag.putDouble("arrowspeed", 1.1); tag.putDouble("inaccuracy", 0.5);
        player.setItemInHand(InteractionHand.OFF_HAND, crossbow);
        h.assertTrue(LoadedEquipment.hasPayload(crossbow) && MaterialResolver.resolve(crossbow).isEmpty(), "Loaded crossbow cannot be melted");
        Class.forName("net.mcreator.epicfletchery.procedures.BoltShootProcedure").getMethod("execute",
                net.minecraft.world.level.LevelAccessor.class, double.class, double.class, double.class,
                net.minecraft.world.entity.Entity.class, ItemStack.class).invoke(null, h.getLevel(), player.getX(), player.getY(), player.getZ(), player, crossbow);
        AbstractArrow arrow = h.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(2)).stream()
                .filter(a -> a.getOwner() == player).findFirst().orElseThrow();
        double damage = arrow.getBaseDamage();
        h.assertTrue(tag.getDouble("loaded") == 0 && arrow.pickup == AbstractArrow.Pickup.ALLOWED, "Native unload and ammunition recovery stay intact");
        var saved = arrow.saveWithoutId(new CompoundTag()); arrow.load(saved);
        com.otectus.immersivesmithing.quality.ProjectileEfficacy.onJoin(new net.minecraftforge.event.entity.EntityJoinLevelEvent(arrow, h.getLevel()));
        h.assertTrue(Math.abs(damage - arrow.getBaseDamage()) < 0.00001, "Saved projectile is never scaled again");
        arrow.discard(); return damage;
    }
    @GameTest(template = "empty")
    public static void artilleryNativeThrownRecoveryAndBothUsePaths(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("artillery_addon")) { h.succeed(); return; }
        for (String id : List.of("javelin", "throwing_cross")) for (boolean finish : List.of(false, true)) {
            FakePlayer player = TestSupport.player(h);
            ItemStack weapon = stack("artillery_addon:" + id); personalize(weapon, player);
            new QualityData(50, 100, false).apply(weapon); weapon.setCount(2);
            ItemStack expected = weapon.copyWithCount(1);
            player.setItemInHand(InteractionHand.OFF_HAND, weapon);
            ItemStack unrelated = new ItemStack(Items.DIAMOND_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, unrelated);
            if (finish) weapon.getItem().finishUsingItem(weapon, h.getLevel(), player);
            else weapon.getItem().releaseUsing(weapon, h.getLevel(), player, 0);
            AbstractArrow arrow = h.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(3)).stream()
                    .filter(a -> a.getOwner() == player).findFirst().orElseThrow();
            h.assertTrue(weapon.getCount() == 1 && unrelated.getCount() == 1, "Exactly the actual offhand thrown piece is consumed");
            double nativeDamage = finish ? (id.equals("javelin") ? 2 : 4.5) : (id.equals("javelin") ? 3 : 3.8);
            h.assertTrue(Math.abs(arrow.getBaseDamage() - nativeDamage * 1.15) < .00001,
                    id + " (finish=" + finish + ") native throw scales once: " + arrow.getBaseDamage());
            var saved = arrow.saveWithoutId(new CompoundTag());
            var type = arrow.getType(); arrow.discard();
            AbstractArrow restored = (AbstractArrow)type.create(h.getLevel()); restored.load(saved);
            var pickup = restored.getClass().getDeclaredMethod("getPickupItem"); pickup.setAccessible(true);
            ItemStack returned = (ItemStack)pickup.invoke(restored);
            h.assertTrue(ItemStack.matches(expected, returned), "Full thrown stack survives entity save/load and native pickup: " + id);
            com.otectus.immersivesmithing.quality.ProjectileEfficacy.onJoin(new net.minecraftforge.event.entity.EntityJoinLevelEvent(restored, h.getLevel()));
            h.assertTrue(Math.abs(restored.getBaseDamage() - nativeDamage * 1.15) < .00001, "Reload does not scale a second time");
            restored.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void artilleryNativeGunConsumesItsChargeAndUsesOneMultiplier(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("artillery_addon")) { h.succeed(); return; }
        for (int score : List.of(0, 50, 100)) {
            boolean fired = false;
            // Misfires are a real native mechanic. Retry fresh guns instead of disabling its random damage/config.
            for (int attempt = 0; attempt < 20 && !fired; attempt++) {
                FakePlayer player = TestSupport.player(h);
                ItemStack gun = stack("artillery_addon:handgonne"); new QualityData(50, score, false).apply(gun);
                gun.getOrCreateTag().putDouble("stage", 2); gun.getOrCreateTag().putDouble("ammo", 0);
                gun.getOrCreateTag().putBoolean("loaded", true);
                player.setItemInHand(InteractionHand.MAIN_HAND, gun);
                player.setItemInHand(InteractionHand.OFF_HAND, stack("artillery_addon:match"));
                gun.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
                var projectiles = h.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(3)).stream()
                        .filter(a -> a.getOwner() == player).toList();
                if (projectiles.isEmpty()) continue;
                h.assertTrue(projectiles.size() == 1 && !gun.getTag().getBoolean("loaded"), "Native single ball and charge consumption retained");
                h.assertTrue(!LoadedEquipment.hasPayload(gun), "Spent native stage does not permanently lock the gun");
                h.assertTrue(Math.abs(projectiles.get(0).getBaseDamage() - 4 * (score == 0 ? .85 : score == 50 ? 1 : 1.15)) < .00001,
                        "Actual gun launch scales its base damage once");
                projectiles.forEach(net.minecraft.world.entity.Entity::discard); fired = true;
            }
            h.assertTrue(fired, "Native gun successfully fired after retries");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryDelayedShotKeepsTheWeaponBeforeAHandChange(GameTestHelper h) {
        if (!ModList.get().isLoaded("artillery_addon")) { h.succeed(); return; }
        FakePlayer player = TestSupport.player(h);
        ItemStack gun = stack("artillery_addon:wheellock_musket"); new QualityData(50, 100, false).apply(gun);
        gun.getOrCreateTag().putDouble("stage", 3); gun.getOrCreateTag().putDouble("powder", 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun); player.setXRot(90);
        java.util.List<AbstractArrow> launched = new java.util.ArrayList<>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() == player) launched.add(arrow);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, listener);
        gun.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack replacement = stack("artillery_addon:handgonne"); new QualityData(50, 0, false).apply(replacement);
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        h.assertTrue(gun.getTag().getDouble("stage") == 4 && !LoadedEquipment.hasPayload(gun), "Native firing consumes the load before its delay");
        ItemStack repeater = stack("artillery_addon:chu_ko_nu"); repeater.getOrCreateTag().putDouble("ammo", 1);
        ItemStack dual = stack("artillery_addon:dual_wheellock_pistol"); dual.getOrCreateTag().putDouble("barrel_two", 1);
        h.assertTrue(LoadedEquipment.hasPayload(repeater) && LoadedEquipment.hasPayload(dual), "Repeater count and partially loaded second barrel are protected");
        h.runAfterDelay(8, () -> {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            h.assertTrue(launched.size() == 1 && Math.abs(launched.get(0).getBaseDamage() - 5.7 * 1.15) < .00001,
                    "Real delayed native shot keeps original high quality after low-quality hand swap: "
                            + launched.stream().map(a -> a.getBaseDamage() + " " + a.getPersistentData()).toList());
            launched.forEach(net.minecraft.world.entity.Entity::discard); h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void boomsticksNativeProjectilesKeepDamageAndThrownStack(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("medieval_boomsticks")) { h.succeed(); return; }
        for (String type : List.of("arbalest", "handgonne", "javelin", "iron_throwing_axe", "war_dart")) {
            float normal = boomHit(h, type, 50), low = boomHit(h, type, 0), high = boomHit(h, type, 100);
            h.assertTrue(normal > 0 && Math.abs(low / normal - 0.85) < 0.002 && Math.abs(high / normal - 1.15) < 0.002,
                    type + " native hit uses one launch multiplier: " + low + "/" + normal + "/" + high);
        }
        h.succeed();
    }

    private static float boomHit(GameTestHelper h, String type, int score) throws Exception {
        FakePlayer player = TestSupport.player(h);
        ItemStack weapon = stack("medieval_boomsticks:" + type);
        personalize(weapon, player); new QualityData(50, score, false).apply(weapon);
        // No enchantment damage in the arithmetic fixture; the full saved weapon still carries Unbreaking and personal data.
        AbstractArrow projectile;
        if (type.equals("arbalest") || type.equals("handgonne")) {
            ListTag ammunition = new ListTag();
            ammunition.add(stack("medieval_boomsticks:" + (type.equals("arbalest") ? "heavy_bolt" : "round_ball")).save(new CompoundTag()));
            weapon.getOrCreateTag().put("ChargedProjectiles", ammunition); weapon.getOrCreateTag().putBoolean("Charged", true);
            player.setItemInHand(InteractionHand.OFF_HAND, weapon);
            weapon.getItem().use(h.getLevel(), player, InteractionHand.OFF_HAND);
            projectile = h.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(4)).stream()
                    .filter(a -> a.getOwner() == player).findFirst().orElseThrow();
            h.assertTrue(!weapon.getTag().getBoolean("Charged"), "Native " + type + " consumes its charge");
        } else {
            String entity = switch (type) { case "javelin" -> "ThrownJavelin"; case "iron_throwing_axe" -> "ThrowableAxe"; default -> "ThrowableWardart"; };
            projectile = (AbstractArrow) Class.forName("com.TBK.medieval_boomsticks.server.entity." + entity)
                    .getConstructor(net.minecraft.world.level.Level.class, net.minecraft.world.entity.LivingEntity.class, ItemStack.class)
                    .newInstance(h.getLevel(), player, weapon);
            h.getLevel().addFreshEntity(projectile);
            var carrier = (com.otectus.immersivesmithing.compat.epicknights.NativeProjectile.ThrownStack) projectile;
            h.assertTrue(ItemStack.isSameItemSameTags(weapon, carrier.immersive_smithing$thrownStack()), "Native throw retains the exact weapon");
            CompoundTag saved = projectile.saveWithoutId(new CompoundTag()); projectile.load(saved);
            h.assertTrue(ItemStack.isSameItemSameTags(weapon, carrier.immersive_smithing$thrownStack()), "Native thrown stack survives save/load with damage, quality, carry, maker and color");
        }
        // Switch hands before the actual hit: this must not affect a launched projectile.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        projectile.setDeltaMovement(0, 0, 1);
        var target = TestSupport.dummy(h); float before = target.getHealth();
        var hit = projectile.getClass().getDeclaredMethod("onHitEntity", net.minecraft.world.phys.EntityHitResult.class);
        hit.setAccessible(true); hit.invoke(projectile, new net.minecraft.world.phys.EntityHitResult(target));
        float damage = before - target.getHealth(); target.discard(); projectile.discard(); return damage;
    }
    @GameTest(template = "empty")
    public static void nativeHeraldryAndDecorationRemovalRetainPersonalIdentity(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        FakePlayer player = TestSupport.player(h);
        var menu = new net.minecraft.world.inventory.CraftingMenu(1, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TestSupport.STATION)));
        ItemStack armor = stack("magistuarmory:knight_chestplate"); personalize(armor, player);
        menu.getSlot(1).set(armor.copy()); menu.getSlot(2).set(new ItemStack(Items.RED_BANNER));
        ItemStack heraldic = menu.getSlot(0).getItem().copy();
        h.assertTrue(heraldic.is(armor.getItem()) && heraldic.getTagElement("BlockEntityTag") != null, "Actual native heraldry recipe adds a surcoat");
        h.assertTrue(heraldic.getHoverName().equals(armor.getHoverName()), "Native heraldry preserves the exact personal name");
        h.assertTrue(heraldic.getTagElement(QualityData.ROOT).equals(armor.getTagElement(QualityData.ROOT)), "Native heraldry preserves full quality, carry and signing rights");
        menu.getSlot(1).set(ItemStack.EMPTY); menu.getSlot(2).set(ItemStack.EMPTY);
        ItemStack helm = stack("magistuarmory:armet"); personalize(helm, player);
        menu.getSlot(1).set(helm.copy()); menu.getSlot(2).set(stack("magistuarmory:plume_middle_decoration"));
        ItemStack decorated = menu.getSlot(0).getItem().copy();
        h.assertTrue(decorated.is(helm.getItem()) && decorated.getTagElement("ArmorDecoration") != null, "Actual native attachment recipe keeps the helmet");
        menu.getSlot(1).set(ItemStack.EMPTY); menu.getSlot(2).set(ItemStack.EMPTY); menu.getSlot(1).set(decorated);
        ItemStack bag = menu.getSlot(0).getItem().copy();
        h.assertTrue(bag.is(stack("magistuarmory:medieval_bag").getItem()) && !QualityData.has(bag), "Native removal produces its neutral bag");
        @SuppressWarnings("unchecked")
        var parts = (List<ItemStack>)Class.forName("com.magistuarmory.item.MedievalBagItem").getMethod("getContents", ItemStack.class).invoke(null, bag);
        ItemStack returned = parts.stream().filter(s -> s.is(helm.getItem())).findFirst().orElseThrow();
        h.assertTrue(returned.getHoverName().equals(helm.getHoverName())
                && returned.getTagElement(QualityData.ROOT).equals(helm.getTagElement(QualityData.ROOT)), "Native removal preserves name, quality, carry and maker on the gear");
        h.assertTrue(parts.stream().filter(s -> !s.is(helm.getItem())).noneMatch(QualityData::has), "Removed decoration never acquires the armor's identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void horseArmorUsesNativeProtectionAndSavedEquipment(GameTestHelper h) {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        ItemStack armor = stack("magistuarmory:chainmail_horse_armor");
        var horse = net.minecraft.world.entity.EntityType.HORSE.create(h.getLevel());
        h.assertTrue(horse != null && horse.isArmor(armor) && horse.getSlot(401).set(armor.copy()), "Native horse equipment accepts the armor");
        double protection = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        personalize(armor, TestSupport.player(h));
        new QualityData(100, 100, false).apply(armor);
        horse.getSlot(401).set(armor.copy());
        h.assertTrue(horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == protection, "No fictional efficacy bonus on the horse");
        CompoundTag saved = horse.saveWithoutId(new CompoundTag());
        var restored = net.minecraft.world.entity.EntityType.HORSE.create(h.getLevel()); restored.load(saved);
        h.assertTrue(ItemStack.isSameItemSameTags(restored.getArmor(), armor), "Native horse save/load keeps full equipped stack");
        h.assertTrue(!com.otectus.immersivesmithing.quality.QualityEligibility.canCarryQuality(armor), "Non-damageable horse armor has no unsupported quality claim");
        horse.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void releasedSevenKingdomsLedgerSurvivesReloadAndCannotBeClaimedTwice(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("ekotsk")) { h.succeed(); return; }
        Class<?> storage = Class.forName("net.ekotsk.loot.storage.UniqueLootSavedData");
        Object ledger = storage.getConstructor().newInstance();
        var id = new ResourceLocation("ekotsk", "ice");
        var claim = storage.getMethod("tryClaim", ResourceLocation.class);
        h.assertTrue((boolean)claim.invoke(ledger, id) && !(boolean)claim.invoke(ledger, id), "Released native ledger permits one claim");
        CompoundTag saved = ((net.minecraft.world.level.saveddata.SavedData)ledger).save(new CompoundTag());
        Object restored = storage.getMethod("load", CompoundTag.class).invoke(null, saved);
        com.otectus.immersivesmithing.recipe.SmithingData.rebuild(h.getLevel().getServer());
        h.assertTrue(!(boolean)claim.invoke(restored, id), "Native persisted claim remains spent after recipe reload");
        ItemStack relic = stack("ekotsk:ice"); personalize(relic, TestSupport.player(h));
        h.assertTrue(com.otectus.immersivesmithing.recipe.SmithingData.current().recipesProducing(relic.getItem()).isEmpty()
                && MaterialResolver.resolve(relic).isEmpty(), "No manufacture or salvage bypass for the actual unique item");
        h.succeed();
    }

    private EpicNativeGameTests() {}
}
