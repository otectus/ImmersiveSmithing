package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.material.CastOutputs;
import com.otectus.immersivesmithing.material.MaterialDefinition;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.minigame.SmithingSession;
import com.otectus.immersivesmithing.recipe.CompatibilityReport;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/**
 * Casting molten metal back into ingots and nuggets at the forge: exact conservation (9 units an ingot, 1 a nugget),
 * which items each metal casts into, and a request checked like every other input (duplicates, wrong stage, a full
 * inventory, a definition that changed since the screen opened, a second smith at a locked forge).
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CastingGameTests {
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron");
    private static final ResourceLocation PEARLITE = new ResourceLocation("immersive_smithing_test", "pearlite");

    private static SmithsForgeBlockEntity forge(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        return (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    /** Seed a casting fixture without inventing an equipment stack worth an arbitrary amount of metal. */
    private static void molten(GameTestHelper h, SmithsForgeBlockEntity forge, ResourceLocation family, int units) {
        forge.refundCast(units, family);
        h.assertTrue(forge.moltenUnits() == units, "Casting fixture contains " + units + " units of " + family);
    }

    private static FakePlayer smith(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.IRON)));
        return player;
    }

    private static SmithingSession open(GameTestHelper h, FakePlayer player, SmithsForgeBlockEntity forge) {
        SessionManager.openForge(player, forge, InteractionHand.MAIN_HAND, SmithingTier.IRON);
        return SessionManager.byPlayer(player.getUUID()).orElseThrow(() -> new AssertionError("The forge screen did not open"));
    }

    private static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** Dropped stacks of the cast item at the forge (other tests' drops are other items, further away). */
    private static int dropsNearForge(GameTestHelper h, Item item) {
        return (int) h.getEntities(net.minecraft.world.entity.EntityType.ITEM, STATION, 2.0).stream()
                .filter(e -> e.getItem().is(item)).count();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void castsExactlyNinePerIngot(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        SmithsForgeBlockEntity.Result[] error = new SmithsForgeBlockEntity.Result[1];
        h.assertTrue(forge.insertMetal(new ItemStack(Items.IRON_INGOT, 64), 64, false, error) == 64 && forge.depositUnits() == 576,
                "64 iron ingots are 576 units");
        h.assertTrue(forge.addLavaBucket(false) == SmithsForgeBlockEntity.Result.OK, "Lava melts them");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(forge.isReady(), "Melted")).thenExecute(() -> {
            FakePlayer player = smith(h);
            SmithingSession session = open(h, player, forge);
            SessionManager.castIngots(player, session.id, 1, id(Items.IRON_INGOT), 3);
            h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 3 && forge.moltenUnits() == 549, "Three ingots cost exactly 27 units");
            h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "The tongs wear by one per cast");
            SessionManager.castIngots(player, session.id, 2, id(Items.IRON_INGOT), 10_000);
            h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 64 && forge.moltenUnits() == 0,
                    "Cast all: all 64 ingots come back and nothing is left, got " + player.getInventory().countItem(Items.IRON_INGOT));
            h.assertTrue(forge.family() == null && forge.isEmpty(), "The empty forge forgets its metal");
            h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty() && !forge.isLocked(), "Casting it empty ends the session");
            h.assertTrue(dropsNearForge(h, Items.IRON_INGOT) == 0, "Nothing was dropped");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void remaindersStayMoltenAndCastAsNuggets(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        molten(h, forge, IRON, 70);
        FakePlayer player = smith(h);
        SmithingSession session = open(h, player, forge);
        SessionManager.castIngots(player, session.id, 1, id(Items.IRON_INGOT), 99);
        h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 7 && forge.moltenUnits() == 7, "Seven ingots, seven units left");
        h.assertTrue(IRON.equals(forge.family()) && SessionManager.byPlayer(player.getUUID()).isPresent(), "The remainder stays molten as iron");
        SessionManager.castIngots(player, session.id, 2, id(Items.IRON_INGOT), 1);
        h.assertTrue(forge.moltenUnits() == 7 && player.getInventory().countItem(Items.IRON_INGOT) == 7, "Too little for an ingot: refused, nothing changes");
        SessionManager.castIngots(player, session.id, 3, id(Items.IRON_NUGGET), 7);
        h.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 7 && forge.isEmpty() && forge.family() == null,
                "The remainder casts as seven nuggets");
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty(), "And the session ends");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void castOutputsResolve(GameTestHelper h) {
        SmithingData data = SmithingData.current();
        record Expect(String family, Item ingot, Item nugget) {}
        for (Expect e : List.of(new Expect("minecraft:iron", Items.IRON_INGOT, Items.IRON_NUGGET), new Expect("minecraft:gold", Items.GOLD_INGOT, Items.GOLD_NUGGET),
                new Expect("minecraft:copper", Items.COPPER_INGOT, null), new Expect("minecraft:netherite", Items.NETHERITE_INGOT, null))) {
            CastOutputs outputs = data.materials().family(new ResourceLocation(e.family())).orElseThrow().casting();
            h.assertTrue(outputs.ingots().stream().anyMatch(o -> o.item() == e.ingot()), e.family() + " casts " + e.ingot() + ": " + outputs);
            h.assertTrue(e.nugget() == null ? outputs.nuggets().stream().allMatch(o ->
                            o.unitsEach() == 1 && o.item().builtInRegistryHolder().is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "nuggets/" + e.family().split(":")[1]))))
                            : outputs.nuggets().stream().anyMatch(o -> o.item() == e.nugget()),
                    e.family() + " nuggets: " + outputs.nuggets());
        }
        h.assertTrue(data.materials().family(new ResourceLocation("minecraft", "iron")).orElseThrow().casting().find(Items.RAW_IRON).isEmpty(),
                "Raw iron is worth an ingot but is never cast");
        h.assertTrue(data.materials().family(new ResourceLocation("immersive_smithing_test", "emeraldine")).orElseThrow().casting().isEmpty(),
                "A metal listed by item without forge:ingots casts into nothing");
        h.assertTrue(data.materials().family(PEARLITE).orElseThrow().casting().ingots().get(0).item() == Items.ENDER_PEARL,
                "A material file's casting entry names the ingot");

        // Several tagged ingots: the preferred namespaces first, then the family's own, then item ids.
        ResourceLocation family = new ResourceLocation("immersive_smithing", "test_metal");
        List<MaterialFamily.Source> tagged = List.of(new MaterialFamily.Source(ModItems.SMITHS_ANVIL.get(), 9, "#forge:ingots/test_metal"),
                new MaterialFamily.Source(Items.COPPER_INGOT, 9, "#forge:ingots/test_metal"), new MaterialFamily.Source(Items.BRICK, 9, "#forge:ingots/test_metal"),
                new MaterialFamily.Source(Items.RAW_COPPER, 9, "#forge:raw_materials/test_metal"));
        CompatibilityReport report = new CompatibilityReport();
        List<Item> order = CastOutputs.resolve(family, tagged, null, List.of("minecraft"), report).ingots().stream().map(CastOutputs.Output::item).toList();
        h.assertTrue(order.equals(List.of(Items.BRICK, Items.COPPER_INGOT, ModItems.SMITHS_ANVIL.get())), "minecraft first by id, then the family's namespace: " + order);
        order = CastOutputs.resolve(family, tagged, null, List.of("immersive_smithing", "minecraft"), report).ingots().stream().map(CastOutputs.Output::item).toList();
        h.assertTrue(order.get(0) == ModItems.SMITHS_ANVIL.get(), "The preferred namespaces decide: " + order);
        order = CastOutputs.resolve(family, tagged, new MaterialDefinition.Casting(true, id(Items.COPPER_INGOT), null), List.of("minecraft"), report)
                .ingots().stream().map(CastOutputs.Output::item).toList();
        h.assertTrue(order.get(0) == Items.COPPER_INGOT && order.size() == 3, "An override comes first: " + order);
        CastOutputs rejected = CastOutputs.resolve(family, tagged, new MaterialDefinition.Casting(true, id(Items.DIAMOND), null), List.of("minecraft"), report);
        h.assertTrue(rejected.ingots().size() == 3 && report.lines().stream().anyMatch(l -> l.contains("minecraft:diamond") && l.contains("is ignored")),
                "An override that is no 9-unit source of the family is reported and ignored");
        h.assertTrue(CastOutputs.resolve(family, tagged, new MaterialDefinition.Casting(false, null, null), List.of(), report).isEmpty(),
                "\"casting\": false switches casting off");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void castsOnlyWhatTheInventoryHolds(GameTestHelper h) {
        var config = TestSupport.isolateServerConfig();
        try {
            ServerConfig.FORGE_CAPACITY_UNITS.set(4000);
            SmithsForgeBlockEntity forge = forge(h);
            molten(h, forge, PEARLITE, 3240);
            FakePlayer player = smith(h);
            for (int slot = 1; slot <= 15; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            SmithingSession session = open(h, player, forge);
            SessionManager.castIngots(player, session.id, 1, id(Items.ENDER_PEARL), 10_000);
            h.assertTrue(player.getInventory().countItem(Items.ENDER_PEARL) == 320 && forge.moltenUnits() == 3240 - 320 * 9,
                    "Twenty free slots of a 16-stack item take 320, got " + player.getInventory().countItem(Items.ENDER_PEARL));
            for (int slot = 16; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                h.assertTrue(stack.is(Items.ENDER_PEARL) && stack.getCount() == 16, "Slot " + slot + " holds a legal stack of 16, got " + stack);
            }
            SessionManager.castIngots(player, session.id, 2, id(Items.ENDER_PEARL), 1);
            h.assertTrue(forge.moltenUnits() == 3240 - 320 * 9, "A full inventory casts nothing: " + forge.moltenUnits());
            h.assertTrue(dropsNearForge(h, Items.ENDER_PEARL) == 0, "And drops nothing");
            h.assertTrue(SessionManager.byPlayer(player.getUUID()).isPresent(), "The smith stays at the forge");
            SessionManager.cancelFromClient(player, session.id);
        } finally {
            TestSupport.restoreServerConfig(config);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void requestsAreCheckedLikeEveryInput(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        molten(h, forge, IRON, 90);
        FakePlayer player = smith(h);
        SmithingSession session = open(h, player, forge);
        SessionManager.castIngots(player, session.id, 5, id(Items.IRON_INGOT), 1);
        SessionManager.castIngots(player, session.id, 5, id(Items.IRON_INGOT), 1);
        SessionManager.castIngots(player, session.id, 4, id(Items.IRON_INGOT), 1);
        h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 1 && forge.moltenUnits() == 81, "A duplicate or replayed request is ignored");
        SessionManager.castIngots(player, session.id, 6, id(Items.GOLD_INGOT), 1);
        SessionManager.castIngots(player, session.id, 7, id(Items.RAW_IRON), 1);
        h.assertTrue(forge.moltenUnits() == 81 && player.getInventory().countItem(Items.GOLD_INGOT) == 0, "Only an offered output of this metal is cast");

        // A second smith cannot use the forge while the first one is at it.
        FakePlayer other = smith(h);
        SessionManager.openForge(other, forge, InteractionHand.MAIN_HAND, SmithingTier.IRON);
        h.assertTrue(SessionManager.byPlayer(other.getUUID()).isEmpty(), "The forge is locked for the second smith");
        SessionManager.castIngots(other, session.id, 8, id(Items.IRON_INGOT), 1);
        h.assertTrue(forge.moltenUnits() == 81 && other.getInventory().countItem(Items.IRON_INGOT) == 0, "Nor can they cast through the first one's session");

        // Choosing a pattern pins it and closes the book: nothing is reserved, and a cast through the closed book is ignored.
        player.getInventory().setItem(5, new ItemStack(Items.STICK, 2));
        SmithingRecipe pickaxe = SmithingData.current().recipesProducing(Items.IRON_PICKAXE).get(0);
        SessionManager.selectRecipe(player, session.id, pickaxe.getId());
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty() && pickaxe.getId().equals(forge.plan()) && !forge.isLocked(),
                "The pattern is pinned and the book closed");
        SessionManager.castIngots(player, session.id, 9, id(Items.IRON_INGOT), 1);
        h.assertTrue(forge.moltenUnits() == 81 && player.getInventory().countItem(Items.STICK) == 2, "Pinning takes nothing; a stale cast is ignored");
        // A committed job's metal is reserved: casting offers only the free metal.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.otectus.immersivesmithing.registry.ModItems.tongs(SmithingTier.IRON)));
        com.otectus.immersivesmithing.work.ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.job() != null && forge.reservedUnits() == 27 && forge.availableUnits() == 54
                && player.getInventory().countItem(Items.STICK) == 0, "Commit reserves 27 units and escrows the sticks");
        session = open(h, player, forge);
        int ingotsBefore = player.getInventory().countItem(Items.IRON_INGOT);
        SessionManager.castIngots(player, session.id, 1, id(Items.IRON_INGOT), 64);
        h.assertTrue(forge.moltenUnits() == 27 && forge.reservedUnits() == 27 && player.getInventory().countItem(Items.IRON_INGOT) - ingotsBefore == 6,
                "Casting all leaves the reservation untouched (" + forge.moltenUnits() + " molten, "
                        + (player.getInventory().countItem(Items.IRON_INGOT) - ingotsBefore) + " ingots)");
        com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge);
        com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge);
        h.assertTrue(forge.job() == null && forge.moltenUnits() == 27 && player.getInventory().countItem(Items.STICK) == 2,
                "Cancelling (confirmed) refunds the sticks once and frees the metal");
        forge.refundCast(54, IRON); // the metal cast above goes back for the rest of the test

        // Leaving ends the session without touching the metal: the station unloading, the smith logging out.
        session = open(h, player, forge);
        SessionManager.onStationUnloaded(h.getLevel(), h.absolutePos(STATION));
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty() && !forge.isLocked() && forge.moltenUnits() == 81, "Unload: session over, metal kept");
        session = open(h, player, forge);
        SessionManager.onLogout(player);
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty() && !forge.isLocked() && forge.moltenUnits() == 81, "Logout: session over, metal kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aReloadBetweenOpenAndCastRevalidates(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        molten(h, forge, IRON, 27);
        FakePlayer player = smith(h);
        SmithingSession session = open(h, player, forge);
        var config = TestSupport.isolateServerConfig();
        try {
            ServerConfig.MATERIAL_FAMILY_BLACKLIST.set(List.of("minecraft:iron"));
            SmithingData.rebuild(h.getLevel().getServer());
            SessionManager.castIngots(player, session.id, 1, id(Items.IRON_INGOT), 1);
            h.assertTrue(forge.moltenUnits() == 27 && player.getInventory().countItem(Items.IRON_INGOT) == 0,
                    "The metal no longer casts into iron ingots after the reload: refused, nothing taken");
            h.assertTrue(SessionManager.byPlayer(player.getUUID()).isPresent(), "The smith stays in the selection");
        } finally {
            TestSupport.restoreServerConfig(config);
            SmithingData.rebuild(h.getLevel().getServer());
        }
        SessionManager.cancelFromClient(player, session.id);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void meltingAndCastingNeverMintMetal(GameTestHelper h) {
        var config = TestSupport.isolateServerConfig();
        SmithsForgeBlockEntity full;
        SmithsForgeBlockEntity half;
        try {
            full = forge(h);
            h.setBlock(new BlockPos(0, 1, 0), ModBlocks.SMITHS_FORGE.get().defaultBlockState());
            half = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(0, 1, 0)));
            SmithsForgeBlockEntity.Result[] error = new SmithsForgeBlockEntity.Result[1];
            ServerConfig.RECYCLING_EFFICIENCY.set(1.0);
            full.insertMetal(new ItemStack(Items.IRON_SWORD), 1, false, error);
            ServerConfig.RECYCLING_EFFICIENCY.set(0.5);
            half.insertMetal(new ItemStack(Items.IRON_SWORD), 1, false, error);
            h.assertTrue(full.depositUnits() == 18 && half.depositUnits() == 9, "Recycling efficiency applies when the sword goes in");
        } finally {
            TestSupport.restoreServerConfig(config);
        }
        full.addLavaBucket(false);
        half.addLavaBucket(false);
        SmithsForgeBlockEntity a = full;
        SmithsForgeBlockEntity b = half;
        h.startSequence().thenWaitUntil(() -> h.assertTrue(a.isReady() && b.isReady(), "Melted")).thenExecute(() -> {
            int[] smithed = {0};
            Consumer<ItemSmithedEvent> listener = e -> smithed[0]++;
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                FakePlayer player = smith(h);
                SmithingSession first = open(h, player, a);
                SessionManager.castIngots(player, first.id, 1, id(Items.IRON_INGOT), 64);
                h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 2 && a.isEmpty(), "At full efficiency the sword casts back into its 2 ingots");
                BlockPos other = h.absolutePos(new BlockPos(0, 1, 0));
                player.setPos(other.getX() + 0.5, other.getY(), other.getZ() + 1.5);
                SmithingSession second = open(h, player, b);
                SessionManager.castIngots(player, second.id, 1, id(Items.IRON_INGOT), 64);
                h.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 3 && b.isEmpty(), "At half efficiency only 1 more: casting adds nothing");
                h.assertTrue(smithed[0] == 0, "Casting posts no ItemSmithedEvent");
                var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(ImmersiveSmithing.id(ModAdvancements.INTO_SHAPE));
                h.assertTrue(advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone(), "And awards no advancement");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
        }).thenSucceed();
    }

    private CastingGameTests() {}
}
