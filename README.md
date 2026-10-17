# Immersive Smithing

Metal equipment is forged, not crafted. Melt metal in the Smith's Forge, shape it on the Smith's Anvil and quench it
in the Smith's Trough. Your skill in two short minigames decides the durability and efficacy of what you make.

A Forge mod for Minecraft 1.20.1.

![The four smithing stations](docs/visual-overhaul/after/ultima/packtest_world.png)

| Choosing a recipe | Forge minigame | Anvil minigame |
| --- | --- | --- |
| ![Recipe selection at the forge](docs/vanilla-gui/after/packtest_forge_select.png) | ![Forge minigame](docs/vanilla-gui/after/packtest_forge_play.png) | ![Anvil minigame](docs/vanilla-gui/after/packtest_anvil.png) |

## How it works

1. Put metal (ingots, nuggets, raw metal, storage blocks or old metal equipment) into the top of a Smith's Forge
   and fuel into the bottom.
2. Ignite the fuel with Flint and Steel or a Fire Charge and wait for the metal to melt. Lava needs no ignition,
   and netherite melts only with lava.
3. Use Smithing Tongs on the forge, choose an item and play the **Forge minigame**: press Space or click while the
   sweeping marker is inside the glowing zone.
4. Carry the hot workpiece to a Smith's Anvil and play the **Anvil minigame** with a Smithing Hammer: strike each
   target when the closing ring touches it.
5. Pick the workpiece up with the tongs and quench it in a Smith's Trough filled with water.

Stone tongs and a stone hammer are enough to start, and the whole process takes about a minute. Most metal weapons,
tools and armor can no longer be made at a crafting table. Players receive the Smithing Guide, an in-game book
with the details, the first time they obtain smithing tongs or a hammer. Every screen uses Minecraft's own look, and
the guide reads like a written book.

## Stations and tools

| Block or item | Crafted from | Purpose |
| --- | --- | --- |
| Smith's Forge | 5 bricks, a furnace, 3 cobblestone, blackstone or cobbled deepslate | Melts metal. Metal goes on top, fuel below. |
| Smith's Anvil | 3 iron ingots, 4 stone bricks | Shapes hot workpieces with a Smithing Hammer. |
| Smith's Trough | 7 wooden slabs, an iron ingot | Quenches shaped workpieces in water. |
| Smith's Grindstone | a diamond, 2 iron ingots, a grindstone | Refines smithed equipment for experience. |
| Smithing Tongs and Smithing Hammer | 3 stone, iron ingots or diamonds and 2 sticks; netherite: the diamond tool and a netherite ingot | Start forging and carry workpieces; strike at the anvil. |
| Smithing Guide | a book and coal or charcoal | The in-game guide. |

The four stations are built from few, large boxes (32 model cuboids together), and their outlines and collision
follow the same shapes.

Tongs and hammers come in stone, iron, diamond and netherite. Higher tiers give more time in the minigames (30,
35, 40 and 45 seconds) and last longer, but never add quality on their own. They can be repaired with their
material at an anvil and accept Unbreaking and Mending.

## Quality

Every smithed item carries two scores from 0 to 100.

- **Forge score** sets durability: 75% of normal at 0, normal at 50, 125% at 100.
- **Anvil score** sets efficacy: attack damage for weapons, mining speed for tools and armor points for armor.
  85% at 0, normal at 50, 115% at 100.

The average of both scores gives the item's label:

| Label | Average score |
| --- | --- |
| Crude | below 35 |
| Standard | 35 to 69 |
| Fine | 70 to 89 |
| Masterwork | 90 and above |

If the anvil timer runs out, the item is **Faulty**: half durability and 70% efficacy. The Smith's Grindstone adds
5 points to one score for experience levels, up to 100, and keeps enchantments; it cannot refine Faulty items. A
piece that has no grade yet but could carry one starts from Standard; that grade is written only when you pay for the
refinement.
Any metal equipment the forge recognises, enchanted or Faulty, can be melted back down, and all of its metal is
recovered by default. Equipment found in chests and treasure carries a grade of its own, never above Standard by
default, so anything Fine or better was made by a smith. That includes equipment the forge never makes, such as
Simply Swords' runic and unique weapons and Epic Knights' gilded pieces and relics.

