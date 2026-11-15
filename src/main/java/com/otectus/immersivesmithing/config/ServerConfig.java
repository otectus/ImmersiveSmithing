package com.otectus.immersivesmithing.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay configuration, registered as Forge's SERVER type. The values come from the world's own file or, when
 * {@code config/immersive_smithing-server.toml} exists, from that shared file ({@link ServerConfigSource}); remote clients
 * receive the effective values on login and after every reload, so the client can read them once it has joined a world.
 * Every read goes through the helpers at the bottom, which fall back to defaults while no world is loaded.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue REPLACE_METAL_EQUIPMENT_RECIPES;
    public static final ForgeConfigSpec.BooleanValue ALLOW_VANILLA_CRAFTING_ALONGSIDE_SMITHING;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_SMITHING_TABLE_UPGRADES;
    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT_EQUIPMENT;
    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT_MODDED_METALS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_METAL_EXCLUSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MATERIAL_FAMILY_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_DETECT_EXCLUSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMITHING_RECIPE_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    public static final ForgeConfigSpec.IntValue BASE_MELT_TIME_TICKS;
    public static final ForgeConfigSpec.IntValue FORGE_CAPACITY_UNITS;
    public static final ForgeConfigSpec.DoubleValue FUEL_CONSUMPTION_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ACCEPT_ANY_FURNACE_FUEL;
    public static final ForgeConfigSpec.IntValue LAVA_BUCKET_HEAT_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_LAVA_BUCKETS;

    public static final ForgeConfigSpec.BooleanValue CASTING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CASTING_ALLOW_NUGGETS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CASTING_PREFERRED_NAMESPACES;

    public static final ForgeConfigSpec.IntValue STONE_FORGE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue IRON_FORGE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue DIAMOND_FORGE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue NETHERITE_FORGE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue STONE_ANVIL_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue IRON_ANVIL_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue DIAMOND_ANVIL_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue NETHERITE_ANVIL_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue STONE_TOOL_DURABILITY;
    public static final ForgeConfigSpec.IntValue IRON_TOOL_DURABILITY;
    public static final ForgeConfigSpec.IntValue DIAMOND_TOOL_DURABILITY;
    public static final ForgeConfigSpec.IntValue NETHERITE_TOOL_DURABILITY;
    public static final ForgeConfigSpec.IntValue STONE_BLOW_RECOVERY_TICKS;
    public static final ForgeConfigSpec.IntValue IRON_BLOW_RECOVERY_TICKS;
    public static final ForgeConfigSpec.IntValue DIAMOND_BLOW_RECOVERY_TICKS;
    public static final ForgeConfigSpec.IntValue NETHERITE_BLOW_RECOVERY_TICKS;

    public static final ForgeConfigSpec.DoubleValue WORK_HEAT_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue WORK_COOLING_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue WORK_HEATING_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue WORK_LEASE_IDLE_SECONDS;
    public static final ForgeConfigSpec.IntValue WORK_JOB_PRIORITY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue WORK_OVERHEAT_BURNS;
    public static final ForgeConfigSpec.DoubleValue TROUGH_QUENCH_TIME_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue GRINDSTONE_PASS_TICKS;

    public static final ForgeConfigSpec.IntValue READY_DELAY_MS;
    public static final ForgeConfigSpec.IntValue TIMING_TOLERANCE_MS;
    public static final ForgeConfigSpec.IntValue MAX_LATENCY_COMPENSATION_MS;
    public static final ForgeConfigSpec.DoubleValue MAX_STATION_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_ASSIST_TIME_MULTIPLIER;

    public static final ForgeConfigSpec.DoubleValue MINIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue FAULTY_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue FAULTY_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TOOL_ATTACK_EFFICACY_FACTOR;
    public static final ForgeConfigSpec.BooleanValue SCALE_ARMOR_TOUGHNESS;

    public static final ForgeConfigSpec.DoubleValue RECYCLING_EFFICIENCY;

    public static final ForgeConfigSpec.IntValue TROUGH_QUENCHES_PER_BUCKET;
    public static final ForgeConfigSpec.IntValue TROUGH_CAPACITY_BUCKETS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SMITH_GRINDSTONE;
    public static final ForgeConfigSpec.IntValue GRINDSTONE_SCORE_STEP;
    public static final ForgeConfigSpec.IntValue GRINDSTONE_BASE_LEVEL_COST;
    public static final ForgeConfigSpec.IntValue GRINDSTONE_SCORE_PER_EXTRA_LEVEL;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_TRADES;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FAULTY_VILLAGER_ITEMS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTOMATION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FORGE_METAL_FACES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FORGE_FUEL_FACES;
    public static final ForgeConfigSpec.BooleanValue TROUGH_FLUID_INPUT;

    public static final ForgeConfigSpec.BooleanValue GRANT_GUIDE_BOOK_AUTOMATICALLY;

    public static final ForgeConfigSpec.BooleanValue SPARTAN_WEAPONRY_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue EPIC_FIGHT_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue SIMPLY_SWORDS_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue EPIC_KNIGHTS_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue EPIC_KNIGHTS_RANGED_EFFICACY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EPIC_KNIGHTS_HELMET_PAIRS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EPIC_KNIGHTS_DISABLED_ADDONS;
    public static final ForgeConfigSpec.BooleanValue TERRAFIRMACRAFT_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue FIRE_VANILLA_CRAFT_EVENT;
    public static final ForgeConfigSpec.BooleanValue INHERIT_QUALITY_ON_CRAFT;

    public enum LootQualityMode { OFF, STANDARD, RANDOM }

    public static final ForgeConfigSpec.EnumValue<LootQualityMode> LOOT_QUALITY_MODE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_QUALITY_EXCLUDED_TABLES;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SIGNING;
    public static final ForgeConfigSpec.BooleanValue OPEN_SIGNING_AFTER_QUENCH;
    public static final ForgeConfigSpec.BooleanValue ALLOW_RESIGNING;
    public static final ForgeConfigSpec.IntValue MAX_TITLE_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_INSCRIPTION_LINES;
    public static final ForgeConfigSpec.IntValue MAX_INSCRIPTION_LINE_LENGTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Recipe replacement and automatic compatibility").push("recipes");
        REPLACE_METAL_EQUIPMENT_RECIPES = b.comment("Disable ordinary crafting recipes for metal equipment that can be smithed.")
                .define("replaceMetalEquipmentRecipes", true);
        ALLOW_VANILLA_CRAFTING_ALONGSIDE_SMITHING = b.comment("Keep the original crafting recipes. Smithing remains available as an alternative.")
                .define("allowVanillaCraftingAlongsideSmithing", false);
        SUPPRESS_SMITHING_TABLE_UPGRADES = b.comment("Also disable smithing-table upgrade recipes (such as netherite upgrades) whose result can be smithed directly.")
                .define("suppressSmithingTableUpgrades", true);
        AUTO_DETECT_EQUIPMENT = b.comment("Generate smithing recipes by analysing crafting recipes of weapons, tools, armor and metal shields.")
                .define("autoDetectEquipment", true);
        AUTO_DETECT_MODDED_METALS = b.comment("Create material families from forge:ingots/<metal> tags that no datapack defines.")
                .define("autoDetectModdedMetals", true);
        AUTO_METAL_EXCLUSIONS = b.comment("forge:ingots/<name> tags that are never turned into material families.")
                .defineListAllowEmpty("autoMetalExclusions", List.of("brick", "nether_brick"), o -> o instanceof String);
        RECIPE_BLACKLIST = b.comment("Native recipe ids (crafting or smithing table, e.g. minecraft:iron_sword) that automatic detection never",
                        "converts and that are never disabled. Another recipe for the same item may still be converted; this one stays.")
                .defineListAllowEmpty("recipeBlacklist", List.of(), o -> o instanceof String);
        MATERIAL_FAMILY_BLACKLIST = b.comment("Material family ids (e.g. forge:steel) that do not exist for Immersive Smithing: the metal cannot be",
                        "melted and every smithing recipe in it is inactive. The only material-level switch.")
                .defineListAllowEmpty("materialFamilyBlacklist", List.of(), o -> o instanceof String);
        MOD_BLACKLIST = b.comment("Mod ids (item namespaces) whose equipment is neither auto-detected nor disabled, whichever mod's recipe",
                        "makes it. Explicit smithing recipes for its items (datapacks, built-in data) still apply.")
                .defineListAllowEmpty("modBlacklist", List.of(), o -> o instanceof String);
        AUTO_DETECT_EXCLUSIONS = b.comment("Item ids and #tags (e.g. examplemod:steel_dagger, #examplemod:ceremonial_armor) that automatic detection",
                        "makes no smithing recipe for: crafting recipes, smithing-table upgrades and upgrade chains. Their native recipes",
                        "stay, explicit smithing recipes are unaffected, and the items can still be melted down. Tags are read at every reload.")
                .defineListAllowEmpty("autoDetectExclusions", List.of(), o -> o instanceof String);
        SMITHING_RECIPE_BLACKLIST = b.comment("Exact smithing recipe ids to switch off: explicit ones (immersive_smithing:smithing/netherite_helmet,",
                        "immersive_smithing:compat/..., a datapack's) or generated ones (immersive_smithing:auto/<namespace>/<item>). The",
                        "native recipes they would have replaced stay, and automatic detection does not stand in for an explicit one.")
                .defineListAllowEmpty("smithingRecipeBlacklist", List.of(), o -> o instanceof String);
        DEBUG_LOGGING = b.comment("Log every detection decision at INFO level instead of DEBUG.")
                .define("debugLogging", false);
        b.pop();

        b.comment("Smith's Forge").push("forge");
        BASE_MELT_TIME_TICKS = b.comment("Ticks needed to melt a batch before the material family multiplier is applied.")
                .defineInRange("baseMeltTimeTicks", 160, 20, 12000);
        FORGE_CAPACITY_UNITS = b.comment("Maximum material units (nugget = 1, ingot = 9, block = 81) a forge holds.")
                .defineInRange("forgeCapacityUnits", 648, 9, 100000);
        FUEL_CONSUMPTION_MULTIPLIER = b.comment("How much faster than a furnace the forge burns solid fuel.")
                .defineInRange("fuelConsumptionMultiplier", 4.0, 0.1, 100.0);
        ACCEPT_ANY_FURNACE_FUEL = b.comment("Accept every furnace fuel, not only items in #immersive_smithing:forge_fuels.")
                .define("acceptAnyFurnaceFuel", false);
        LAVA_BUCKET_HEAT_TICKS = b.comment("Melting ticks provided by one bucket of lava.")
                .defineInRange("lavaBucketHeatTicks", 4000, 20, 1000000);
        MAX_LAVA_BUCKETS = b.comment("How many buckets of lava the forge can hold.")
                .defineInRange("maxLavaBuckets", 4, 1, 64);
        b.pop();

        b.comment("Casting molten metal back into ingots and nuggets at the forge (empty Smithing Tongs, the Cast panel). No hands-on work,",
                "quality or experience: 9 units make an ingot, 1 unit a nugget. Ignores the recipe options and exclusions.").push("casting");
        CASTING_ENABLED = b.comment("Offer casting at the forge.")
                .define("enabled", true);
        CASTING_ALLOW_NUGGETS = b.comment("Also cast nuggets, for metal that does not make a whole ingot (only families with nugget items).")
                .define("allowNuggets", true);
        CASTING_PREFERRED_NAMESPACES = b.comment("When several ingots (or nuggets) of one metal exist, mods listed here come first, in this order;",
                "then the metal's own namespace, then item ids alphabetically. A material file's \"casting\" entry overrides this.")
                .defineListAllowEmpty("preferredNamespaces", List.of("minecraft"), o -> o instanceof String);
        b.pop();

        b.comment("Smithing tool tiers: durability and how quickly a hammer recovers between blows. Stone tools complete every",
                "process; better tools are more durable and slightly quicker, never better at quality.").push("tools");
        String deadline = "Deprecated in 1.3.0, no effect: the forge and anvil no longer have deadlines. Kept so existing files stay valid.";
        STONE_FORGE_TIME_SECONDS = b.comment(deadline).defineInRange("stoneForgeTimeSeconds", 30, 5, 600);
        IRON_FORGE_TIME_SECONDS = b.comment(deadline).defineInRange("ironForgeTimeSeconds", 35, 5, 600);
        DIAMOND_FORGE_TIME_SECONDS = b.comment(deadline).defineInRange("diamondForgeTimeSeconds", 40, 5, 600);
        NETHERITE_FORGE_TIME_SECONDS = b.comment(deadline).defineInRange("netheriteForgeTimeSeconds", 45, 5, 600);
        STONE_ANVIL_TIME_SECONDS = b.comment(deadline).defineInRange("stoneAnvilTimeSeconds", 30, 5, 600);
        IRON_ANVIL_TIME_SECONDS = b.comment(deadline).defineInRange("ironAnvilTimeSeconds", 35, 5, 600);
        DIAMOND_ANVIL_TIME_SECONDS = b.comment(deadline).defineInRange("diamondAnvilTimeSeconds", 40, 5, 600);
        NETHERITE_ANVIL_TIME_SECONDS = b.comment(deadline).defineInRange("netheriteAnvilTimeSeconds", 45, 5, 600);
        STONE_BLOW_RECOVERY_TICKS = b.comment("Ticks a stone hammer needs between accepted blows (a heavy blow takes half again as long).")
                .defineInRange("stoneBlowRecoveryTicks", 12, 4, 40);
        IRON_BLOW_RECOVERY_TICKS = b.defineInRange("ironBlowRecoveryTicks", 11, 4, 40);
        DIAMOND_BLOW_RECOVERY_TICKS = b.defineInRange("diamondBlowRecoveryTicks", 10, 4, 40);
        NETHERITE_BLOW_RECOVERY_TICKS = b.defineInRange("netheriteBlowRecoveryTicks", 9, 4, 40);
        STONE_TOOL_DURABILITY = b.defineInRange("stoneToolDurability", 512, 1, 100000);
        IRON_TOOL_DURABILITY = b.defineInRange("ironToolDurability", 1024, 1, 100000);
        DIAMOND_TOOL_DURABILITY = b.defineInRange("diamondToolDurability", 2048, 1, 100000);
        NETHERITE_TOOL_DURABILITY = b.defineInRange("netheriteToolDurability", 4096, 1, 100000);
        b.pop();

        b.comment("Station reach and validation. (The section keeps its old name so existing files stay valid.)").push("minigames");
        String timing = "Deprecated in 1.3.0, no effect: station work is simulated on server ticks, without client timestamps.";
        READY_DELAY_MS = b.comment(timing).defineInRange("readyDelayMs", 1500, 0, 10000);
        TIMING_TOLERANCE_MS = b.comment(timing).defineInRange("timingToleranceMs", 120, 0, 2000);
        MAX_LATENCY_COMPENSATION_MS = b.comment(timing).defineInRange("maxLatencyCompensationMs", 400, 0, 5000);
        MAX_STATION_DISTANCE = b.comment("Maximum distance in blocks between a player's eyes and the station they are working at.")
                .defineInRange("maxStationDistance", 6.0, 2.0, 32.0);
        MAX_ASSIST_TIME_MULTIPLIER = b.comment("Ceiling on the assistance other mods may grant a smith (ISmithingAssistSource). Since 1.3.0 it",
                        "shortens the hammer's recovery between blows instead of extending a deadline. 1 refuses all assistance.")
                .defineInRange("maxAssistTimeMultiplier", 1.5, 1.0, 3.0);
        b.pop();

        b.comment("Hands-on station work: heat, shaping, quality and who may work where.").push("work");
        WORK_HEAT_WEIGHT = b.comment("Share of the forge score that comes from heat preparation; the rest is quench control.")
                .defineInRange("heatPreparationWeight", 0.70, 0.0, 1.0);
        WORK_COOLING_MULTIPLIER = b.comment("How fast hot pieces cool in air (applied when a job is committed).")
                .defineInRange("coolingMultiplier", 1.0, 0.25, 4.0);
        WORK_HEATING_MULTIPLIER = b.comment("How fast the forge heats a billet or a piece on the reheat rest (applied when a job is committed).")
                .defineInRange("heatingMultiplier", 1.0, 0.25, 4.0);
        WORK_LEASE_IDLE_SECONDS = b.comment("A smith's hold on a station lapses after this long without an action. The work itself stays.")
                .defineInRange("leaseIdleSeconds", 30, 5, 600);
        WORK_JOB_PRIORITY_SECONDS = b.comment("A committed forge job belongs to its smith for this long after they last worked it (online or not);",
                        "after that anyone may work, draw or cancel it (components cancelled by someone else drop at the forge).")
                .defineInRange("jobPrioritySeconds", 300, 0, 86400);
        WORK_OVERHEAT_BURNS = b.comment("Destructive overheating: metal held above 0.95 heat for three seconds burns and the piece will be Faulty.",
                        "A warning is shown first. Off: overheating only builds scale, a bounded and recoverable quality loss.")
                .define("overheatBurns", false);
        b.pop();

        b.comment("Quality multipliers. A score of 50 is the midpoint between minimum and maximum.").push("quality");
        MINIMUM_DURABILITY_MULTIPLIER = b.defineInRange("minimumDurabilityMultiplier", 0.75, 0.05, 10.0);
        MAXIMUM_DURABILITY_MULTIPLIER = b.defineInRange("maximumDurabilityMultiplier", 1.25, 0.05, 10.0);
        FAULTY_DURABILITY_MULTIPLIER = b.defineInRange("faultyDurabilityMultiplier", 0.50, 0.05, 10.0);
        MINIMUM_EFFICACY_MULTIPLIER = b.defineInRange("minimumEfficacyMultiplier", 0.85, 0.05, 10.0);
        MAXIMUM_EFFICACY_MULTIPLIER = b.defineInRange("maximumEfficacyMultiplier", 1.15, 0.05, 10.0);
        FAULTY_EFFICACY_MULTIPLIER = b.defineInRange("faultyEfficacyMultiplier", 0.70, 0.05, 10.0);
        TOOL_ATTACK_EFFICACY_FACTOR = b.comment("Share of the efficacy deviation applied to the attack damage of mining tools.")
                .defineInRange("toolAttackEfficacyFactor", 0.5, 0.0, 1.0);
        SCALE_ARMOR_TOUGHNESS = b.comment("Scale armor toughness with efficacy when the item already has toughness.")
                .define("scaleArmorToughness", true);
        b.pop();

        b.push("recycling");
        RECYCLING_EFFICIENCY = b.comment("Share of an item's metal recovered when it is melted down.")
                .defineInRange("recyclingEfficiency", 1.0, 0.0, 1.0);
        b.pop();

        b.push("trough");
        TROUGH_QUENCHES_PER_BUCKET = b.defineInRange("troughQuenchesPerBucket", 4, 1, 64);
        TROUGH_CAPACITY_BUCKETS = b.defineInRange("troughCapacityBuckets", 2, 1, 16);
        TROUGH_QUENCH_TIME_MULTIPLIER = b.comment("Scales every process profile's quench duration.")
                .defineInRange("quenchTimeMultiplier", 1.0, 0.25, 4.0);
        b.pop();

        b.push("grindstone");
        ENABLE_SMITH_GRINDSTONE = b.define("enableSmithGrindstone", true);
        GRINDSTONE_SCORE_STEP = b.comment("Score gained per refinement.").defineInRange("scorePerRefinement", 5, 1, 100);
        GRINDSTONE_BASE_LEVEL_COST = b.comment("XP levels charged for a refinement of a score of 0.").defineInRange("baseLevelCost", 1, 0, 100);
        GRINDSTONE_SCORE_PER_EXTRA_LEVEL = b.comment("Every this many score points add one XP level to the cost.").defineInRange("scorePerExtraLevel", 20, 1, 100);
        GRINDSTONE_PASS_TICKS = b.comment("How long an edge must be held to the wheel for one refinement, in ticks.")
                .defineInRange("passTicks", 40, 10, 400);
        b.pop();

        b.push("villagers");
        ENABLE_VILLAGER_TRADES = b.define("enableVillagerTrades", true);
        ALLOW_FAULTY_VILLAGER_ITEMS = b.define("allowFaultyVillagerItems", false);
        b.pop();

        b.comment("Automation. Hoppers and pipes can supply stations (metal, fuel, water) but never do the hands-on work.").push("automation");
        ENABLE_AUTOMATION = b.define("enableAutomation", true);
        FORGE_METAL_FACES = b.comment("Faces of the Smith's Forge that accept metal (up, down, north, south, east, west, relative to a north-facing forge).")
                .defineListAllowEmpty("forgeMetalFaces", List.of("up", "east", "west"), o -> o instanceof String);
        FORGE_FUEL_FACES = b.comment("Faces of the Smith's Forge that accept fuel.")
                .defineListAllowEmpty("forgeFuelFaces", List.of("down", "south"), o -> o instanceof String);
        TROUGH_FLUID_INPUT = b.comment("Allow pipes to fill the Smith's Trough with water.")
                .define("troughFluidInput", true);
        b.pop();

        b.push("guide");
        GRANT_GUIDE_BOOK_AUTOMATICALLY = b.comment("Give players the Smithing Guide when they first obtain smithing tools.")
                .define("grantGuideBookAutomatically", true);
        b.pop();

        b.comment("Built-in support for other mods. Changes need /reload.").push("integrations");
        SPARTAN_WEAPONRY_INTEGRATION = b.comment("Smith every Spartan Weaponry metal weapon with the built-in recipes. When off, Spartan Weaponry is left to automatic detection like any other mod.")
                .define("spartanWeaponry", true);
        EPIC_FIGHT_INTEGRATION = b.comment("Smith Epic Fight's metal weapons with the built-in recipes (the uchigatana included) and disable the crafting",
                        "and smithing-table recipes they replace; adds a crafting recipe for the bokken. When off, every Epic Fight recipe is",
                        "left exactly as Epic Fight ships it (automatic detection skips the mod too); weapons already graded keep their quality.")
                .define("epicFight", true);
        EPIC_KNIGHTS_INTEGRATION = b.comment("Smith Epic Knights and its add-ons' metal weapons, armour and shields with the built-in recipes and disable the",
                        "native recipes they replace. Gilding, darkening, visor swaps, decorations and unique relics keep their own acquisition.",
                        "When off, Epic Knights and every add-on are left exactly as they ship; pieces already graded keep their quality.")
                .define("epicKnights", true);
        EPIC_KNIGHTS_DISABLED_ADDONS = b.comment("Epic Knights mods (mod ids, e.g. \"slavicarmory\") to leave exactly as they ship while the others are integrated.")
                .defineListAllowEmpty("epicKnightsDisabledAddons", List.of(), o -> o instanceof String);
        EPIC_KNIGHTS_RANGED_EFFICACY = b.comment("Apply quality once at supported Epic Knights firearm/projectile launch boundaries. Does not change ammunition, spread, reload or armor piercing.")
                .define("epicKnightsRangedEfficacy", true);
        EPIC_KNIGHTS_HELMET_PAIRS = b.comment("Server-authorized Swappable Helms pairs (item_A-item_B, both directions). Its own HelmetPairs is CLIENT-only.",
                        "Defaults match the pinned addon. Configure custom pairs here and in each client's quick_swap config.",
                        "Only registered head armor is accepted. Equal durability preserves damage; other pairs round proportional wear up.")
                .defineListAllowEmpty("epicKnightsHelmetPairs", com.otectus.immersivesmithing.compat.epicknights.VisorPairs.DEFAULTS, o -> o instanceof String);
        TERRAFIRMACRAFT_INTEGRATION = b.comment("TerraFirmaCraft and Epic Knights of TerraFirma have their own metalworking. While false, Immersive Smithing never",
                        "converts, disables or grades their equipment. True treats them like any other mod (automatic detection).")
                .define("terraFirmaCraft", false);
        SIMPLY_SWORDS_INTEGRATION = b.comment("Smith Simply Swords' metal weapons: automatic detection and suppression of its crafting and smithing-table",
                        "recipes, and quality on its runic and unique weapons. Runic upgrades, rerolls and unique weapons always keep their",
                        "native acquisition. When off, every Simply Swords recipe is left as shipped; weapons already graded keep their quality.")
                .define("simplySwords", true);
        FIRE_VANILLA_CRAFT_EVENT = b.comment("Also fire the vanilla ItemCraftedEvent when a piece is quenched, so mods that reward crafting see forged items. ItemSmithedEvent is always posted.")
                .define("fireVanillaCraftEvent", true);
        INHERIT_QUALITY_ON_CRAFT = b.comment("When a crafting recipe turns exactly one graded piece into other equipment (dyeing, an elemental upgrade, a sword",
                        "made into a spear), the result keeps the quality and the maker's mark, already in the crafting preview.",
                        "Smithing-table upgrades and rerolls carry the piece's data natively and are not affected by this option.")
                .define("inheritQualityOnCraft", true);
        b.pop();

        b.comment("Quality on gear found in the world (chests, mob loot tables, treasure bags). Graded: items the forge can make, plus",
                "#immersive_smithing:quality_eligible (loot, runic and unique weapons of the supported weapon mods).").push("lootQuality");
        LOOT_QUALITY_MODE = b.comment("OFF: loot stays ungraded. STANDARD: scores stay inside the Standard band, so Fine and Masterwork are always someone's work. RANDOM: the villager distribution, Masterwork included.")
                .defineEnum("mode", LootQualityMode.STANDARD);
        LOOT_QUALITY_EXCLUDED_TABLES = b.comment("Loot table ids (namespace:path) or whole namespaces that are never graded.")
                .defineListAllowEmpty("excludedLootTables", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("The Maker's Mark: every finished piece records its smith; the smith may title and inscribe it.").push("signing");
        ENABLE_SIGNING = b.define("enableSigning", true);
        OPEN_SIGNING_AFTER_QUENCH = b.comment("Legacy preference: open the engraving screen as soon as a piece is finished (collected from the anvil, or",
                        "lifted from the trough). Off by default since 1.3.0; existing files keep their saved value. Engrave any time by",
                        "sneak-using the Smith's Anvil with the piece in hand.")
                .define("openAfterQuench", false);
        ALLOW_RESIGNING = b.comment("Let the smith change a title or inscription they already wrote.")
                .define("allowResigning", true);
        MAX_TITLE_LENGTH = b.defineInRange("maxTitleLength", 32, 1, 64);
        MAX_INSCRIPTION_LINES = b.defineInRange("maxInscriptionLines", 3, 0, 6);
        MAX_INSCRIPTION_LINE_LENGTH = b.defineInRange("maxInscriptionLineLength", 48, 8, 128);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static <T extends Enum<T>> T get(ForgeConfigSpec.EnumValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        return (List<String>) (SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    private ServerConfig() {}
}
