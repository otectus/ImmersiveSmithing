# Changelog

## 1.2.0 — Epic Knights, built-in Epic Fight recipes and lighter stations

### Epic Fight

- **Built-in recipes.** Epic Fight's metal weapons now have bundled forge recipes instead of automatic detection, so
  they need no setup and work with `autoDetectEquipment` off. Daggers cost 9 units and a stick; longswords and tachis
  36 and a stick; greatswords 54 and a plank; spears 18 and three sticks; the glove 2 units of iron and two leather.
  Netherite weapons are forged from netherite in the diamond shape, without template or base weapon.
- **The uchigatana can be forged**: 36 iron, a diamond, two leather and a stick, on the katana pattern.
- **The bokken has a crafting recipe**: two planks laid diagonally and a stick.

### Epic Knights

- **Built-in support for Epic Knights and its add-ons**: Addon, Slavic Armory, Antique Legacy, Ice and Fire, Ores and
  Alloys, Dark Ages, Open Helms with Swappable Helms, Seven Kingdoms, Artilleries and Firearms and Medieval Boomsticks.
  Their metal weapons, armour and shields are forged, each on an anvil pattern of its shape, and each forge recipe
  disables only the native recipes it replaces.
- **Costs follow the mods' own recipes.** Metal is counted once, intermediates of the same metal flattened (an armet
  is a barbute and three plates: 72 steel) and rounded down, never up. Hilts, poles, leather strips, base shields and
  fittings of another metal are components.
- **Kept routes.** Gilding, darkening and other transformations, visor swaps, firearm assembly, Medieval Boomsticks'
  own smithing furnace, wooden and leather pieces and loot keep their mod's route. Transformed pieces and visor states
  keep the base piece's quality and recycling value. Relics can carry quality as loot and can't be melted down.
