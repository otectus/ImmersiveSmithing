# Epic Fight and Epic Knights integrations

Immersive Smithing ships the recipes, coverage decisions and tags for Epic Fight, Epic Knights and the Epic Knights
add-ons inside its own jar. They are on by default and need no datapack, no recipe JSON from the player and no generator
run. Every file is conditional on its mod being loaded, so the same jar works with any subset of these mods or none.

Server operators can switch the integrations in the server config (`[integrations]`, `/reload` applies a change):

| Option | Default | Effect |
| --- | --- | --- |
| `epicFight` | `true` | Epic Fight's metal weapons are forged with the built-in recipes (the uchigatana included), the crafting and smithing-table recipes they replace are disabled, and the bokken gets a crafting recipe. When off, Epic Fight is left exactly as it ships. |
| `epicKnights` | `true` | The same for Epic Knights and every add-on below. When off, all of them are left exactly as they ship. |
| `epicKnightsDisabledAddons` | `[]` | Mod ids (for example `"slavicarmory"`) left exactly as they ship while the others stay integrated. |
| `terraFirmaCraft` | `false` | TerraFirmaCraft and Epic Knights of TerraFirma keep their own metalworking. `true` treats them like any other mod (automatic detection); no TerraFirmaCraft data is bundled. |

Pieces that were already graded keep their quality when an integration is switched off.

## Tested versions

The bundled data was generated from these jars by `tools/generate_epic_compat.py`. `tools/epic_compat.lock.json`
records each hash; the generator's `--check` fails when one changes. The Gradle cache holds them after any
`-PepicFight` or `-PepicKnights` task (see "Test profiles").

| Mod (id) | Version | Source | SHA-256 |
| --- | --- | --- | --- |
| Epic Fight (`epicfight`) | 20.14.17 | Modrinth `KEBfkBat` | `69566cf70ae2d91d3f2564c608f014c87e290cef6215c2a27719851165485f73` |
| Epic Knights: Shields, Armor and Weapons (`magistuarmory`) | 10.15 | Modrinth `gJ59URg4` | `5923859b45f6f0f8236f09980f1ead9b48ae9824872a429153d928fd10d5835d` |
| Epic Knights: Addon (`magistuarmoryaddon`) | 2.6 | Modrinth `xnYQSIH7` | `cbaf743ce72199d2007fc9d9199be28d2f805a81a837230cf0d6e8ce828cd193` |
| Epic Knights: Slavic Armory (`slavicarmory`) | 2.1 | Modrinth `Q5VtlZ47` | `f22f6f92475271f515b226f4496fffddacdd712a31f195413afa4b925a35c22d` |
| Epic Knights: Antique Legacy (`antiquelegacy`) | 1.13 | Modrinth `4DhbZFhg` | `06bf57a754d93816382fd60a4b2030a84854518918a0553b3f7b38aae33f5def` |
| Epic Knights of the Seven Kingdoms (`ekotsk`) | 1.20.1_1.5.0 | Modrinth `eveDj5L8` | `21d9d8c6571aaabd6c35ef277449f4c62fe1a8e1737980639150af43f0bfc187` |
| Epic Knights: Ice and Fire (`epic_knights_ice_and_fire`) | 1.0 | CurseForge 1151603 / 5972466 | `2c1c89c8c4d7bb077c5f235e763471e0b4204ffd59aafb950c33e37c90c87c80` |
| Epic Knights: Ores and Alloys (`epic_knights_ores_and_alloys`) | 1.1 | CurseForge 1125802 / 5857444 | `8f493e0f82a6476b97803bed49f518d0486ae4ef367b2f24012e78bcd4160ae7` |
| Epic Knights: Dark Ages (`darkagesarmory`) | 1.1 | CurseForge 1361923 / 7107406 | `227279de86f19e44be7a05f4ea187dd3b083d17e321179d3b8da224ed545c6ae` |
| Epic Knights: Open Helms (`epicknights_open_helms`) | 1.3 | CurseForge 1176829 / 7493041 | `52af6a987c7870ac252abf321a4955a40b50f64d2207fda42c76e11175bee6ca` |
| Swappable Helms for Epic Knights (`quick_swap`) | 0.1.0 | CurseForge 1368717 / 7128444 | `44bc365d8cf928e800aebdfa1c4d597c190da9024f083a56ce2c08bcf9d4bd49` |
| EK: Artilleries and Firearms (`artillery_addon`) | 1.16.2 | CurseForge 1307540 / 8812944 | `9cde7b826555b72c5f9ea90b5a89e941a68c16fab47d58850dffb559f7e61cb2` |
| Medieval Boomsticks (`medieval_boomsticks`) | 1.2 | CurseForge 1123016 / 6148892 | `732939d7efb9419ac6a7c88d76e78c6359021a42edfe8bb0309c148f06b510b6` |
| FoxRall's Visors | - | CurseForge 1365951 / 8171854 | client-only visuals, no items (not a generator input) |
| Ice and Fire (`iceandfire`, support only) | 2.1.13-1.20.1 | Modrinth `EzN8KQYF` | `2b80245fc9b7d6fdc61d71f9892f4c6114eb7f303f65634845aaab25f84d1e82` |

