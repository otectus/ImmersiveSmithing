# Immersive Smithing - Datapack and Java API

Immersive Smithing is configured almost entirely through datapacks. The Java API exists for items that datapacks
cannot describe. Everything below is re-read on `/reload` (or `/immersivesmithing reload`).

## Material units

All metal accounting uses integers: nugget = 1, ingot or raw ore = 9, storage block = 81.

## Smithing recipes

Recipe type `immersive_smithing:smithing`. Recipes are ordinary Minecraft recipes, so they live in
`data/<namespace>/recipes/` (any subfolder) and support Forge `conditions`, for example `forge:mod_loaded`.
They are synced to clients and shown in JEI.

```json
{
  "type": "immersive_smithing:smithing",
  "material": "examplemod:steel",
  "metal_units": 27,
  "auxiliary": [
    { "ingredient": { "item": "minecraft:stick" }, "count": 2 }
  ],
  "result": { "item": "examplemod:steel_pickaxe" },
  "forge_pattern": "immersive_smithing:standard",
  "anvil_pattern": "immersive_smithing:pickaxe"
}
```

| Field | Required | Notes |
|---|---|---|
| `material` | yes | Material family id. A recipe whose family does not exist is deactivated and reported. |
| `metal_units` | yes | Positive integer. |
| `auxiliary` | no | Non-metal ingredients taken from the smith's inventory when forging starts. `count` defaults to 1. With `"consume_equipment": true` the ingredient is a finished piece the recipe reworks (a netherite helmet becoming an ignitium helmet): it is matched by item, whatever its damage or quality, escrowed like any other auxiliary and replaced by the new piece with the quality of the new forging. |
| `result` | yes | Standard item-stack JSON; `nbt` is kept on the finished item. |
| `forge_pattern` | no | Defaults to `immersive_smithing:standard`. |
| `anvil_pattern` | no | Defaults to the closest pattern family for the result item. |
| `replaces` | no | Ids of the native recipes this recipe stands in for. While it is active they are disabled (with `replaceMetalEquipmentRecipes` on), and only they: other recipes for the same result are kept and reported. |
| `derived_from` | no | Ids of native recipes this recipe follows without disabling them (a remake that adds no metal stays beside it). |
| `keep_base_data` | no | Default `true`. When the recipe reworks exactly one finished piece (`consume_equipment`), the result keeps that piece's data, as a smithing-table upgrade does: enchantments, name, lore, wear and other mods' item data. The old quality and maker's mark are dropped because the quench writes new ones, and the `result`'s own `nbt` keys win. Wear is capped so the piece never arrives broken. `false` makes a fresh `result`. |

An explicit recipe always wins over automatic detection for the same result item. Results tagged
`immersive_smithing:non_smithable_equipment` are ignored.

A recipe with `replaces` or `derived_from` is active only while at least one of the recipes it names is loaded. When a
datapack or KubeJS removes the native recipe, the forge recipe that stands in for it goes too, and the report says
why. A recipe naming neither is active whenever its conditions pass.

### Conditions

Besides Forge's conditions, `immersive_smithing:mod_version` is true when a mod is loaded and its version is in a
Maven version range. Use it when another version of a mod reuses the same recipe ids with other ingredients:

```json
{ "type": "immersive_smithing:mod_version", "modid": "medieval_boomsticks", "versions": "[1.2,1.3)" }
```

The finished piece is assembled from what the forge's escrow actually holds, before any metal is charged. If that is
not possible when the minigame ends, the session is cancelled, the molten metal stays in the forge and the components
go back to the smith.

## Material families

`data/<namespace>/immersive_smithing/materials/<name>.json`

```json
{
  "family": "examplemod:steel",
  "display_name": "material.examplemod.steel",
  "sources": [
    { "tag": "forge:ingots/steel", "units": 9 },
    { "tag": "forge:nuggets/steel", "units": 1 },
    { "tag": "forge:storage_blocks/steel", "units": 81 },
    { "item": "examplemod:steel_scrap", "units": 3 }
  ],
  "melt_time_multiplier": 1.0,
  "requires_ignition": true,
  "required_fuel_tag": "immersive_smithing:netherite_fuels",
  "tint": "#FF8A3D",
  "upgrade_policy": "shape"
}
```

