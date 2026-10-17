package com.otectus.immersivesmithing.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.recipe.SourceRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Development aid, not a check: with {@code -Dimmersive_smithing.registryDump=ns1,ns2} (or {@code all} for every loaded
 * mod but Minecraft and Forge) it writes {@code logs/immersive_smithing_registry_<ns>.json}, the live registry as the
 * game sees it: every item's class chain, stack size, durability, the metal families it is repaired with, its tags and
 * the recipes that make it. tools/generate_epic_compat.py --registry reads these to confirm what the jars register.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RegistryDumpGameTests {
    private static final List<String> NON_METAL_SAMPLES = List.of("minecraft:oak_planks", "minecraft:cobblestone", "minecraft:diamond",
            "minecraft:emerald", "minecraft:leather", "minecraft:rabbit_hide", "minecraft:string", "minecraft:white_wool", "minecraft:bone",
            "minecraft:stick", "minecraft:flint", "minecraft:phantom_membrane", "minecraft:scute", "minecraft:amethyst_shard",
            "minecraft:prismarine_shard", "minecraft:obsidian", "minecraft:blaze_rod", "iceandfire:dragonbone", "iceandfire:witherbone",
            "iceandfire:myrmex_desert_chitin", "iceandfire:myrmex_jungle_chitin", "magistuarmory:woolen_fabric");

    @GameTest(template = "empty")
    public static void dumpRegistriesWhenAsked(GameTestHelper h) throws IOException {
        String wanted = System.getProperty("immersive_smithing.registryDump", "").trim();
        if (wanted.isEmpty()) {
            h.succeed();
            return;
        }
        TreeSet<String> namespaces = new TreeSet<>();
        if (wanted.equals("all")) {
            ModList.get().getMods().forEach(m -> namespaces.add(m.getModId()));
            namespaces.removeAll(List.of("minecraft", "forge", ImmersiveSmithing.MOD_ID));
        } else {
            for (String ns : wanted.split(",")) if (!ns.isBlank()) namespaces.add(ns.trim());
        }
        var access = h.getLevel().registryAccess();
        List<Recipe<?>> sources = SmithingData.current().sourceRecipes();
        for (String ns : namespaces) {
            JsonArray items = new JsonArray();
            List<Item> registered = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ns)) registered.add(item);
            }
            registered.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
            for (Item item : registered) {
                ItemStack stack = new ItemStack(item);
                JsonObject o = new JsonObject();
                o.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
                JsonArray classes = new JsonArray();
                for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) classes.add(c.getName());
                o.add("classes", classes);
                o.addProperty("maxStack", stack.getMaxStackSize());
                o.addProperty("damageable", stack.isDamageableItem());
                o.addProperty("maxDamage", stack.getMaxDamage());
                o.addProperty("candidate", EquipmentClassifier.isCandidate(stack));
                o.addProperty("shape", EquipmentClassifier.classify(stack).toString());
                EquipmentSlot slot = EquipmentClassifier.armorSlot(stack);
                if (slot != null) o.addProperty("armorSlot", slot.getName());
                o.addProperty("shield", EquipmentClassifier.isShield(stack));
                double damage = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                        .mapToDouble(a -> a.getAmount()).sum();
                o.addProperty("attackDamage", damage);
                JsonArray repair = new JsonArray();
                for (MaterialFamily family : SmithingData.current().materials().families()) {
                    boolean repairs = family.sources().stream().anyMatch(s -> {
                        try {
                            return item.isValidRepairItem(stack, new ItemStack(s.item()));
                        } catch (RuntimeException e) {
                            return false;
                        }
                    });
                    if (repairs) repair.add(family.id().toString());
                }
                o.add("repairFamilies", repair);
                // Common non-metal repair materials: wood, stone, gems, leather, cloth and mob materials.
                JsonArray repairOther = new JsonArray();
                for (String sample : NON_METAL_SAMPLES) {
                    Item other = BuiltInRegistries.ITEM.get(new ResourceLocation(sample));
                    try {
                        if (other != net.minecraft.world.item.Items.AIR && item.isValidRepairItem(stack, new ItemStack(other))) repairOther.add(sample);
                    } catch (RuntimeException ignored) {
                        // a broken repair check repairs nothing
                    }
                }
                o.add("repairOther", repairOther);
                JsonArray tags = new JsonArray();
                stack.getTags().map(t -> t.location().toString()).sorted().forEach(tags::add);
                o.add("tags", tags);
                JsonArray made = new JsonArray();
                for (Recipe<?> r : sources) {
                    if (r instanceof SmithingRecipe) continue;
                    ItemStack result;
                    try {
                        result = r.getResultItem(access);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (result.isEmpty() || result.getItem() != item) continue;
                    JsonObject m = new JsonObject();
                    m.addProperty("id", r.getId().toString());
                    ResourceLocation serializer = SourceRecipes.serializerId(r);
                    m.addProperty("serializer", serializer == null ? "?" : serializer.toString());
                    m.addProperty("kind", SourceRecipes.kind(r).name());
                    m.addProperty("count", result.getCount());
                    JsonArray ingredients = new JsonArray();
                    for (Ingredient ingredient : r.getIngredients()) {
                        if (ingredient.isEmpty()) continue;
                        TreeSet<String> ids = new TreeSet<>();
                        for (ItemStack s : ingredient.getItems()) ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                        ingredients.add(ids.size() <= 3 ? String.join("|", ids) : ids.first() + "|+" + (ids.size() - 1));
                    }
                    m.add("ingredients", ingredients);
                    made.add(m);
                }
                o.add("recipes", made);
                items.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("namespace", ns);
            root.addProperty("version", ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
            root.add("items", items);
            Path dir = Path.of("logs");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("immersive_smithing_registry_" + ns + ".json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n", StandardCharsets.UTF_8);
        }
        h.succeed();
    }

    private RegistryDumpGameTests() {}
}