Minecraft 1.20.1's and Forge 47.4.23's own data jars are inputs too (vanilla tags and recipes), with their hashes in the
lock file.

Other versions of these mods load the same data safely. Every built-in forge recipe names the native recipes it replaces
or follows and is active only while one of them is loaded, so a version that renames or drops a recipe keeps its own
route instead of losing the item. Items added by a newer version are never guessed by automatic detection: they keep
their native recipes until the data is regenerated. Medieval Boomsticks' forge recipes also carry an
`immersive_smithing:mod_version` condition (`[1.2,1.3)`), because other versions use the same recipe ids with other
ingredients.

## How equipment is obtained

Each equipment item of these mods has exactly one disposition. The coverage catalog (`/immersivesmithing catalog
<modid>`, and the GameTests below) reports it for every item in the live registry:

| Disposition | Meaning |
| --- | --- |
| `forge_default` | Forged with a bundled recipe; the native recipes it names are disabled. |
| `forge_auto` | Forged with an automatically detected recipe (mods without a curated integration, such as Simply Swords). |
| `native_nonmetal_recipe` | Not metal work (wood, leather, cloth, stone, diamond): crafted as the mod ships it. The bokken's crafting recipe is bundled because Epic Fight ships none. |
| `native_transformation` | Made from another piece: gilding, darkening, fluting, whitening and tinning, and visor states. The route stays the mod's own; the result keeps the base piece's quality and recycling value. |
| `native_assembly` | Firearms and machines assembled from mechanisms (locks, triggers, gunsmith kits), or Medieval Boomsticks pieces from its own smithing furnace: those systems stay with their mod. |
| `protected_loot` | Unique or loot-only pieces (Seven Kingdoms relics and Valyrian steel, crowns, weathered and rusted finds): never forged; relics can't be melted down. |
| `non_equipment` | Damageable but not equipment the forge works: powder flasks, ramrods, spanners, the Boomsticks smithing tools, Epic Fight's stray cloth armour. |
| `disabled_by_upstream` | The mod registers the item but ships no working route (see "Upstream issues"). |
| `material_unavailable` | The piece's metal has no source in this pack (for example bronze, tin or silver without a mod that adds the ingot): its native recipe stays. |
| `integration_disabled` | Its integration is switched off in the config. |
| `missing_support` | An eligible piece with none of the above. Every tested profile has zero. |

### Coverage of the tested profiles

The largest compatible set (profile `full`: every content add-on except Epic Knights: Ice and Fire, which cannot load
beside the Addon; see "Upstream issues"):