| Field | Required | Notes |
|---|---|---|
| `family` | yes | The family id. Several files for the same id are merged in file-id order: sources add up, and the last file sets the other fields. |
| `sources_only` | no | Default `false`. `true`: the file only adds its sources to a family that another file defines; its other fields are ignored, and a family with nothing but `sources_only` files is not created. Epic Knights' steel plate joins `forge:steel` this way, only where steel exists. |
| `display_name` | no | A translation key (the key itself is shown if untranslated) or a text component object. |
| `sources` | yes | Each entry has a `tag` or an `item`, plus positive `units`. |
| `melt_time_multiplier` | no | Multiplies the configured base melt time. Default 1.0. |
| `requires_ignition` | no | Default `true`. When `false`, solid fuel heats the forge without being lit. |
| `required_fuel_tag` | no | The heat source must be in this item tag. Lava counts as `minecraft:lava_bucket`. |
| `tint` | no | Colour of the molten metal, `"#RRGGBB"` or an integer. |
| `upgrade_policy` | no | How upgrades *into* this metal are priced when their base is another piece of equipment. `shape` (default): the base's whole shape in this metal, base not needed (vanilla netherite). `addition`: only the metal the original recipe asked for, and the base piece is consumed as an auxiliary; right for scarce boss metals. |

Files support Forge `conditions`. An item claimed by two families is ignored and reported as ambiguous.

When `autoDetectModdedMetals` is on, every `forge:ingots/<metal>` tag that no definition covers becomes the
family `forge:<metal>`, with nuggets (1), storage blocks (81), raw materials (9) and raw storage blocks (81)
taken from the matching `forge:` tags. Its display name uses the key `material.forge.<metal>`.

## Recycling overrides

`data/<namespace>/immersive_smithing/recycling/<name>.json`

```json
{ "item": "examplemod:steel_greatsword", "family": "examplemod:steel", "units": 45 }
```

```json
{ "tag": "examplemod:relics", "recyclable": false }
```

Use `item` or `tag`. With `"recyclable": false` the items can never be melted down. Items tagged
`immersive_smithing:non_recyclable` are excluded too.

Recycling values are resolved in this order: exclusions, explicit smithing recipes, recycling overrides, analysis
of the item's crafting recipe, then Java `IRecyclingValueProvider`s. The value returned is multiplied by
`recyclingEfficiency` and never exceeds the recognised value.

## Coverage decisions

`data/<namespace>/immersive_smithing/coverage/<name>.json` records how equipment that has no forge recipe is obtained.
It adds and removes nothing: the coverage catalog (`/immersivesmithing catalog <modid>`) and the GameTests read it so
every item of a mod has one stated route.

```json
{
  "conditions": [ { "type": "forge:mod_loaded", "modid": "examplemod" } ],
  "mod": "examplemod",
  "tested_version": "1.4.2",
  "policies": [
    { "items": ["examplemod:crown", "#examplemod:relics"], "disposition": "protected_loot",
      "reason": "boss drop; the mod ships no recipe" },
    { "items": ["examplemod:open_helmet"], "disposition": "native_transformation",
      "reason": "the open visor of examplemod:closed_helmet", "state_of": "examplemod:closed_helmet" }
  ]
}
```

| Field | Notes |
|---|---|
| `mod`, `tested_version` | Optional. The catalog shows the version the decisions were checked against. |
| `items` | Item ids or `#tags`. An item entry wins over a tag entry; otherwise the first match in file-id order. |
| `disposition` | `native_nonmetal_recipe`, `native_transformation`, `native_assembly`, `protected_loot`, `non_equipment` or `disabled_by_upstream` (the catalog derives `forge_default`, `forge_auto`, `material_unavailable`, `integration_disabled` and `missing_support` itself). [epic-integrations.md](compatibility/epic-integrations.md) defines each. |
| `reason` | Shown in the catalog. |
| `state_of` | The item is another state of that piece (a visor swapped open or closed). It gets the same recycling value. |