### The Maker's Mark

Every quenched piece records who forged it ("Crafted by" in the tooltip). Right after the quench the smith can give
the piece a title and up to three lines of inscription, or sneak-use the Smith's Anvil with it later. The mark, the
title and the inscription survive repairs, enchanting, dyeing and elemental upgrades; only melting the piece down
removes them. Faulty work is never signed.

## Metals

Metal is measured in units: a nugget is 1, an ingot or raw ore 9 and a storage block 81. An iron sword costs 18
units (two ingots) plus a stick.

Iron, gold, copper and netherite families are built in. Tin, bronze, steel, silver, electrum, lead, nickel,
invar, constantan, platinum and aluminum have definitions that apply when another mod supplies them, and any other
`forge:ingots/<metal>` tag becomes a material family automatically. Families melt at different speeds: gold
quickly, netherite slowly.

Netherite equipment is forged straight from netherite ingots, with no smithing template or diamond equipment.
Smithing-table upgrades from other mods, such as netherite shields and weapons, are forged the same way, and the
smithing-table recipe is disabled. Scarce boss metals can use the `addition` upgrade policy instead: an ignitium
helmet costs one ingot plus the netherite helmet it reworks. Armor trims are never affected.

## Other mods

- **Automatic detection.** Crafting and smithing-table recipes for weapons, tools, armor and metal shields made
  mostly of one metal become smithing recipes. The main metal is the one the item is repaired with. Components such
  as sticks or leather, and small fittings of another metal, are taken from your inventory. A native recipe is
  disabled only when a smithing recipe replaces that exact recipe. Recipes that only reshape a finished piece (a
  sword into a spear) and custom recipe types (Simply Swords' runic rerolls) are kept. Recipes that mix metals or are
  otherwise unclear are left alone, and a metal shield built on a wooden base shield needs only the metal.
- **Upgrade chains.** A crafting recipe that reworks a piece the forge makes with more metal (Botania's terrasteel
  armour from manasteel armour) becomes a smithing recipe too, with the base piece consumed.
- **Spartan Weaponry** is supported out of the box: every metal weapon of all 24 types, including throwing weapons,
  longbows and heavy crossbows, has its own recipe and anvil pattern. A thrown weapon keeps its quality in flight,
  and arrows and bolts take the quality of the bow or crossbow that fires them. Spartan Fire's dragonsteel weapons
  are covered the same way.
- **Epic Fight** and **Epic Knights** come with built-in forge recipes that need no setup. Epic Knights is covered with
  its add-ons: Addon, Slavic Armory, Antique Legacy, Ice and Fire, Ores and Alloys, Dark Ages, Open Helms, Seven
  Kingdoms, Artilleries and Firearms and Medieval Boomsticks. Their metal weapons, armour and shields are forged on
  anvil patterns of their shapes. Each forge recipe disables only the native recipes it replaces. Epic Fight's
  uchigatana can be forged (36 iron, a diamond, two leather and a stick), and its bokken is crafted from two planks and
  a stick. Gilding, darkening, visor swaps, firearms assembly, relics and wooden or leather pieces keep their own route,
  and transformed pieces keep their quality and recycling value. Quality applies once in Epic Fight's battle mode, in
  the main hand and the off hand.
- **Simply Swords** weapons are found by automatic detection and forged on patterns that match their shapes, including
  the twinblade, warglaive and chakram patterns. Quality also applies to its abilities and thrown weapons. Runic
  upgrades, rerolls and unique weapons keep their usual way of being obtained, and unique weapons can't be melted
  down by default.
- [docs/compatibility/epic-integrations.md](docs/compatibility/epic-integrations.md) gives the tested versions, costs
  and known upstream issues, and [docs/compatibility/INVENTORY.md](docs/compatibility/INVENTORY.md) lists every piece of
  equipment from these mods with its route, cost, pattern, quality and recycling. Each integration has its own switch
  in the server config. TerraFirmaCraft keeps its own metalworking unless the server opts in.
- **Pack metals.** Cataclysm (black steel, ancient metal, witherite, cursium, ignitium), Ice and Fire dragonsteel,
  Iron's mithril and pyrium, Botania's manasteel, terrasteel and elementium and MCA's rose gold have names, tints,
  melt rules and upgrade policies. Fire Sticks, Lit It Up and Hardcore Torches fire starters light the forge.
- **Progression mods** can listen to `ItemSmithedEvent` (and, by default, the vanilla crafted event) to reward
  smithing; **Jade** shows what a forge, anvil or trough holds and what the grindstone would charge.
- **JEI** (optional) shows every smithing recipe, detected ones included, in a Smithing category, and hides the
  crafting recipes that smithing replaced.
- The automated tests also run with Spartan Weaponry, Spartan Shields and Immersive Armors installed, with Epic
  Fight and Simply Swords, and with Epic Knights alone, with each add-on and with the largest compatible set.

Datapacks can add or override material families, smithing recipes, recycling values and minigame patterns. The
formats and the Java/IMC API are documented in [docs/API.md](docs/API.md).

## Multiplayer and automation

The server runs the minigames: clients only send their key presses, and the server checks timing and scores
them. While someone works at an anvil, nobody else can take or start its workpiece.

Hoppers and pipes can feed metal and fuel into a forge and water into a trough, and comparators read both.
Automation can never choose a recipe, play a minigame, move a workpiece or quench it.

Toolsmiths and Weaponsmiths sell tongs and hammers. They and Armorers also sell iron equipment forged at a random
quality, never Faulty by default.

## Configuration

`immersive_smithing-server.toml` (per world, synced to clients) covers recipe replacement and automatic detection,
forge capacity and fuel, minigame times, quality multipliers, recycling, the trough and grindstone, villager
trades, automation faces, loot quality, the Maker's Mark (signing) and the integrations (Spartan Weaponry, Epic
Fight, Epic Knights and single add-ons, Simply Swords, TerraFirmaCraft, the vanilla craft event, quality
inheritance). Run `/reload` after changing recipe or integration options.