| Mod | Items | forge_default | nonmetal | transformation | assembly | protected | non_equipment | upstream |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Epic Fight | 36 | 17 | 16 | - | - | - | 3 | - |
| Epic Knights | 400 | 279 | 96 | 8 | - | 17 | - | - |
| Addon | 349 | 144 | 60 | 138 | - | 5 | - | 2 |
| Slavic Armory | 59 | 34 | 7 | 18 | - | - | - | - |
| Antique Legacy | 109 | 81 | 19 | 4 | - | 5 | - | - |
| Seven Kingdoms | 75 | 36 | 5 | 11 | - | 23 | - | - |
| Ores and Alloys | 25 | 25 | - | - | - | - | - | - |
| Dark Ages | 126 | 59 | 63 | 4 | - | - | - | - |
| Open Helms | 58 | 1 | - | 53 | - | - | - | 4 |
| Artilleries and Firearms | 37 | 4 | 1 | - | 17 | 5 | 10 | - |
| Medieval Boomsticks | 14 | 4 | - | - | 7 | - | 3 | - |

Other profiles: Epic Knights alone has 192 forge_default and 87 material_unavailable pieces (its bronze, tin and silver
pieces need another mod's ingots); with Epic Knights: Ice and Fire, Epic Knights has 221 forged and 58 unavailable, and
the add-on's 220 items are 60 forged, 60 nonmetal and 100 transformations (its dragonsteel pieces use Ice and Fire's
dragonsteel). The per-item tables, with costs, patterns, quality and recycling values, are in
[INVENTORY.md](INVENTORY.md); the fixtures they are generated from are in [inventory/](inventory/).

## Costs

Costs are in units: a nugget is 1, an ingot 9, a block 81.

- A recipe's metal is counted once. Intermediates of the same metal are flattened into it: a plate is 9 (two ingots make
  two plates), a ring ½ (four nuggets make eight), a chain link three rings, chainmail nine rings, a small plate ¾.
  An armet is a barbute and three plates, so it costs 72 steel. Fractions are rounded down, never up, so forging never
  costs more than crafting did.
- The piece's repair material decides its metal; without one, the recipe's metal does. Fittings of another metal (a
  steel boss on an iron shield, bronze nuggets on an iron sword) and every non-metal part (hilts, poles, leather strips,
  fabric, planks) are components, taken whole from the inventory and never refunded.
