package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.quality.QualityCalculator;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.SmithingData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Actual native hurt callbacks, not a reimplementation of elemental effects. No optional types in signatures. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EpicElementalGameTests {
    @GameTest(template = "empty")
    public static void elementalWeaponsRetainNativeEffectsAndScalePhysicalDamageOnce(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("epic_knights_ice_and_fire")) { h.succeed(); return; }
        var player = TestSupport.fighter(h);
        List<LightningBolt> bolts = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() == h.getLevel() && event.getEntity() instanceof LightningBolt bolt) bolts.add(bolt);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, listener);
        try {
            for (String element : List.of("fire", "ice", "lightning")) {
                var item = BuiltInRegistries.ITEM.get(new ResourceLocation("epic_knights_ice_and_fire", "dragonsteel_" + element + "_shortsword"));
                h.assertTrue(!SmithingData.current().recipesProducing(item).isEmpty(), "Metal variant can be forged: " + element);
                float baseline = 0;
                for (int score : new int[]{-1, 0, 50, 100}) {
                    var target = TestSupport.dummy(h);
                    ItemStack weapon = new ItemStack(item);
                    if (score >= 0) new QualityData(score, score, false).apply(weapon);
                    TestSupport.hold(player, weapon, ItemStack.EMPTY);
                    bolts.clear();
                    float damage = TestSupport.damageOf(player, target, () -> player.attack(target));
                    if (score == -1) baseline = damage;
                    double multiplier = score < 0 ? 1 : QualityCalculator.efficacyMultiplier(new QualityData(score, score, false));
                    h.assertTrue(baseline > 0 && Math.abs(damage - baseline * multiplier) < .05,
                            element + " physical damage at " + score + ": " + damage + " vs " + baseline * multiplier);
                    if (element.equals("fire")) h.assertTrue(target.getRemainingFireTicks() == 300, "Native fire lasts 15 seconds at every quality");
                    if (element.equals("ice")) {
                        h.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                && target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == 300, "Native ice slow duration is unchanged");
                        Class<?> provider = Class.forName("com.github.alexthe666.iceandfire.entity.props.EntityDataProvider");
                        LazyOptional<?> capability = (LazyOptional<?>) provider.getMethod("getCapability", Entity.class).invoke(null, target);
                        Object data = capability.orElseThrow(() -> new IllegalStateException("Missing native frozen capability"));
                        Object frozen = data.getClass().getField("frozenData").get(data);
                        h.assertTrue(frozen.getClass().getField("isFrozen").getBoolean(frozen)
                                && frozen.getClass().getField("frozenTicks").getInt(frozen) == 300, "Native freeze capability is applied once");
                    }
                    h.assertTrue(bolts.size() == (element.equals("lightning") ? 1 : 0), "Exactly one native lightning bolt per lightning hit");
                    bolts.forEach(Entity::discard);
                    target.discard();
                }
            }
            var venom = BuiltInRegistries.ITEM.get(new ResourceLocation("epic_knights_ice_and_fire", "myrmex_jungle_venom_shortsword"));
            h.assertTrue(SmithingData.current().recipesProducing(venom).isEmpty(), "Venom/chitin acquisition stays native");
            var target = TestSupport.dummy(h);
            TestSupport.hold(player, new ItemStack(venom), ItemStack.EMPTY);
            TestSupport.damageOf(player, target, () -> player.attack(target));
            h.assertTrue(target.hasEffect(MobEffects.POISON) && target.getEffect(MobEffects.POISON).getDuration() == 200
                    && target.getEffect(MobEffects.POISON).getAmplifier() == 2, "Native venom potency and duration are unchanged");
            target.discard();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            bolts.forEach(Entity::discard);
            TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        }
        h.succeed();
    }
    private EpicElementalGameTests() {}
}