## Anvil patterns

`data/<namespace>/immersive_smithing/anvil_patterns/<name>.json`. The pattern id is the file id.

```json
{
  "targets": [[0.30, 0.70], [0.46, 0.54], [0.62, 0.38], [0.78, 0.22]],
  "strikes": 10,
  "target_radius": 0.075,
  "target_lifetime": 1.8,
  "ideal_fraction": 0.6,
  "gap": 0.25,
  "jitter": 0.03,
  "time_multiplier": 1.0,
  "categories": ["minecraft:swords"]
}
```

| Field | Default | Notes |
|---|---|---|
| `targets` | required | 1 to 64 points `[x, y]`, normalised 0..1 over the workpiece (y points down). Visited in order and repeated when `strikes` is larger. |
| `strikes` | number of targets | Strikes needed to finish (1 to 64). |
| `target_radius` | 0.075 | Radius as a fraction of the work area. |
| `target_lifetime` | 1.8 | Seconds a target stays before it expires and comes back. |
| `ideal_fraction` | 0.6 | When in the lifetime a strike is perfectly timed. |
| `gap` | 0.25 | Seconds between a hit and the next target. |
| `jitter` | 0.03 | Random offset applied to each target from the session seed. |
| `time_multiplier` | 1.0 | Multiplies the time granted by the hammer tier. |
| `categories` | none | Item tags; items in them use this pattern before class-based classification. |

