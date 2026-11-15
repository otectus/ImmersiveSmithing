package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.quality.QualityData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;

/** Exercises Better Combat's registered server attack receiver, including Rework's loaded presets. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BetterCombatGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void actualCombosAndOffhandAttacksScaleOnceWithoutChangingPresets(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("bettercombat") || !ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        for (String id : List.of("iron_shortsword", "steel_zweihander", "iron_chivalrylance")) {
            for (int combo = 0; combo < 3; combo++) {
                double normal = hit(h, id, combo, 50, false);
                double low = hit(h, id, combo, 0, false), high = hit(h, id, combo, 100, false);
                h.assertTrue(normal > 0 && Math.abs(low / normal - .85) < .002 && Math.abs(high / normal - 1.15) < .002,
                        "Actual combo " + id + "/" + combo + " scales once: " + low + "/" + normal + "/" + high);
            }
        }
        double normal = hit(h, "iron_shortsword", 1, 50, true);
        double low = hit(h, "iron_shortsword", 1, 0, true), high = hit(h, "iron_shortsword", 1, 100, true);
        h.assertTrue(normal > 0 && Math.abs(low / normal - .85) < .002 && Math.abs(high / normal - 1.15) < .002,
                "Actual offhand combo uses the offhand quality once: " + low + "/" + normal + "/" + high);
        h.succeed();
    }

    private static double hit(GameTestHelper h, String id, int combo, int score, boolean offhand) throws Exception {
        var player = TestSupport.fighter(h);
        ItemStack weapon = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("magistuarmory", id)));
        Class<?> registry = Class.forName("net.bettercombat.logic.WeaponRegistry");
        var attributes = registry.getMethod("getAttributes", ItemStack.class);
        Object preset = attributes.invoke(null, weapon);
        new QualityData(50, score, false).apply(weapon);
        h.assertTrue(java.util.Objects.equals(preset, attributes.invoke(null, weapon)), "Grading keeps the actual loaded preset: " + id);
        ItemStack other = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("magistuarmory", "iron_shortsword")));
        new QualityData(50, 0, false).apply(other);
        TestSupport.hold(player, offhand ? other : weapon, offhand ? weapon : ItemStack.EMPTY);
        if (preset == null) {
            h.assertTrue(!offhand, "Native unanimated fallback is a main-hand attack");
            var target = TestSupport.dummy(h);
            double damage = TestSupport.damageOf(player, target, () -> player.attack(target));
            target.discard(); TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
            return damage;
        }
        Class<?> helper = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
        Object hand = helper.getMethod("getCurrentAttack", Player.class, int.class).invoke(null, player, combo);
        h.assertTrue(hand != null && (boolean)hand.getClass().getMethod("isOffHand").invoke(hand) == offhand, "Native selection uses expected hand");
        double beforeAttribute = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        var target = TestSupport.dummy(h); target.setPos(player.position().add(0, 0, 1));
        player.charge();
        float before = target.getHealth();
        Class<?> packetClass = Class.forName("net.bettercombat.network.Packets$C2S_AttackRequest");
        Object packet = packetClass.getConstructor(int.class, boolean.class, int.class, int[].class)
                .newInstance(combo, false, player.getInventory().selected, new int[]{target.getId()});
        FriendlyByteBuf payload = (FriendlyByteBuf)packetClass.getMethod("write").invoke(packet);
        var transport = packetClass.getField("UseVanillaPacket"); boolean original = transport.getBoolean(null);
        try {
            // Use BC's direct attack transport; the fake connection cannot receive vanilla entity-interact packets.
            transport.setBoolean(null, false);
            Class<?> networking = Class.forName("net.bettercombat.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            Map<?, ?> handlers = (Map<?, ?>)networking.getField("HANDLERS").get(null);
            Object receiver = handlers.get(packetClass.getField("ID").get(null));
            Class<?> handler = Class.forName("net.bettercombat.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayChannelHandler");
            handler.getMethods()[0].invoke(receiver, h.getLevel().getServer(), player, player.connection, payload, null);
        } finally { transport.setBoolean(null, original); payload.release(); }
        double damage = before - target.getHealth(); target.discard();
        h.assertTrue(Math.abs(beforeAttribute - player.getAttributeValue(Attributes.ATTACK_DAMAGE)) < .00001,
                "Native combo and offhand cleanup restore attributes without stacking quality");
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        return damage;
    }
}