- **Plates melt for one ingot** (Epic Knights' steel plate, Antique Legacy's iron and bronze plates, the Seven
  Kingdoms' blacked and gilded steel plates). New metals: blacked steel and gilded steel.
- **New anvil pattern**: flail, for chain morning stars and evening stars.
- **Every item has a stated route.** The catalog gives each piece of equipment a disposition and a reason; the tested
  profiles have none without support. See [docs/compatibility/epic-integrations.md](docs/compatibility/epic-integrations.md)
  for versions, costs and upstream issues, and [docs/compatibility/INVENTORY.md](docs/compatibility/INVENTORY.md) for
  every item.
- **New server options**: `integrations.epicKnights`, `integrations.epicKnightsDisabledAddons` and
  `integrations.terraFirmaCraft` (default off: TerraFirmaCraft keeps its own metalworking).

### Quality

- **Weapons that can block** (many Epic Knights weapons) now count as weapons: their attack damage gets efficacy. A
  shield is a piece that blocks without dealing damage.

### Datapacks and API

- **`replaces` and `derived_from`** recipe fields name the native recipes a forge recipe stands in for or follows. It
  disables exactly those, and it is active only while one of them is loaded, so a pack that removes the native recipe
  removes the forge route too.
- **`sources_only`** material files add sources to a family defined elsewhere, without creating it.
- **`immersive_smithing:mod_version`** condition for data that matches one version range of a mod.
- **Coverage decisions** (`immersive_smithing/coverage`) record how equipment without a forge recipe is obtained.
- **Curated integrations** are never guessed by automatic detection: their items are forged only from bundled recipes.
- Gilding and other upgrades that add metal now inherit the base piece's recycling value.
- The compatibility report summarises inactive built-in recipes in one line per reason.

### Stations

- **All four stations rebuilt from fewer, larger boxes**: model cuboids and faces went from 37 / 222 to 10 / 44 (forge),
  27 / 162 to 8 / 41 (anvil), 11 / 66 to 7 / 32 (trough) and 11 / 66 to 7 / 38 (grindstone). Outlines and collision
  decompose into 10, 10, 5 and 10 boxes in every facing (they were 22 to 25, 32 to 36, 11 and 13). Straps and bands are painted
  on two new textures instead of modelled.
- The forge's lava and molten pool follow the new firebox and basin.

### Forge screen

- **Search by mod**: a word starting with `@` shows one mod's pieces, as in JEI (`@epicknights helmet`).

### Testing

- New GameTests for coverage, Epic Knights, station shapes and a registry dump, with Gradle profiles for Epic Knights
  alone, each add-on and the largest compatible set (`-PepicKnights`).
- The pack-test agent checks Epic Knights in the client, compares the client's synced recipes with the server's on a
  dedicated server, and measures the stations (`-PstationBenchmark=<label>`). `tools/model_preview.py --check`
  enforces the station model budgets.
- GameTests that switch server options now work on an in-memory copy of the config. Forge's config file watcher
  reloads the file on another thread after every save and could undo a switch mid-test.

## 1.1.1 — Compatibility, vanilla screens and cost fixes

### Cost fixes

- **Recycling discounted pieces.** Newly quenched equipment records the metal actually paid. Recycling is capped at
  that amount before efficiency is applied, closing the discounted-forging metal loop. The accounting survives
  crafting repairs and remakes even with quality inheritance disabled. Older pieces without a payment record keep
  their existing recycling value.
- **Empty recipe lists.** The explanation uses the smith's modified metal cost, so an affordable discounted recipe
  correctly reports a missing component or a progression gate instead of asking for more metal.
- **Jade refinement prices.** The Smith's Grindstone overlay shows server-calculated, per-player quotes including
  cost modifiers. Quotes match the held piece and station and expire when stale. Requires network protocol 3 on
  both client and server; update both sides together.

### Epic Fight and Simply Swords

- **Optional support for Epic Fight and Simply Swords**, alone or together. Their metal weapons are forged on anvil
  patterns that match their shapes: Epic Fight longswords, greatswords, tachis, daggers, spears and gloves, and every
  Simply Swords weapon type. Immersive Smithing's code never references either mod, so nothing changes when they are
  absent.
  The equipment inventory (now [docs/compatibility/INVENTORY.md](docs/compatibility/INVENTORY.md)) lists every piece
  of equipment from both mods, with its route, cost, pattern, native recipes, quality and recycling.
- **New patterns and tags.** Three new anvil patterns: twinblade, warglaive and chakram. There are 17 weapon-type tags
  (`immersive_smithing:longswords`, `katanas`, ...) that any mod can add weapons to.
- **Quality applies exactly once:**
  - in Epic Fight's vanilla and battle modes, in the main hand and the off hand;
  - to Simply Swords' attack-scaled abilities;
  - to its thrown weapons (new entity tag `immersive_smithing:weapon_scaled_projectiles`).
- **Loot-only weapons** are graded as loot and can be refined at the grindstone: Epic Fight's uchigatana, and Simply
  Swords' runic and unique weapons (new tag `immersive_smithing:quality_eligible`). Unique weapons can't be melted
  down by default. Runic weapons recycle for the netherite weapon they were made from.
- **Unchanged acquisition.** Runic upgrades, rerolls and unique weapons keep their usual way of being obtained.
- **New server options:** `integrations.epicFight` and `integrations.simplySwords`, both default on. Switched off,
  the mod is left exactly as it ships.
- **New command:** `/immersivesmithing catalog <modid>` writes the same audit for any mod in the running game.

### Recipe handling

- **Native recipes.** A native crafting or smithing-table recipe is disabled only when a forge recipe replaces that
  very recipe. Every other one is kept, and the compatibility report lists it with the reason.
- **Custom recipe types** (a recipe subclass, Simply Swords' runic reroll) are never converted or disabled.
- **The main metal is the one the item is repaired with.** A gold weapon with iron-nugget fittings is forged from gold
  with the nuggets as components. A diamond weapon with iron fittings is no longer treated as iron equipment.
- **Finished pieces in crafting recipes.** A recipe that adds metal to a finished piece grows from it (Epic Fight's
  longsword is an iron sword and two ingots). A recipe that only reshapes a piece (a sword made into a spear) is kept,
  and the result inherits the piece's quality.
- **Repeatable detection.** Detection reads the recipes as the datapacks loaded them, so a `/reload`, a config change or
  a restart gives the same result and never loses an original recipe.
- **Reworked pieces** keep their enchantments, name, lore, wear and other mods' data, as their native recipe would.
  The new recipe field `keep_base_data` (default `true`) controls this. The piece is assembled from the forge's escrow
  before any metal is charged.
- **Quality inheritance** (`inheritQualityOnCraft`) covers crafting recipes, and the crafting preview now shows the
  inherited quality before you take the piece. Smithing-table upgrades and rerolls keep the piece's quality natively,
  whatever the option says.
- **Loot quality** no longer depends on a forge recipe: `#immersive_smithing:quality_eligible` pieces are graded too.
- **Grindstone.** A piece without a grade starts from Standard, written only with a paid refinement.

### Vanilla look

- **Screens.** Every screen uses Minecraft's own look in place of the Stoneborn workshop theme: vanilla container
  panels, buttons, text fields, scrollers and slots. This covers forge selection and the forge minigame, the anvil
  minigame and the Maker's Mark, the latter now with character counters.
- **Guide.** The Smithing Guide reads like a written book: the page texture, the page arrows and "Page x of y". The
  chapter list and search sit beside it, or alternate with it on small screens.
- **JEI.** The category uses JEI's own slots and arrows.
- **Retired.** The theme's textures and classes are removed (`gui/stone.png`, `gui/slot.png`, `gui/guide.png`,
  `StonebornGui`, `StonebornJei`), and the texture checker fails if they return.
- **Layout fixes.** The forge panel shrinks to its content while forging. Recipe rows read "Costs 3 ingots · 9 ingots
  left".

## 1.1.0 — Progression hooks

- `ISmithingRecipeGate` (IMC `recipe_gate`): another mod may refuse a recipe to a smith with a reason. The Forge screen leaves it out and shows the first reason when nothing is left; the selection is refused again before any metal or auxiliary is taken.
- `ISmithingCostModifier` (IMC `cost_modifier`): adjusts the metal units the forge charges (never below one, never above the recipe's own; the minigame keeps the recipe's shape) and the experience levels a Smith's Grindstone refinement costs (never below zero). `ItemSmithedEvent.getMetalUnits()` reports what was actually charged.
- `ISmithingAssistSource` (IMC `assist_source`): per-player time at the forge and anvil, clamped to the new server setting `minigames.maxAssistTimeMultiplier` (default 1.5). No protocol change.

## 1.0.0 - unreleased

First release as Immersive Smithing, for Minecraft 1.20.1 and Forge 47.

### Modpack integration and the Maker's Mark

- **Maker's Mark.** Every quenched piece records its smith ("Crafted by"). A screen after the quench lets the smith
  title the piece and add up to three lines of inscription; sneak-use the Smith's Anvil with the piece to sign it
  later. Titles and inscriptions are stored as the vanilla name and lore, so every tooltip mod shows them, and the
  server sanitises them. Faulty work cannot be signed. New advancement and guide chapter.
- **Loot quality.** Equipment from loot tables (chests, mob loot, treasure bags) that the forge could have made is
  graded, Standard-only by default (`lootQuality.mode`: `OFF`, `STANDARD`, `RANDOM`; `excludedLootTables`).
- **Quality inheritance.** A crafting or smithing-table recipe that turns one graded piece into another keeps the
  quality, the mark, the title and the inscription (`inheritQualityOnCraft`).

#### Compatibility

- **Consumed-equipment auxiliaries** (`"consume_equipment": true`) and a per-material `upgrade_policy`: `shape`
  (today's netherite rule) or `addition` (pay the addition, rework the base piece). Smithing-table upgrades whose
  base has no metal shape of its own (loot weapons, cloth robes) are no longer skipped: they use `addition`.
- **Crafting upgrade chains**: a recipe that reworks a piece the forge makes with more metal (Botania terrasteel
  armour) becomes a smithing recipe with the base consumed.
- **Predominance rule**: recipes with fewer metal slots than components and under one ingot of metal are skipped.
  Reworking equipment without any metal is no longer reported as skipped.
- **Pack metals**: Cataclysm (black steel, ancient metal, witherite, cursium, ignitium), Ice and Fire dragonsteel,
  Spartan Fire dragonsteel weapons (all 24 types, generated), Iron's mithril and pyrium, Botania's three metals and
  MCA's rose gold, with names, tints, melt rules and policies. Iron's cloth wizard sets, Ars Nouveau robes, Ice and
  Fire sheep armour and elytra-like items are tagged non-smithable. Fire Sticks, Lit It Up and Hardcore Torches fire
  starters ignite the forge; `#forge:coal_coke` is fuel.
- **`ItemSmithedEvent`** on the Forge bus for progression mods and KubeJS; the vanilla `ItemCraftedEvent` is fired
  at the quench as well (`integrations.fireVanillaCraftEvent`).
- **Jade** overlays for the forge, anvil, trough and grindstone.
- Villagers also sell forged silver (Ice and Fire) and black steel (Cataclysm) gear when those mods are present.
- The compatibility report starts with a coverage table per mod and marks recipes that rework a base piece.

### Renamed from Ote's Smithing

This mod was previously called Ote's Smithing (builds 1.0.0 and 1.1.0, mod ID `otes_smithing`). Everything from
those builds is included here, and version numbering starts again at 1.0.0.

- The mod ID and datapack namespace are now `immersive_smithing`, the command is `/immersivesmithing`, the Java
  package is `com.otectus.immersivesmithing` and the jar is `immersive-smithing-1.0.0.jar`.
- **Not compatible with Ote's Smithing worlds or datapacks.** Its stations, tools, workpieces and item quality data
  do not carry over. Datapacks must use the `immersive_smithing` namespace and `data/<namespace>/immersive_smithing/`
  folders. The config files are now `immersive_smithing-server.toml` and `immersive_smithing-client.toml`.
- Remove the Ote's Smithing jar when installing this version; the two mods would otherwise both load.

### Smithing

- Four stations: the Smith's Forge melts metal, the Smith's Anvil shapes it, the Smith's Trough quenches it and the
  Smith's Grindstone refines finished equipment for experience levels.
- Smithing Tongs and Smithing Hammers in stone, iron, diamond and netherite. Higher tiers give more minigame time
  and durability, never extra quality.
- Forge minigame (timing on a sweeping marker) sets the Forge score and durability. Anvil minigame (strikes on
  closing rings) sets the Anvil score and efficacy: attack damage, mining speed or armor.
- Quality labels Crude, Standard, Fine and Masterwork; Faulty items when the anvil timer runs out.
- Material families measured in units (nugget 1, ingot 9, block 81); built-in iron, gold, copper and netherite,
  optional definitions for common modded metals, and automatic families from `forge:ingots` tags.
- Metal equipment can be melted back into the forge, recovering its metal.
- Netherite equipment is forged directly from netherite, and lava is the only heat that melts it.
- Hoppers and pipes can load forges and fill troughs; comparators read both. Minigames and quenching always need a
  player.
- The Smithing Guide in-game book, nine advancements, and Toolsmith, Weaponsmith and Armorer trades.

### Compatibility

- Automatic detection turns single-metal crafting recipes for weapons, tools, armor and metal shields from any mod
  into smithing recipes and disables the crafting recipe.
- Built-in Spartan Weaponry recipes and anvil patterns for every metal weapon of all 24 types. Thrown weapons keep
  their quality, and arrows and bolts take the quality of the bow or crossbow that fires them.
- Metal shields no longer need their wooden base shield.
- Smithing-table upgrades whose result can be smithed, modded netherite gear included, are forged from the metal
  instead. Armor trims are untouched.
- Optional JEI category for every smithing recipe.
- Datapack formats and a Java/IMC API for materials, recipes, recycling values, anvil patterns, efficacy and shield
  handlers ([docs/API.md](docs/API.md)).

### Presentation

- Custom station models with four rotations and matching collision shapes. Finished artwork for every tool tier,
  loaded tongs, hot workpieces, the guide and station items.
- Stations show their contents: billets and fuel in the forge, animated molten metal, heated stock on the anvil and
  the water level in the trough.
- Strike and grinding sparks, ignition embers, and quench splashes and steam. Tongs thrust and hammer strikes are
  animated in first and third person.
- Framed recipe and minigame screens, a parchment guide, a matching JEI category and clearer quality tooltips.
- Client options for numeric quality scores, reduced screen shake and flashes, particle amount, high-contrast
  minigames, larger or colorblind-safe targets and timing cue sounds. None of them widen scoring windows.

### Multiplayer

- Minigames are server-authoritative: clients send only their inputs, and the server checks timing with bounded
  latency compensation and calculates the scores.
- Only one player can work on an anvil's workpiece at a time. Components set aside for a forging are held by the
  forge and leave it exactly once: used on completion or returned on cancellation. If the forge's chunk unloads
  first, they drop at the forge when it loads again.

### Known issues

- Sounds reuse vanilla sound events; there is no custom audio yet.