Built-in patterns: `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `generic_tool`, `helmet`, `chestplate`, `leggings`,
`boots`, `shield`, `generic_weapon`, `generic_armor`, `generic_equipment` (all in the `immersive_smithing` namespace).
A missing pattern falls back to `immersive_smithing:generic_equipment`.

The Spartan Weaponry patterns are named after its weapon types (`immersive_smithing:longsword`, `immersive_smithing:javelin`,
`immersive_smithing:heavy_crossbow`, ... plus `club` and `cestus`). Their `categories` are Spartan's per-type item tags
(`spartanweaponry:longswords`, ...), so add-ons that tag their weapons the same way get the matching pattern.

Epic Fight and Simply Swords weapons use the same weapon-type patterns. Three more patterns, `twinblade`, `warglaive`
and `chakram`, cover the Simply Swords shapes that have no Spartan counterpart. Each weapon-type pattern also lists an
`immersive_smithing:<type>` item tag in its `categories`. There are 17 such tags: `longswords`, `greatswords`, `katanas`,
`sabers`, `rapiers`, `daggers`, `parrying_daggers`, `spears`, `glaives`, `halberds`, `scythes`, `battleaxes`,
`battle_hammers`, `cestusae`, `twinblades`, `warglaives` and `chakrams`. They hold the Epic Fight and Simply Swords
weapons as optional entries, generated by `tools/generate_weapon_compat.py`. Another mod or a datapack can add its own
weapons to them:

- An Epic Fight tachi or uchigatana is a `katanas` weapon.
- A Simply Swords cutlass is a `sabers` weapon, and a sai is a `parrying_daggers` weapon.
- A claymore is a `greatswords` weapon, a greataxe a `battleaxes` weapon and a greathammer a `battle_hammers` weapon.

## Forge patterns

`data/<namespace>/immersive_smithing/forge_patterns/<name>.json`

```json
{
  "base_phases": 3,
  "units_per_extra_phase": 24,
  "min_phases": 3,
  "max_phases": 7,
  "zone_half_width": 0.085,
  "min_speed": 0.45,
  "max_speed": 0.8,
  "acceleration": 0.35,
  "transition_ms": 450
}
```

Phases = `base_phases + metal_units / units_per_extra_phase`, clamped to `min_phases..max_phases`. Speeds are
track lengths per second; the marker speeds up by `acceleration` per second while the smith waits.

## Tags

| Tag | Meaning |
|---|---|
| `immersive_smithing:smithable_equipment` | Always a candidate for automatic detection. |
| `immersive_smithing:non_smithable_equipment` | Never detected, disabled or given a smithing recipe (explicit recipes included). |
| `immersive_smithing:smithable_shields` | Shields that may be detected even if not predominantly metal. |
| `immersive_smithing:non_smithable_shields` | Shields never redirected (contains `minecraft:shield`). |
| `immersive_smithing:non_recyclable` | Never melted down. |
| `immersive_smithing:forge_fuels` | Solid fuels the forge accepts (with a furnace burn time), plus `minecraft:lava_bucket`. |
| `immersive_smithing:forge_igniters` | Items that ignite the forge (flint and steel, fire charge). |
| `immersive_smithing:netherite_fuels` | Heat sources for netherite (lava only). |
| `immersive_smithing:tongs`, `immersive_smithing:hammers` | The smithing tools. |
| `immersive_smithing:quality_eligible` | Equipment the forge never makes that may still carry quality: it is graded as loot and can be refined at the grindstone (Simply Swords' runic and unique weapons, Epic Knights' gilded pieces and relics). Never adds a recipe. Entries of a mod whose integration is switched off are ignored. |
| `immersive_smithing:compat/<integration>/quality_eligible`, `.../smithable_equipment`, `.../non_recyclable` | Generated entries of the built-in integrations (`epicknights`), included in the tags above. |
| `immersive_smithing:<weapon type>` | The 17 weapon-type tags of the anvil patterns (`longswords`, `katanas`, ...); see [Anvil patterns](#anvil-patterns). |
| `immersive_smithing:weapon_scaled_projectiles` (entity types) | Thrown weapons whose damage already comes from the thrown stack's quality-adjusted attack damage, so efficacy is not applied a second time (Simply Swords' thrown swords and spears, including those its runic powers and unique weapons throw). |

## Automatic detection

With `autoDetectEquipment` on, each crafting recipe whose result is a single unstackable, damageable weapon, tool,
armor piece or metal shield is analysed. It becomes an automatic smithing recipe
(`immersive_smithing:auto/<namespace>/<item>`) only if:

- it is exact vanilla shaped or shapeless crafting, or a crafting recipe known to hand the reworked piece's data to its
  result (`botania:armor_upgrade`),
- every ingredient is entirely one metal family or entirely non-metal,
- one metal family is the main one. When ingredients include the result's repair material, that material's family is
  the main one: a gold weapon with iron-nugget fittings is gold equipment, and the nuggets are components. Fittings of
  another metal must come to fewer units than the main metal; otherwise the recipe is a true alloy and is skipped.
  When the repair material is not metal (diamond, leather, bone), the recipe is skipped as "not predominantly metal",
  so a diamond weapon with iron fittings is never sold as iron,
- no ingredient is itself damageable equipment, apart from one finished piece (see below),
- the result is not blacklisted (tag, recipe id, mod id, family),
- no explicit recipe exists for the result.

Recipes of any other class or serializer are never converted or disabled; the report lists them as kept. That
includes a subclass of a vanilla recipe and Simply Swords' `smithing_reroll`.

Detection reads the recipes as the last datapack load produced them, not the manager it has already rewritten. Every
rebuild (at server start, and after `/reload`, including the `/reload` that applies a config change) therefore finds
the same sources and gives the same result, and no original recipe is lost.

**Native recipes.** With `replaceMetalEquipmentRecipes` on, a native crafting or smithing-table recipe is disabled only
when a forge recipe replaces that very recipe. That forge recipe is either generated from it or makes the same item
from the same metal (or, for an upgrade, from the addition's metal). Every other native recipe is kept, and
`/immersivesmithing report` lists it with the reason.

An ingredient that is itself a shield is dismissed when the result is a shield: a metal shield built on a wooden base
shield costs only its metal, and the base is not required. Everything else is skipped and listed by
`/immersivesmithing report`. Bows, crossbows, fishing rods and tridents are not candidates unless tagged
`immersive_smithing:smithable_equipment`.

Smithing-table upgrades (`minecraft:smithing_transform`) whose result is candidate equipment without a smithing
recipe are redirected too, when the addition is a single metal family (for example netherite from any mod). The
template is never required. Under the addition metal's `shape` policy the cost is the base item's shape in that
metal: the base's own smithing recipe if it has one (units scaled by the addition's unit value), otherwise its
crafting recipe, where ingredients the base accepts as repair material (or `forge:gems`/`forge:ingots`) are the
material slots, other components stay auxiliary and a base shield is dismissed. A finished piece in that recipe counts
with its own shape when that shape is gems or ingots: Epic Fight's diamond longsword is a diamond sword and two
diamonds, so its netherite upgrade costs four ingots. A leather or cloth piece leaves the base without a metal shape.
Iron's Spells' wandering magician boots, for example, are leather boots and arcane essence. Under the `addition` policy, or
when the base has no metal shape of its own (a loot weapon, a cloth robe), the cost is the addition itself and the
base piece is consumed. The recipe id is `immersive_smithing:auto/<namespace>/<item>`.

**Finished pieces in crafting.** A crafting recipe with exactly one finished piece among its ingredients is handled
by what it does to that piece:

- **Growth.** When the forge makes the piece from the same metal the recipe adds, the result grows from the piece. Its
  forge recipe costs the piece's own recipe plus the added metal and components. Epic Fight's iron longsword is an iron
  sword and two iron ingots: 36 units and a stick. Growth stays within one kind of equipment (one armour slot,
  shields, mining tools or weapons). A recipe that would turn a pickaxe into boots is skipped and reported.
- **Upgrade chain.** When the piece is reworked with another metal, the recipe is priced like a smithing-table upgrade
  (Botania's terrasteel armour from manasteel armour), and its other components stay auxiliary. The piece must be of
  the result's class (same anvil pattern and armour slot) and something the forge already makes; otherwise the recipe
  is skipped and reported.
- **Remake.** A recipe that adds no metal only reshapes a finished piece, as when Epic Fight makes a spear from an iron
  sword and two sticks. The forge gets a recipe that makes the same result from the piece's own metal plus every
  component (18 iron units and three sticks for that spear). The native recipe is kept next to it, so an existing piece
  can still be remade, and the result inherits the piece's quality. A recipe whose piece has no metal at all (a bone
  sword given dragon blood) is not the forge's business and is not reported.

**Predominance.** A recipe with fewer metal slots than other components and less than one ingot of metal (a helmet
with a single gold nugget) is skipped as "not predominantly metal" unless the result is tagged
`immersive_smithing:smithable_equipment`.

## Built-in mod support

Recipes for other mods live under `data/immersive_smithing/recipes/compat/<modid>/` and start with a
`forge:mod_loaded` condition, so they are inert without that mod. Material definitions for other mods live under
`immersive_smithing/materials/compat/<modid>/` with the same condition and give the metal a name, tint, melt rule and
upgrade policy:

| Mod | Families | Notes |
|---|---|---|
| Cataclysm | `cataclysm:black_steel`, `cataclysm:ancient_metal`, `cataclysm:witherite`, `cataclysm:cursium`, `cataclysm:ignitium` | Cataclysm ships no `forge:ingots` tags, so these are listed item by item. The three boss metals need lava and use the `addition` policy: an ignitium helmet costs one ingot plus the netherite helmet it reworks. Ignitium and cursium armour is unbreakable, so it is tagged `smithable_equipment` to count as equipment at all. |
| Ice and Fire | `forge:fire_dragonsteel`, `forge:ice_dragonsteel`, `forge:lightning_dragonsteel` | Lava only. The ids match the tags Spartan Fire adds, so recipes are the same with or without it. |
| Spartan Fire | recipes under `compat/spartanfire/` | Every dragonsteel weapon of all 24 Spartan types, generated by `tools/generate_spartan_compat.py --spartanfire <jar>`; follows the `integrations.spartanWeaponry` toggle. |
| Iron's Spells 'n Spellbooks | `forge:mithril`, `forge:pyrium` | Pyrium needs lava and uses `addition`, so the hellrazor and legionnaire flamberge rework their loot base. The cloth wizard sets are tagged `non_smithable_equipment`; the netherite mage set is forged. |
| Botania | `forge:manasteel`, `forge:terrasteel`, `forge:elementium` | Terrasteel uses `addition`: terrasteel armour costs its three ingots plus the manasteel piece, as in Botania. |
| Minecraft Comes Alive | `forge:rose_gold` | Name and tint only. |

Fire Sticks, Lit It Up and Hardcore Torches fire starters ignite the forge (`forge_igniters`), and `#forge:coal_coke`
is a forge fuel. Ars Nouveau robes, Ice and Fire sheep armour and elytra-like items are tagged non-smithable.