`immersive_smithing-client.toml` covers presentation only: numeric quality scores in tooltips, reduced screen shake
and flashes, particle amount, high-contrast minigames, larger and colorblind-safe targets, timing cue sounds, how long
a strike's grade stays readable (`feedbackLabelMs`), and station messages kept above the hotbar (`stationNotices`,
`noticeSeconds`; off uses the action bar). These options never widen scoring windows.

## Commands

Operators (permission level 2):

| Command | Purpose |
| --- | --- |
| `/immersivesmithing reload` | Full datapack reload. |
| `/immersivesmithing material <item>` | Family, units and the rule that matched, or the recycling value. |
| `/immersivesmithing recipe <item>` | Smithing recipes for the item and the recipes they disabled. |
| `/immersivesmithing quality` | Quality and multipliers of the held item. |
| `/immersivesmithing report` | Writes `logs/immersive_smithing_report.txt` with every detection decision. |
| `/immersivesmithing session` | Active minigame sessions. |
| `/immersivesmithing catalog <modid>` | Writes `logs/immersive_smithing_catalog_<modid>.json`: every piece of equipment the mod registers, how it is obtained (its disposition and the reason), its smithing recipes, what happened to its native recipes, quality and recycling. |

## Requirements

- Minecraft 1.20.1 with Forge 47 or later, Java 17.
- Install on both the client and the server.
- Optional: Just Enough Items, Jade.

## Building

```sh
./gradlew build                  # jar in build/libs/
./gradlew runGameTestServer      # headless GameTests
./gradlew runGameTestServer -PcompatMods   # the same with Spartan Weaponry, Spartan Shields and Immersive Armors
./gradlew runGameTestServer -PepicFight -PsimplySwords   # with Epic Fight and/or Simply Swords (either flag alone works)
./gradlew runGameTestServer -PepicKnights        # Epic Knights with its add-ons (=base alone, =addon,slavic, =all,iceandfire)
python3 tools/model_preview.py --check           # station model budgets and model/texture resolution
```

Artwork sources and Blockbench model notes are in [art/README.md](art/README.md).

## License

GPL-3.0. See [LICENSE](LICENSE).
