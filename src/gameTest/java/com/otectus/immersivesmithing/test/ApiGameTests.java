package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ISmithingAssistSource;
import com.otectus.immersivesmithing.api.ISmithingCostModifier;
import com.otectus.immersivesmithing.api.ISmithingRecipeGate;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.api.SmithingStation;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** The 1.1.0 progression hooks: recipe gates, cost modifiers and per-player assistance. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApiGameTests {

    private static SmithingRecipe recipe(int units) {
        return new SmithingRecipe(ImmersiveSmithing.id("test/api_sword"), ImmersiveSmithing.id("iron"), units, List.of(),
                new ItemStack(Items.IRON_SWORD), ImmersiveSmithing.id("blade"), null, false, null);
    }

    @GameTest(template = "empty")
    public static void recipeGatesFirstDenialWinsAndThrowingGatesAreSkipped(GameTestHelper h) {
        FakePlayer smith = TestSupport.player(h);
        SmithingRecipe recipe = recipe(18);
        ISmithingRecipeGate broken = (p, r) -> { throw new IllegalStateException("broken gate"); };
        ISmithingRecipeGate allow = (p, r) -> Optional.empty();
        ISmithingRecipeGate first = (p, r) -> Optional.of(Component.literal("first"));
        ISmithingRecipeGate second = (p, r) -> Optional.of(Component.literal("second"));
        try {
            h.assertTrue(ImmersiveSmithingAPI.recipeDenial(smith, recipe).isEmpty(), "No gates: allowed");
            ImmersiveSmithingAPI.registerRecipeGate(broken);
            ImmersiveSmithingAPI.registerRecipeGate(allow);
            h.assertTrue(ImmersiveSmithingAPI.recipeDenial(smith, recipe).isEmpty(), "A throwing gate is skipped, an allowing one allows");
            ImmersiveSmithingAPI.registerRecipeGate(first);
            ImmersiveSmithingAPI.registerRecipeGate(second);
            Optional<Component> reason = ImmersiveSmithingAPI.recipeDenial(smith, recipe);
            h.assertTrue(reason.isPresent() && "first".equals(reason.get().getString()), "The first registered denial is the reason");
        } finally {
            for (ISmithingRecipeGate g : List.of(broken, allow, first, second)) ImmersiveSmithingAPI.unregisterRecipeGate(g);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void costModifiersAreFlooredAndCapped(GameTestHelper h) {
        FakePlayer smith = TestSupport.player(h);
        SmithingRecipe recipe = recipe(18);
        ItemStack piece = new ItemStack(Items.IRON_SWORD);
        ISmithingCostModifier discount = new ISmithingCostModifier() {
            @Override public int metalUnits(net.minecraft.server.level.ServerPlayer s, SmithingRecipe r, int units) { return units - 5; }
            @Override public int refinementLevelCost(net.minecraft.server.level.ServerPlayer s, ItemStack p, int levels) { return levels - 1; }
        };
        ISmithingCostModifier ruinous = new ISmithingCostModifier() {
            @Override public int metalUnits(net.minecraft.server.level.ServerPlayer s, SmithingRecipe r, int units) { return -40; }
            @Override public int refinementLevelCost(net.minecraft.server.level.ServerPlayer s, ItemStack p, int levels) { return -9; }
        };
        ISmithingCostModifier greedy = new ISmithingCostModifier() {
            @Override public int metalUnits(net.minecraft.server.level.ServerPlayer s, SmithingRecipe r, int units) { return units + 100; }
        };
        try {
            h.assertTrue(ImmersiveSmithingAPI.metalUnits(smith, recipe) == 18, "No modifiers: the recipe's own units");
            h.assertTrue(ImmersiveSmithingAPI.refinementLevelCost(smith, piece, 3) == 3, "No modifiers: the base level cost");
            ImmersiveSmithingAPI.registerCostModifier(discount);
            h.assertTrue(ImmersiveSmithingAPI.metalUnits(smith, recipe) == 13, "A discount lowers the units");
            h.assertTrue(ImmersiveSmithingAPI.refinementLevelCost(smith, piece, 3) == 2, "A discount lowers the levels");
            ImmersiveSmithingAPI.registerCostModifier(ruinous);
            h.assertTrue(ImmersiveSmithingAPI.metalUnits(smith, recipe) == 1, "Never fewer than one unit");
            h.assertTrue(ImmersiveSmithingAPI.refinementLevelCost(smith, piece, 3) == 0, "Never fewer than zero levels");
            ImmersiveSmithingAPI.unregisterCostModifier(ruinous);
            ImmersiveSmithingAPI.registerCostModifier(greedy);
            h.assertTrue(ImmersiveSmithingAPI.metalUnits(smith, recipe) == 18, "Never more than the recipe's own units");
        } finally {
            for (ISmithingCostModifier m : List.of(discount, ruinous, greedy)) ImmersiveSmithingAPI.unregisterCostModifier(m);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void assistanceOnlyLengthensAndStopsAtTheCeiling(GameTestHelper h) {
        FakePlayer smith = TestSupport.player(h);
        int base = 30_000;
        double ceiling = ServerConfig.get(ServerConfig.MAX_ASSIST_TIME_MULTIPLIER);
        ISmithingAssistSource shorter = (p, s) -> 0.5;
        ISmithingAssistSource longer = (p, s) -> 1.2;
        ISmithingAssistSource huge = (p, s) -> 10.0;
        ISmithingAssistSource broken = (p, s) -> { throw new IllegalStateException("broken source"); };
        try {
            h.assertTrue(ImmersiveSmithingAPI.assistedTimeMs(smith, SmithingStation.FORGE, base) == base, "No sources: the base time");
            ImmersiveSmithingAPI.registerAssistSource(shorter);
            h.assertTrue(ImmersiveSmithingAPI.assistedTimeMs(smith, SmithingStation.FORGE, base) == base, "Assistance never shortens a session");
            ImmersiveSmithingAPI.registerAssistSource(longer);
            h.assertTrue(ImmersiveSmithingAPI.assistedTimeMs(smith, SmithingStation.ANVIL, base) == base, "0.5 x 1.2 is still below 1");
            ImmersiveSmithingAPI.unregisterAssistSource(shorter);
            h.assertTrue(ImmersiveSmithingAPI.assistedTimeMs(smith, SmithingStation.ANVIL, base) == 36_000, "1.2 lengthens by a fifth");
            ImmersiveSmithingAPI.registerAssistSource(huge);
            ImmersiveSmithingAPI.registerAssistSource(broken);
            h.assertTrue(ImmersiveSmithingAPI.assistedTimeMs(smith, SmithingStation.FORGE, base) == Math.round(base * ceiling),
                    "The product stops at maxAssistTimeMultiplier and a throwing source counts as 1");
        } finally {
            for (ISmithingAssistSource s : List.of(shorter, longer, huge, broken)) ImmersiveSmithingAPI.unregisterAssistSource(s);
        }
        h.succeed();
    }
}
