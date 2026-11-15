package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.minigame.SmithingSession;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.ForgeRecipeSelection;
import com.otectus.immersivesmithing.recipe.RecipeDecisions;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/**
 * Netherite armour at the forge: full-shape costs on the armour patterns, the smithing-table upgrades they replace, and a
 * forge with too little metal that shows the armour dimmed with what it still needs instead of hiding it. Modded upgrades
 * in the test datapack cover both upgrade policies.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetheriteGameTests {
    private static final ResourceLocation NETHERITE = new ResourceLocation("minecraft", "netherite");

    private record Piece(Item item, int units, String pattern, String upgrade) {}

    private static final List<Piece> ARMOUR = List.of(
            new Piece(Items.NETHERITE_HELMET, 45, "immersive_smithing:helmet", "minecraft:netherite_helmet_smithing"),
            new Piece(Items.NETHERITE_CHESTPLATE, 72, "immersive_smithing:chestplate", "minecraft:netherite_chestplate_smithing"),
            new Piece(Items.NETHERITE_LEGGINGS, 63, "immersive_smithing:leggings", "minecraft:netherite_leggings_smithing"),
            new Piece(Items.NETHERITE_BOOTS, 36, "immersive_smithing:boots", "minecraft:netherite_boots_smithing"));

    @GameTest(template = "empty")
    public static void netheriteArmourIsForgedAtFullShape(GameTestHelper h) {
        RecipeManager manager = h.getLevel().getServer().getRecipeManager();
        for (Piece piece : ARMOUR) {
            List<SmithingRecipe> recipes = SmithingData.current().recipesProducing(piece.item());
            h.assertTrue(recipes.size() == 1, piece.item() + ": one active forge recipe, got " + recipes.size());
            SmithingRecipe r = recipes.get(0);
            h.assertTrue(!r.isAuto() && r.family().equals(NETHERITE) && r.metalUnits() == piece.units() && r.auxiliary().isEmpty(),
                    piece.item() + " costs " + piece.units() + " netherite units and nothing else, got " + r.metalUnits());
            h.assertTrue(r.anvilPattern().toString().equals(piece.pattern()), piece.item() + " is shaped on " + piece.pattern());
            h.assertTrue(manager.byKey(new ResourceLocation(piece.upgrade())).isEmpty(), "Its smithing-table upgrade is replaced");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armourBeyondTheMetalIsShownWithItsShortfall(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        forge.acceptWorkpiece(new WorkpieceData(new ItemStack(Items.NETHERITE_SWORD), ImmersiveSmithing.id("test"), NETHERITE, 18,
                0, -1, false, WorkpieceState.FORGED, ImmersiveSmithing.id("sword")));
        FakePlayer player = TestSupport.player(h);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.IRON)));
        player.getInventory().setItem(9, new ItemStack(Items.STICK, 4));
        Map<Item, ForgeRecipeSelection.Offer> offers = new java.util.HashMap<>();
        ForgeRecipeSelection.offers(player, forge, 0).forEach(o -> offers.put(o.recipe().resultItem(), o));
        h.assertTrue(offers.get(Items.NETHERITE_SWORD) != null && offers.get(Items.NETHERITE_SWORD).selectable(), "18 units make a netherite sword");
        for (Piece piece : ARMOUR) {
            ForgeRecipeSelection.Offer offer = offers.get(piece.item());
            h.assertTrue(offer != null, piece.item() + " is still offered with 18 units");
            h.assertTrue(!offer.selectable() && offer.shortfall() == piece.units() - 18,
                    piece.item() + " is flagged as needing " + (piece.units() - 18) + " more, got " + offer.shortfall());
            h.assertTrue(offer.reason().getString().contains("more"), "And says so: " + offer.reason().getString());
        }
        h.assertTrue(!ForgeRecipeSelection.eligible(player, forge, 0).contains(SmithingData.current().recipesProducing(Items.NETHERITE_HELMET).get(0)),
                "The choosable list leaves it out");
        // Choosing the dimmed helmet anyway: the answer is the reason, the session stays and nothing is taken.
        SessionManager.openForge(player, forge, InteractionHand.MAIN_HAND, SmithingTier.IRON);
        SmithingSession session = SessionManager.byPlayer(player.getUUID()).orElseThrow();
        SessionManager.selectRecipe(player, session.id, SmithingData.current().recipesProducing(Items.NETHERITE_HELMET).get(0).getId());
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isPresent() && forge.moltenUnits() == 18 && forge.plan() == null,
                "Refused with a notice, the book stays open, nothing is pinned or taken");
        var sword = SmithingData.current().recipesProducing(Items.NETHERITE_SWORD).get(0);
        SessionManager.selectRecipe(player, session.id, sword.getId());
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty() && sword.getId().equals(forge.plan()), "The sword can still be chosen and is pinned");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moddedUpgradesUnderBothPolicies(GameTestHelper h) {
        RecipeManager manager = h.getLevel().getServer().getRecipeManager();
        // Shape policy (lapisine): the iron helmet's shape in the new metal, no base piece needed.
        SmithingRecipe shape = SmithingData.current().recipesProducing(Items.CHAINMAIL_HELMET).get(0);
        h.assertTrue(shape.isAuto() && shape.family().toString().equals("immersive_smithing_test:lapisine") && shape.metalUnits() == 45
                && shape.auxiliary().stream().noneMatch(AuxiliaryIngredient::consumesEquipment), "45 lapisine units, no base: " + shape.metalUnits());
        // Addition policy (emeraldine): one addition, and the base piece is reworked.
        SmithingRecipe addition = SmithingData.current().recipesProducing(Items.TURTLE_HELMET).get(0);
        h.assertTrue(addition.isAuto() && addition.metalUnits() == 9 && addition.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment),
                "One emerald and the reworked base");
        for (String upgrade : List.of("immersive_smithing_test:lapis_upgrade", "immersive_smithing_test:unpriced_base_upgrade")) {
            h.assertTrue(manager.byKey(new ResourceLocation(upgrade)).isEmpty(), upgrade + " is replaced on the smithing table");
        }
        h.assertTrue(SmithingData.current().decisionsFor(Items.CHAINMAIL_HELMET).stream().anyMatch(d -> d.outcome() == RecipeDecisions.Outcome.DISABLED
                && "immersive_smithing_test:lapis_upgrade".equals(String.valueOf(d.recipe()))), "And the decision names it");
        h.succeed();
    }

    private NetheriteGameTests() {}
}
