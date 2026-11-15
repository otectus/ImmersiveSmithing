package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.api.ISmithingRecipeProvider;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.minigame.ForgePattern;
import com.otectus.immersivesmithing.quality.*;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.List;

/** Alternative metallurgy intentionally does not satisfy tests that require vanilla smithing ownership. */
@GameTestHolder("immersive_smithing_coexistence")
@PrefixGameTestTemplate(false)
public final class ExternalWorkflowGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nativeManufacturingRetainsEverySourceAcrossReload(GameTestHelper h) throws IOException {
        h.assertTrue(!ExternalWorkflow.namespaces().isEmpty(), "A real manufacturing bridge is loaded");
        var server = h.getLevel().getServer();
        var before = SmithingData.current().sourceRecipes().stream().filter(r -> !(r instanceof SmithingRecipe)).toList();
        h.assertTrue(before.stream().anyMatch(r -> ExternalWorkflow.owns(r.getId().getNamespace())), "Native manufacturing recipes are present");
        SmithingData.rebuild(server);
        SmithingData.rebuild(server);
        for (var source : before) {
            h.assertTrue(server.getRecipeManager().byKey(source.getId()).orElse(null) == source,
                    "Native recipe preserved exactly: " + source.getId());
        }
        h.assertTrue(SmithingData.current().suppressedRecipes().isEmpty(), "No native route suppressed by our workflow");
        for (var item : BuiltInRegistries.ITEM) {
            var stack = new ItemStack(item);
            if (!ExternalWorkflow.owns(stack)) continue;
            h.assertTrue(SmithingData.current().recipesProducing(item).isEmpty(), "No bypass for " + BuiltInRegistries.ITEM.getKey(item));
            h.assertTrue(MaterialResolver.resolve(stack).isEmpty(), "Native heat/metallurgy retained for " + BuiltInRegistries.ITEM.getKey(item));
        }
        for (String mod : CoverageGameTests.loadedMods()) {
            com.otectus.immersivesmithing.recipe.WeaponCatalog.write(java.nio.file.Path.of("logs"), mod, h.getLevel().registryAccess());
        }
        RegistryDumpGameTests.dumpRegistriesWhenAsked(h);
    }

    @GameTest(template = "empty")
    public static void importedQualityCannotStackWithTheOwnersQuality(GameTestHelper h) {
        for (String id : List.of("minecraft:iron_sword", "magistuarmory:steel_bastardsword", "magistuarmory:knight_chestplate")) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(new ResourceLocation(id)), "Real owner fixture exists: " + id);
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            ItemStack nativeStack = new ItemStack(item);
            var nativeAttributes = nativeStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
            ItemStack imported = nativeStack.copy();
            new QualityData(100, 100, false).apply(imported);
            imported.getOrCreateTagElement(QualityData.ROOT).putDouble(QualityData.CARRY, .375);
            imported.setDamageValue(7);
            var saved = imported.copy();
            h.assertTrue(imported.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)
                    .equals(nativeAttributes.get(Attributes.ATTACK_DAMAGE)), "No added damage modifier: " + id);
            h.assertTrue(QualityHooks.adjustDamage(imported, 7) == 7 && ProjectileEfficacy.efficacy(imported) == 1,
                    "Native durability and ranged damage: " + id);
            h.assertTrue(ItemStack.matches(imported, saved), "Read-only coexistence preserves imported metadata");
            h.assertTrue(MaterialResolver.recyclingValue(imported).isEmpty(), "No imported recycling shortcut");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeProviderCannotBypassTheNativeOwner(GameTestHelper h) {
        var id = new ResourceLocation("immersive_smithing", "test/external_owner");
        ISmithingRecipeProvider provider = (materials, access) -> List.of(new SmithingRecipe(id,
                new ResourceLocation("minecraft", "iron"), 1, List.of(), new ItemStack(Items.IRON_SWORD),
                ForgePattern.STANDARD, new ResourceLocation("immersive_smithing", "sword"), false, null));
        try {
            ImmersiveSmithingAPI.registerRecipeProvider(provider);
            SmithingData.rebuild(h.getLevel().getServer());
            h.assertTrue(SmithingData.current().recipe(id) == null
                    && h.getLevel().getRecipeManager().byKey(id).isEmpty(), "Provider cannot reintroduce an owned route");
        } finally {
            ImmersiveSmithingAPI.recipeProviders().remove(provider);
            SmithingData.rebuild(h.getLevel().getServer());
        }
        h.succeed();
    }
}