Spartan Weaponry: every metal weapon of all 24 types (melee, throwing weapons, longbows, heavy crossbows) in copper,
gold, iron and netherite, and in tin, bronze, steel, silver, electrum, lead, nickel, invar, constantan, platinum and
aluminum when another mod supplies those ingots. Costs and components mirror Spartan's own recipes; netherite
weapons use the diamond shape in netherite. The studded club and cestus cost 9 iron units and no base weapon.
Spartan's `type_disabled` config conditions are honoured. Arrows and bolts are not smithed. The server config option
`integrations.spartanWeaponry` (default `true`) turns these recipes off; Spartan Weaponry is then handled by automatic
detection like any other mod.

Quality reaches projectiles: a thrown Spartan Weaponry weapon's base damage is multiplied by its efficacy, and any
arrow or bolt by the efficacy of the bow or crossbow that fired it.

### Epic Fight, Epic Knights and Simply Swords

All are optional and work alone or together. Immersive Smithing's code never references them (only their namespaces):
support is data, so the jar runs unchanged without them. The server options `integrations.epicFight`,
`integrations.epicKnights` (with `integrations.epicKnightsDisabledAddons` for single add-ons) and
`integrations.simplySwords` (default `true`) switch each one off. The mod's recipes are then left exactly as it ships
them, nothing of it is forged, and its `quality_eligible` entries are ignored. Pieces that already carry quality keep
it.