- A wooden base shield in a metal shield's recipe is a component: an iron kite shield is 36 iron and a wooden kite shield.
- Netherite pieces are forged from netherite in the diamond piece's whole shape, without template or base piece.
- Plates melt down for one ingot of their metal (Epic Knights' steel plate, Antique Legacy's iron and bronze plates, the
  Seven Kingdoms' blacked and gilded steel plates). Rings are never melted.

Epic Fight:

| Weapon | Cost | Pattern |
| --- | --- | --- |
| dagger | 9 + stick | dagger |
| longsword | 36 + stick | longsword |
| greatsword | 54 + plank | greatsword |
| spear | 18 + 3 sticks | spear |
| tachi | 36 + stick | katana |
| uchigatana | 36 iron + diamond, 2 leather, stick | katana |
| glove | 2 iron (units) + 2 leather | cestus |
| bokken | crafted: two planks diagonally and a stick | - |

Iron, gold and netherite weapons have these costs. The diamond and wooden weapons keep their recipes. Epic Fight's iron
and golden spear are also made from a sword and sticks; that recipe adds no metal and stays beside the forge recipe.

Epic Knights examples (the GameTests check these):

| Piece | Cost |
| --- | --- |
| armet | 72 steel |
| chainmail helmet | 22 steel |
| lamellar chestplate | 36 steel + 24 leather strips |
| iron bastard sword | 27 iron + hilt |
| netherite shortsword | 18 netherite + hilt |
| iron kite shield | 36 iron + wooden kite shield |
| iron buckler | 36 iron + steel ingot |
| heavy crossbow | 27 steel + 2 string, tripwire hook, pole |
| iron chain morning star | 27 iron + steel chain, stick, hilt (flail pattern) |

## Precedence, suppression and reload

- A bundled forge recipe disables only the native recipes it names in `replaces`. Other recipes for the same item are
  kept and reported: gilding and other transformations, visor swaps, remakes that add no metal, and smithing-table
  upgrades when `suppressSmithingTableUpgrades` is false. `derived_from` names a recipe the forge recipe follows
  without disabling it (Epic Fight's spears).
- A bundled recipe is active only while at least one native recipe it names is loaded. A pack that removes a native
  recipe (a datapack or KubeJS) removes the forge route with it, and the catalog says so.
- Datapacks win: a recipe at the same id replaces the bundled one, and a pack can remove one with a false condition.
  Coverage decisions are data too (`data/<ns>/immersive_smithing/coverage/*.json`; format in [API.md](../API.md)).
- Automatic detection never runs on Epic Fight or Epic Knights items. The bundled recipes work with
  `autoDetectEquipment = false`; an item they do not cover keeps its native recipe and shows as `missing_support`.
- Switching an integration off or on takes effect on `/reload` and on the next server start; the recipe set the
  clients receive is rebuilt both times.

## Quality

- Forged pieces carry quality like any other: durability from the Forge score, efficacy from the Anvil score.
- Quality counts once, in either hand and in Epic Fight's battle mode. Epic Knights weapons that can block still count
  as weapons (their attack damage gets efficacy); a shield is a piece that blocks without dealing damage.
- Gilded, darkened and other transformed pieces keep the base piece's quality and recycling value. A helmet's open and
  closed visor states share one value, and switching the visor with Swappable Helms keeps its quality.
- Relics and other protected loot can carry quality as found loot (the loot quality mode decides); relics can't be
  melted down.

## Upstream issues and blockers

None of these is patched here: Immersive Smithing does not modify other mods' files or redistribute their jars.

- **TerraFirmaCraft and Epic Knights of TerraFirma** have their own metalworking (anvils, welding, heating). They are not
  integrated and no data is bundled; by default Immersive Smithing does not touch them. A server can opt in with
  `terraFirmaCraft = true`, which applies automatic detection like any other mod. A curated integration needs its own
  audit.
- **Epic Knights: Addon 2.6 and Epic Knights: Ice and Fire 1.0** both name their AutoConfig `epicknightsaddon`. Each
  overwrites the other's `config/epicknightsaddon/*.json5`, and the next launch with both fails with a
  NullPointerException in AutoConfig. Use one or the other. Both are tested, in separate profiles.
- **Addon darkblued armet and sallet**: their darkening recipes use the `minecraft:smithing` recipe type, which 1.20.1
  removed, so the add-on ships no working route (`disabled_by_upstream`).
- **Open Helms**: four visor states (`gilded_exquiste_maximilian_helmet`, `open_blue_armet_helmet`,
  `open_patrician_helmet`, `openonepiecesallet_helmet`) are not in Swappable Helms' default pairs, so they can't be
  reached (`disabled_by_upstream`). Adding the pairs to Swappable Helms' configuration makes them reachable; their
  recycling then follows the closed helmet.
- **Swappable Helms 0.1.0** trusts its client. The swap packet carries a player entity id and a target item id, and the
  server puts a new stack of that item, with the old helmet's NBT, into that entity's head slot. It checks neither that
  the item is the helmet's pair nor that the entity is the sender. A modified client can create any item. Immersive
  Smithing only relies on the NBT copy (quality survives a swap). Server operators should weigh this before installing
  it on a public server.
- **Medieval Boomsticks**: the bundled data matches 1.2.x only (see "Tested versions"). The mod needs GeckoLib. Its alloy
  bars, barrels and the pieces made from them come from its own smithing furnace and stay there.
- **FoxRall's Visors** is client-only visuals with no items or recipes: there is nothing to integrate. It is loaded in the
  client test profiles only.

## Test profiles

Gradle properties choose the optional mods for `runGameTestServer` and `runClient` (all `compileOnly` or runtime-only
in development; none ships in the jar):

- `-PepicFight`: Epic Fight.
- `-PepicKnights` (or `=all`): Epic Knights with every add-on above except Ice and Fire, plus Swappable Helms and the
  support mods (Architectury, Cloth Config, and GeckoLib for Medieval Boomsticks).
- `-PepicKnights=base` is Epic Knights alone; a comma list adds keys to it (`addon`, `antique`, `slavic`,
  `sevenkingdoms`, `oresalloys`, `darkages`, `openhelms`, `swappable`, `artillery`, `boomsticks`, `iceandfire`, `foxrall`),
  for example `=addon` or `=all,foxrall`. `iceandfire` also loads Ice and Fire and Citadel; `foxrall` only joins client
  runs.
- `-PsimplySwords`, `-PcompatMods` (Spartan Weaponry, Spartan Shields, Immersive Armors) as before.

`CoverageGameTests` compares the live catalog with the fixture whose `loaded_mods` match the loaded set exactly
(`docs/compatibility/inventory/<profile>/`). Refresh a fixture after an intended change with
`python3 tools/coverage_inventory.py --profile <name>`, which also rebuilds [INVENTORY.md](INVENTORY.md).

## Verification (1.2.0)

| Check | Result |
| --- | --- |
| `runGameTestServer`, 18 profiles | All 86 required tests passed in each, with no startup failure: no optional mods; Spartan Weaponry, Spartan Shields and Immersive Armors (`-PcompatMods`); Epic Fight alone; Epic Knights alone; Epic Knights with Epic Fight; Epic Knights with every add-on but without Epic Fight; the largest compatible set (Epic Knights, every add-on except Ice and Fire, Epic Fight and Simply Swords); Epic Knights with Ice and Fire; and each add-on alone (Addon, Slavic Armory, Antique Legacy, Seven Kingdoms, Ores and Alloys, Dark Ages, Open Helms with and without Swappable Helms, Artilleries and Firearms, Medieval Boomsticks). [Results](validation/gametests-1.2.0.txt). |
| Client, `runClient -PsmokeTest -PepicKnights -PepicFight` | Passed. The armet is forged for 72 steel and its crafting recipe is gone, the ceremonial armet's gilding is kept, the steel plate melts for 9 steel, the uchigatana is forged for 36 iron, and the client's recipe set matches the server's. [Result](validation/client-smoke-epic-knights-epic-fight.json). |
| Dedicated server with two clients, Epic Fight and Epic Knights (`tools/visual-test/multiplayer_visual_test.py --extra-mods ... --expect epicfight,epicknights`) | Passed. Both clients received the server's 1,644 recipes after the rewrite: the armet and longsword forged, not crafted, the gilding kept, the uchigatana and bokken present. Remote tool swings and an anvil pickup synchronised. [Summary](validation/dedicated-server-epic-fight-epic-knights.json). |
| Release gate, `python3 tools/packtest/ultima_pack_test.py --expect spartan,shields,upgrades` | Passed on an isolated copy of the Ultima instance (377 mods): freeze check OK, every agent check true, no stack trace or error of Immersive Smithing, no crash report. [Summary](validation/ultima-release-gate-1.2.0.json). |
| `python3 tools/generate_epic_compat.py --check` | Up to date: 1,509 items, every one decided. |
| Packaged jar | Version 1.2.0; all bundled recipes, coverage files and tags present; no test, tool or documentation files and no nested jars. |

## Regenerating the data

```
python3 tools/generate_epic_compat.py            # --check (default): writes nothing, fails if anything differs
python3 tools/generate_epic_compat.py --write    # regenerate after changing a pin or the manifest
python3 tools/generate_epic_compat.py --refresh-registry run-gametest/logs
    # after ./gradlew runGameTestServer -PepicKnights=all,iceandfire -PepicFight -PregistryDump=all
```

Decisions the rules can't make (patterns, protected loot, assemblies, special recipes) are reviewed policy in
`tools/epic_compat_manifest.py`. An item the generator can't decide is an error, never a guess.