- **Epic Fight and Epic Knights are curated.** Their forge recipes are bundled under `recipes/compat/<modid>/`,
  generated from the pinned jars by `tools/generate_epic_compat.py`, and each one names the native recipes it
  `replaces` or is `derived_from`. Automatic detection never runs on their items, so a piece the bundled data does not
  cover keeps its native recipe and shows as `missing_support` in the catalog. Everything else (wooden and diamond
  pieces, gilding and other transformations, visor states, firearms, relics) has a coverage decision. Epic Fight's
  uchigatana gets a forge recipe and the bokken a crafting recipe, because Epic Fight ships neither. Costs, versions and
  upstream issues: [compatibility/epic-integrations.md](compatibility/epic-integrations.md).
- **Simply Swords** is found by automatic detection: metal weapons are forged on their weapon-type patterns; runic
  upgrades, rerolls and unique weapons keep their native recipes or stay loot.
- **Quality.** Quality lives in the item's attribute modifiers, so it applies exactly once:
  - in Epic Fight's vanilla and battle modes, in the main hand and the off hand (Epic Fight swaps the hands' modifiers
    for an off-hand strike), Epic Knights weapons included;
  - to Simply Swords' attack-scaled abilities, which read the wielder's attack damage;
  - to Simply Swords' thrown weapons, which are tagged `weapon_scaled_projectiles`.
- **Loot.** Pieces the forge does not make but which may carry quality are tagged `quality_eligible`: Simply Swords'
  runic weapons and uniques, and Epic Knights' metal transformations, visor states and relics.
- **Recycling.** Runic weapons recycle for the netherite weapon they were made from. A transformed Epic Knights piece
  (gilded, darkened) recycles like its base piece, and a visor state like its helmet (coverage `state_of`). Epic Knights'
  metal plates melt for one ingot. Simply Swords' uniques and the Seven Kingdoms' relics are tagged `non_recyclable`.
  To let a pack melt them, replace that tag in a datapack (`"replace": true`) with only the entries the pack still
  wants, and give each piece a [recycling override](#recycling-overrides) with its metal content.
- **Inventory.** [`compatibility/INVENTORY.md`](compatibility/INVENTORY.md) and the fixtures under
  `compatibility/inventory/<profile>/` list every piece of equipment of the tested versions: its route, forge recipe,
  cost, pattern and components, quality and recycling. The coverage GameTests compare the live game with them, and
  `/immersivesmithing catalog <modid>` writes the same audit for the running game.

When `replaceMetalEquipmentRecipes` is on and `allowVanillaCraftingAlongsideSmithing` is off, crafting recipes
that produce a smithable item from recognised metal are removed at reload, as are smithing-table upgrade recipes
(`suppressSmithingTableUpgrades`). Armor trim recipes are never touched. Changing these options needs a `/reload`.

## Loot quality

A global loot modifier (`immersive_smithing:forged_loot`) grades equipment that comes out of loot tables (chests,
mob loot tables, treasure bags) when it carries no quality yet and the forge has a smithing recipe for it or it is
tagged `immersive_smithing:quality_eligible`. Server
config `lootQuality.mode`: `OFF`, `STANDARD` (default: both scores between 35 and 69, so Fine and Masterwork are
always someone's work) or `RANDOM` (the villager distribution). `lootQuality.excludedLootTables` lists table ids or
whole namespaces that stay ungraded. Equipment a mob drops from its own hands is not loot-table output and stays
ungraded.

## Quality inheritance

When a crafting recipe consumes exactly one graded piece and produces other equipment without a quality of its own
(dyeing, an elemental upgrade of a dragonbone sword, a sword made into a spear), the result keeps the quality, the
maker's mark, the title and the inscription (`inheritQualityOnCraft`, default on). The crafting grid's preview already
shows the inherited quality, so the piece you see is the piece you take. Smithing-table upgrades and rerolls carry the
piece's whole data natively and do not depend on this option.

## Grindstone

The Smith's Grindstone refines a graded piece. A piece without a grade starts from Standard (both scores 50) if the
forge makes it or it is tagged `quality_eligible`. That starting grade is written only together with a paid
refinement, so looking at the price changes nothing.

Jade shows the server's per-player refinement quote, including cost modifiers. Quotes refresh while looking at the
station and are matched to the dimension, station and held stack; a pending or stale quote never falls back to the
unmodified price. The quote packet requires network protocol 3 on both sides.

## Item data

Finished items keep quality and the maker's mark in the `immersive_smithing` compound of the stack's tag:

```
immersive_smithing: { Version: 1, Forged: 1b, ForgeScore: 94, AnvilScore: 82, Faulty: 0b, Quality: "fine", DurabilityCarry: 0.4d,
                      SmithName: "Otectus", SmithUUID: [I; ...], Signed: 1b }
```

Newly quenched pieces also store `RecyclingFamily` and `RecyclingUnits` in this compound: the material and units
actually charged by the forge. Recycling takes the smaller of this amount and the normal recipe/provider value,
then applies recycling efficiency. Old pieces without these fields keep their existing recycling value because
their original paid cost is unknown. A mismatched recorded material cannot be recycled as a different metal.
Crafting repairs and remakes preserve the known input metal even with quality inheritance disabled; native
smithing-table recipes copy the fields with the base's tag. Reforging writes a new paid amount at the quench.

`SmithName` and `SmithUUID` are written at the quench and never change. A signed piece stores its title as the
vanilla `display.Name` (non-italic, in the quality colour) and its inscription as `display.Lore`, so every tooltip
mod shows them. Only the smith named in the mark can sign, from the screen that opens after the quench or by
sneak-using the Smith's Anvil with the finished piece; Faulty work is never signed. The server sanitises titles and
inscriptions (formatting codes and control characters are stripped, lengths capped by the `signing` config).

A stack without this compound behaves exactly like Standard quality. Tongs and Hot Workpiece items carry
`immersive_smithing.HeldWorkpiece` with `Version`, `TargetStack`, `RecipeId`, `MaterialFamily`, `MetalUnits`,
`ForgeScore`, `AnvilScore`, `Faulty`, `State` (`FORGED` or `SHAPED`) and `AnvilPattern`.

## Events

`com.otectus.immersivesmithing.api.event.ItemSmithedEvent` is posted on the Forge bus (server side, not cancellable)
when a smith quenches a piece, after quality and the maker's mark are written and before the item reaches the
inventory. It carries the smith, the live result stack (changes stay on the item), the `QualityData`, the smithing
recipe id, the material family, the units consumed and the trough position. KubeJS scripts can subscribe with
`ForgeEvents.onEvent('com.otectus.immersivesmithing.api.event.ItemSmithedEvent', ...)`. With
`integrations.fireVanillaCraftEvent` (default on) the vanilla `PlayerEvent.ItemCraftedEvent` is fired too, with a
one-slot container, so mods that reward crafting see forged items.

## Java API

`com.otectus.immersivesmithing.api.ImmersiveSmithingAPI`. Register during `FMLCommonSetupEvent`, or send an IMC message to
`immersive_smithing` whose payload is a `Supplier` of the interface.

| Interface | IMC method | Purpose |
|---|---|---|
| `ISmithingMaterialProvider` | `material_provider` | Adds material definitions, resolved like datapack files. |
| `ISmithingRecipeProvider` | `recipe_provider` | Adds smithing recipes; they rank with explicit recipes. |
| `ISmithingEfficacyHandler` | `efficacy_handler` | Replaces the default attribute and mining-speed changes for items it claims. |
| `IShieldSmithingHandler` | `shield_handler` | Applies efficacy to shields (neutral by default). |
| `IEquipmentClassifier` | `equipment_classifier` | Chooses the anvil pattern for an item. |
| `IRecyclingValueProvider` | `recycling_provider` | Supplies metal content when nothing else does. |
| `Predicate<ItemStack>` | `exclusion` | Excludes items from detection, suppression, recycling and quality. |
| `IMeltVeto` | `melt_veto` | Refuses an item at the Smith's Forge (hand and automation) with a player-facing reason, for items whose other data melting would destroy. |
| `ISmithingRecipeGate` | `recipe_gate` | 1.1.0. Refuses a recipe to a smith with a reason: the Forge screen leaves it out (and shows the first reason when nothing is left), and the selection is refused again before any metal or auxiliary is taken. For progression mods that gate the making of gear. |
| `ISmithingCostModifier` | `cost_modifier` | 1.1.0. Adjusts the metal units the forge charges (never below one, never above the recipe's own; the minigame keeps the recipe's shape) and the experience levels a Smith's Grindstone refinement costs (never below zero). |
| `ISmithingAssistSource` | `assist_source` | 1.1.0. Per-player time allowance at the forge and anvil (`SmithingStation`). Answers multiply, clamped to `1.0..minigames.maxAssistTimeMultiplier`; the client receives the final allowance in the start packet, so no protocol change. |

```java
InterModComms.sendTo("immersive_smithing", ImmersiveSmithingAPI.IMC_CLASSIFIER,
        () -> (IEquipmentClassifier) stack -> stack.is(MY_GLAIVES) ? new ResourceLocation("immersive_smithing", "generic_weapon") : null);
```

## Commands

Operators (permission level 2):

- `/immersivesmithing reload` - full datapack reload.
- `/immersivesmithing material <item>` - family, units and the rule that matched, or the recycling value.
- `/immersivesmithing recipe <item>` - smithing recipes for the item and the conventional recipes they disabled.
- `/immersivesmithing quality` - quality and multipliers of the held item.
- `/immersivesmithing report` - writes `logs/immersive_smithing_report.txt` with every detection decision.
- `/immersivesmithing session` - active minigame sessions.
- `/immersivesmithing catalog <modid>` - writes `logs/immersive_smithing_catalog_<modid>.json`, covering every piece
  of equipment the mod registers: its anvil shape, its forge recipes, what happened to each native recipe, whether it
  carries quality and what it recycles for.
