# Immersive Smithing — Epic Knights ecosystem compatibility

**Coding-agent implementation handoff · Research date: October 31, 2026**

## 1. Objective and implementation contract

Implement comprehensive, optional compatibility between **Immersive Smithing**, **Epic Knights**, and the notable Java Edition addons listed below, targeting **Minecraft 1.20.1, Forge 47, Java 17**. Use the existing Forge → Smith's Anvil → Trough workflow, quality system, maker's mark, recipe display, recycling, and server-authoritative sessions.

Deliver working integration, automated coverage evidence, multiplayer verification, and updated player/developer documentation. Do not stop after adding a few steel recipes or confirming that the game launches.

“Full compatibility” means that every relevant registered item and acquisition/transformation route in each tested addon has an explicit disposition:

- **Forge:** eligible metal weapons, tools, armor, and metal shields use the Immersive Smithing workflow.
- **Preserve:** dyes, heraldry, decorations, visor changes, special upgrades, ammunition, nonmetal equipment, alloy production, and other native processes remain usable and retain their intended requirements.
- **Preserve progression:** unique loot, restricted equipment, disabled content, and addon-specific unlocks are not made freely craftable.
- **Coexist:** addons that deliberately use a different manufacturing system retain that system by default, with documented recipe ownership and no duplicate quality effects.
- **Explain:** every exclusion or unresolved mapping has a specific reason in the compatibility report. Unknown behavior is not reported as verified support.

Keep all integrations optional. Preserve original item registry IDs, classes, capabilities, combat behavior, rendering, and resource-pack support. Do not replace upstream equipment with look-alike Immersive Smithing items. Do not add new stations, an alloying system, or blanket rebalance changes merely to implement compatibility.

This handoff is based on project pages and static source inspection; no game instance or mod build was executed during this research. Findings below distinguish directly inspected behavior from risks that require reproduction.

## 2. Establish the correct baseline before editing

The linked [Immersive Smithing project](https://www.curseforge.com/minecraft/mc-mods/immersive-smithing) lists **1.1.1 for Forge 1.20.1**. The inspected public repository snapshot still describes an unreleased 1.0.0 in its changelog. Therefore, reconcile the user's actual development branch and release source before applying changes. Do not assume the public snapshot exactly represents the distributed 1.1.1 jar, or choose a new release version from this document.

Inspected repository references:

| Repository | Branch/ref inspected | Relevant observation |
|---|---|---|
| [otectus/ImmersiveSmithing](https://github.com/otectus/ImmersiveSmithing) | `main`, `0c5e781f53641a5cc9dcab72acde2a50e55a6fd3` | Existing recipe detector, quality hooks, sessions, materials, API, and test setup |
| [Magistu/Epic-Knights](https://github.com/Magistu/Epic-Knights/tree/1.20.1) | `1.20.1`, `aa772b7d156f6bd7e9ab58c8dbb7b660d70fdaf3` | Source declares version 10.15; mod ID `magistuarmory` |
| [Magistu/Epic-Knights-Addon](https://github.com/Magistu/Epic-Knights-Addon/tree/1.20.1) | `1.20.1`, `1ca1e20661f0c82e3b2417ab4babc705215c7e80` | Version 2.6; mod ID `magistuarmoryaddon` |
| [Magistu/Epic-Knights-Antique-Legacy](https://github.com/Magistu/Epic-Knights-Antique-Legacy/tree/1.20.1) | `1.20.1`, `66575441f602c522ec42b38c6647199b34f66591` | Version 1.13; mod ID `antiquelegacy`; source layout inventoried |
| [Magistu/Epic-Knights-Ice-And-Fire](https://github.com/Magistu/Epic-Knights-Ice-And-Fire) | `master`, `7e964255769c1f34df193e8914c8dd24eceef17e` | Version property 1.1; mod ID `epic_knights_ice_and_fire`; materials and weapon subclasses inspected |
| [Magistu/Epic-Knights-Thirty-Years-War](https://github.com/Magistu/Epic-Knights-Thirty-Years-War/tree/1.20.1) | `1.20.1`, `22c2a6bcd18b214e9d866894a0393088327e2b61` | Version 1.3; mod ID `thirtyyearswar`; hitscan firearm implementation inspected |
| [WitchkingsRu/EpicKnightsOfTheSevenKingdoms](https://github.com/WitchkingsRu/EpicKnightsOfTheSevenKingdoms/tree/1.20.1) | `1.20.1`, `e8f57bc4102c3b84e10cd91dc83c60c5d4d52c21` | Source declares `1.20.1_2.0.0`, while the project page lists 1.5.0; unique-loot implementation inspected |

For each test profile, record the actual jar filename, file/version ID, hash, loader, mod ID, dependencies, and matching source commit when available. Public source branches can be ahead of releases. Inspect the selected jar's recipes and metadata as the runtime authority; inspect/decompile relevant classes when matching source is unavailable. Never infer a mod ID from its display name or filename.

Read repository instructions, `README.md`, `CLAUDE.md`/`AGENTS.md` if present, `MODMAP.md`, `CHANGELOG.md`, `docs/API.md`, and relevant implementation notes first. Preserve existing user changes and existing integrations.

## 3. Supported project inventory

The scope combines the base author's addon list, established equipment/material expansions, and companion mods that directly affect equipment behavior. Versions below are **observed candidate baselines**, not assertions that the entire combination already works. Recheck file metadata at implementation time. Fabric/NeoForge releases on the same project page do not establish Forge 1.20.1 compatibility.

### Equipment, materials, and related functionality

| Project | Observed Forge 1.20.1 candidate | Required integration scope |
|---|---|---|
| [Epic Knights](https://www.curseforge.com/minecraft/mc-mods/epic-knights-armor-and-weapons) | 10.15 | Every enabled metal weapon family, metal armor, metal shields, relevant horse armor, components, upgrades, and customization routes |
| [Epic Knights: Addon](https://www.curseforge.com/minecraft/mc-mods/epic-knights-addon) | 2.6 | Expanded equipment, gilding/darkening, decorations, clothing separation, and cross-mod upgrade chains |
| [Slavic Armory](https://www.curseforge.com/minecraft/mc-mods/epic-knights-slavic-armory) | 2.1 | Metal mail, lamellar/plate equipment, weapons, and native assembly/customization |
| [Antique Legacy](https://www.curseforge.com/minecraft/mc-mods/epic-knights-antique-legacy) | 1.13 | Bronze and other metal equipment, shields, components, decorations, and nonmetal exclusions |
| [Dark Ages](https://www.curseforge.com/minecraft/mc-mods/epic-knights-dark-ages-addon) | 1.1, file `7107406` | Early-medieval metal equipment and gilded/cloth variants. The file explicitly lists both 1.20.1 and 1.20.2 |
| [Japanese Armory](https://www.curseforge.com/minecraft/mc-mods/epic-knights-japanese-armory) | 1.6.2 | Metal Japanese weapons and armor; preserve textile clothing and special-item acquisition. Verify compatibility with the selected newer base-mod jar |
| [Epic Knights: Ice and Fire](https://www.curseforge.com/minecraft/mc-mods/epic-knights-ice-and-fire) | 1.1 | Metal families, dragonsteel weapons, native effects, and nonmetal/blood/venom distinctions |
| [Ores and Alloys](https://www.curseforge.com/minecraft/mc-mods/epic-knights-ores-and-alloys) | 1.1 | Material tags and additional tools; preserve ore processing and alloy acquisition |
| [Ingots Addition for Epic Knights](https://www.curseforge.com/minecraft/mc-mods/ingots-addition-for-epic-knights) | 1.0.6 | Alternate tin/bronze/silver supply and steel/bronze equipment; coexist with other suppliers |
| [Thirty Years War](https://www.curseforge.com/minecraft/mc-mods/epic-knights-thirty-years-war) | 1.3, released October 31, 2026 | Metal melee weapons, metal armor, firearms, darkened variants, and textile exclusions |
| [Artilleries and Firearms](https://www.curseforge.com/minecraft/mc-mods/epic-knights-artillery-addon) | 1.16.2 | Handheld metal weapons, thrown weapons, firearm state, metal parts, and native mounted-weapon/ammunition assembly |
| [Medieval Boomsticks](https://www.curseforge.com/minecraft/mc-mods/medieval-boomsticks) | 1.01 release; 1.2 alpha is a separate profile | Formerly Ranged Knights Medieval Boomsticks and Sharpsticks. Preserve GeckoLib animation, reloading, intermediate components, and unavailable/unfinished content |
| [Epic Knights of the Seven Kingdoms](https://www.curseforge.com/minecraft/mc-mods/epic-knights-of-the-seven-kingdoms) | 1.5.0 release; source head differs | Craftable metal equipment and decorations; preserve unique-loot rules, rare materials, and one-per-world acquisition |
| [Epic Fletchery](https://www.curseforge.com/minecraft/mc-mods/epic-fletchery) | 0.7; work in progress | Crossbow/metal-part classification, projectile behavior, and compatibility with its native bow/ammunition crafting |
| [Open Helms](https://www.curseforge.com/minecraft/mc-mods/epic-knights-open-helms) | 1.3 | Closed/open equipment equivalence and metadata preservation; do not restore removed crafting recipes |
| [Swappable Helms](https://www.curseforge.com/minecraft/mc-mods/swappable-helms-for-epic-knights) | 1.0 | Server-side swapping, quality, damage, marks, decorations, enchantments, and configured mappings |
| [FoxRall's Visors](https://www.curseforge.com/minecraft/mc-mods/foxralls-visors) | 1.0.6 Forge file | Visual coexistence and helmet identification; no new forging recipes are required for a visor overlay |

### Combat and manufacturing companions

| Project | Observed candidate | Required disposition |
|---|---|---|
| [Epic Knights x Better Combat Rework](https://www.curseforge.com/minecraft/mc-mods/epic-knights-x-better-combat-rework) | June 30, 2026 multi-version wrapped datapack | Preserve its weapon presets and damage multipliers; validate the exact file's 1.20.1 metadata |
| [Overgeared x Epic Knights](https://www.curseforge.com/minecraft/mc-mods/overgeared-x-epic-knights) | 1.1.1 Forge | Detect competing recipe ownership; preserve its forging/casting/assembly and blueprints by default |
| [Epic Knights of TerraFirma](https://www.curseforge.com/minecraft/mc-mods/knights-of-terrafirma) | 3.1.2 | Preserve TFC manufacturing, material progression, heat, and native equipment behavior in a dedicated coexistence profile |
| [Epic Knights of TerraFirmaGreg](https://www.curseforge.com/minecraft/mc-mods/epic-knights-of-terrafirmagreg) | 3.1.2 gregified | Separate pack-specific alternative to the preceding project; preserve its metallurgy and progression |

Also verify with **Better Combat** and **Epic Fight** separately. They are combat dependencies/companions, not interchangeable names for Epic Knights. Do not require both together. Test original Ice and Fire and an applicable Community Edition build separately only where the selected addon supports them; the project's Fabric CE statement is not proof of Forge CE compatibility.

Bedrock ports, unrelated modpacks, texture packs, translations, and different-version-only ports do not require new Java Forge content adapters. Record any newly discovered relevant Forge addon in the same inventory rather than silently narrowing the scope to this list.

## 4. Concrete findings from the inspected code

Paths in this section are relative to the repositories above. Reproduce each finding against the selected release before calling it a confirmed player-facing defect.

| Finding | Evidence | Implementation consequence |
|---|---|---|
| Metal components are not recognized automatically as ingots | `MaterialRegistry.build` and the built-in `materials/steel.json` recognize ingots, nuggets, raw materials, and storage blocks. Epic Knights armor uses plates and mail components | Add verified component accounting; a steel-ingot mapping alone is insufficient |
| Some components represent fractional nuggets | Epic Knights `steel_ring.json` produces 8 rings from 4 nuggets; `small_steel_plate.json` produces 8 small plates from 6 nuggets | Do not round each component upward or label every plate as one ingot |
| Actual upgrades can lose the base requirement or be underpriced | `AutoRecipeDetector.generateChains` can use `SHAPE`, taking the base's metal cost while omitting the consumed base. Epic Knights' `steel_bastardsword.json` consumes a shortsword plus an ingot | Use a recipe-level upgrade policy; preserve base-plus-addition recipes and progression |
| Blocking weapons can be treated as shields | Epic Knights `MedievalWeaponItemForge.canPerformAction` advertises shield actions for block-capable weapons. Both `EquipmentClassifier.isShield` and `QualityHooks.isShield` use shield blocking as evidence | Fix semantic classification in both recipe and quality paths. An attack-capable blocking sword must still receive weapon efficacy |
| Gilding can enter the generic metal-upgrade path | Addon's ceremonial Kastenbrust recipe uses a steel base, gold addition, and gilding template. `generateUpgrades` identifies the addition's metal and omits templates | Preserve gilding as a native transformation; never generate a solid-gold substitute or bypass its template |
| Suppression is broader than the specific replaced recipe | `SmithingData.rebuild` suppresses crafting recipes with metal and smithing transforms based on whether their result item is smithable | Track exact source recipe IDs and policies; sharing an output is not enough to remove a route |
| Different recipes for the same output can collapse | Automatic generation maps by result `Item`, chooses the first sorted usable recipe, and creates IDs from the output | Keep intentional alternate recipes and NBT variants distinct or prove they are equivalent before merging |
| Consuming a base currently does not preserve its complete stack | `AuxInventory.take` escrows stacks; `SessionManager` constructs the workpiece from `recipe.result().copy()` | An upgrade adapter must assemble from the actual reserved base before escrow is discarded |
| Generic quality inheritance is not a complete transformation adapter | `CommonEvents.onItemCrafted` copies quality/mark only under limited conditions; it does not handle a helmet keybind or placed block lifecycle | Audit all transform paths. Do not assume a crafting event solves visor swapping or metadata transfer |
| Ranged items and non-damageable gear need explicit coverage | `EquipmentClassifier.isCandidate` excludes projectile weapons by default and usually requires durability. `IEquipmentClassifier` only selects a pattern | Add role eligibility separately from pattern selection; account for horse armor, firearms, decorations, and stackable parts |
| The projectile hook does not cover every firearm | `ProjectileEfficacy` handles `AbstractArrow` plus Spartan thrown weapons. Thirty Years War `GunItem.performShooting` performs a server raycast and directly applies damage | A generic arrow-spawn hook cannot implement hitscan firearm efficacy |
| Provider recipes need careful synchronization | Recipe providers feed `SmithingData`'s active list, while the inspected manager rewrite explicitly adds the automatic list | If using Java recipe providers, verify/fix manager insertion, network sync, JEI visibility, and reload deduplication |

### Verified component arithmetic

Use these as regression fixtures for the inspected Epic Knights 10.15 source; recalculate from the selected runtime recipes when packs override them.

| Component | Native construction | Exact steel content, in nugget units |
|---|---|---:|
| Steel plate | 2 ingots → 2 plates | 9 per plate |
| Small steel plate | 6 nuggets → 8 small plates | 3/4 per plate |
| Steel ring | 4 nuggets → 8 rings | 1/2 per ring |
| Steel chain | 3 rings → 1 chain | 3/2 per chain |
| Steel chainmail component | 9 rings → 1 component | 9/2 per component |
| Chainmail chestplate | 8 chainmail components → 1 chestplate | 36 total = 4 ingots |

The inspected knight chestplate recipe consumes **a halfarmor chestplate and five steel plates (45 added steel units)**. The halfarmor is not a free ingredient: consume that exact base stack and keep its acquisition chain. Corrected to match the pinned Epic Knights 10.15 jar and its referenced upstream recipe (`p p` / `php` / ` p `).

## 5. Phase A — Inventory and auditable coverage

Build an inventory tool and runtime report before broad recipe changes. Suggested development outputs:

- `docs/compat/epic-knights-support.md`: supported versions, profiles, known limits, and player-facing behavior.
- `docs/compat/epic-knights-coverage.csv`: registered item/variant and route coverage.
- `tools/epic_knights_inventory.py`: deterministic jar/resource inventory, or an equivalent tool fitting the repository.
- A machine-readable test manifest with exact mod hashes and expected classifications.

For every target namespace, enumerate registered items, active recipes, recipe serializers/types, tags, upstream config toggles, transformations, and special acquisition routes. Inspect both Forge-specific and common resources; do not accidentally inventory Fabric recipes as the Forge implementation.

Each coverage row must include:

`mod/version, item ID, role, variant/state, material family, source recipe IDs, acquisition mode, replacement policy, forge cost, auxiliaries/base/template, output count, recycling policy, quality behavior, pattern, verification status, reason`.

Use explicit statuses such as `FORGE_BASE`, `FORGE_UPGRADE`, `NATIVE_TRANSFORM`, `NATIVE_ASSEMBLY`, `NONMETAL`, `LOOT_ONLY`, `EXTERNAL_WORKFLOW`, `DISABLED_UPSTREAM`, and `UNRESOLVED`. These are proposed report categories, not existing APIs.

Start with all registered content, not only items the current detector already sees. Otherwise a report can claim 100% coverage while omitting every firearm or horse armor item. Report item counts and acquisition-route counts separately. Optional missing mods are “not installed,” not failures; a relevant installed item with no explained route is a failure.

## 6. Phase B — Recipe policy and material resolution

### 6.1 Extend the existing integration architecture

Prefer a small `compat/epicknights/` module, data-backed mappings, and shared fixes in the existing recipe/material code. Do not create a second forge or replace the entire recipe manager independently of `SmithingData`.

Introduce a **recipe-level decision** before automatic generation and suppression. It should identify the source recipe, semantic operation, material/ingredients, output behavior, and whether replacement is permitted. The exact class/file names are implementation choices.

Required precedence:

1. Explicit server/pack exclusions and selected external workflow ownership.
2. User datapack overrides and explicit per-recipe policies.
3. Built-in addon-specific rules, guarded by verified mod/version/item availability.
4. Conservative automatic detection.
5. Preserve the upstream route and report a reason when analysis is uncertain.

Excluding an item from **forging**, excluding it from **recycling**, and excluding it from **all quality effects** are different decisions. Do not use the broad public exclusion predicate when only one behavior should be disabled.

Use the loaded, post-condition recipe manager and bound tags as the source of truth. Built-in compatibility must not resurrect recipes deliberately removed by a datapack, upstream configuration, or an external forging integration. Any intentional fallback recipe needs an explicit rule and diagnostic provenance.

If provider-generated recipes are used, ensure every active generated recipe is inserted exactly once into the authoritative manager and synchronized to clients. Preserve higher-priority datapack overrides. Validate result items, count, ingredients, family, pattern, and conditions before suppressing anything.

### 6.2 Resolve materials without inventing metal

- Reuse existing canonical families for steel, iron, gold, copper, tin, bronze, silver, netherite, and the three dragonsteel materials where available.
- Support multiple ingot suppliers through valid tags. Do not register a second steel family solely for Epic Knights.
- Recognize exact verified components through a bounded, allowlisted component graph. Generic recursion through every crafting recipe is unsafe and unnecessary.
- Resolve chains after tags bind, in deterministic dependency order. Detect cycles, ambiguous alternatives, missing items, empty tags, mixed-metal inputs, count mismatches, and unknown serializers. Bound work and cache by reload generation.
- Classify a component's metal separately from whether it may be inserted directly into a forge. A known manufacturing cost does not automatically authorize every item as melt input.
- Expand simple metal components only when their source recipes and output counts are unambiguous. Preserve finished equipment bases, gated components, organic parts, and special ingredients as actual inputs.
- Do not count a required component twice: either its verified metal is paid from the forge or the actual component is consumed.
- Preserve all nonmetal costs, including leather, handles, shafts, string, cloth, gun stocks, and relevant crafted fittings. Honor ingredient alternatives without substituting an arbitrary first match.

Use rational arithmetic during analysis. Keep the existing saved convention of nugget = 1, ingot = 9, block = 81. For a fractional final manufacturing total, round **once at the final forge charge**, upward; cap recycling at the exact known content rounded downward. Document the small rounding loss. Do not change the global saved unit scale just to accommodate rings.

For direct recycling of fractional components, accept only verified batches whose aggregate content is integral, or implement an exact persisted remainder with migration and equivalence tests. Do not assign a half-nugget ring a one-nugget melt value. Keeping native component production available is sufficient; players should not need separate full minigames for every individual ring.

Do not add all `forge:plates/*` or vaguely named items globally at an assumed cost. A modpack can define materially different plate and ring recipes. Ambiguous tags need explicit per-item/per-recipe rules.

### 6.3 Preserve the meaning of upgrades

Use the following default policies:

| Operation | Default behavior |
|---|---|
| New eligible metal equipment from raw material/components | Forge, shape, and quench; consume complete verified costs |
| Equipment physically rebuilt with additional metal | Consume the exact base plus the added metal and other required ingredients |
| Gilding, full gilding, darkening, heraldry, dyeing, decoration add/remove, trim | Keep the native recipe/process and requirements; preserve the existing gear's quality and mark |
| Nonmetal enhancement, blood/venom treatment, special template unlock | Keep native unless a dedicated adapter reproduces every prerequisite and effect |
| Genuine netherite material replacement | Honor the project's established configurable netherite policy through an explicit supported rule; never generalize it to every smithing template |
| Open/closed visor conversion | Preserve the native swap route; no new forge recipe simply because the open item has no crafting recipe |
| Unique or loot-only equipment | Preserve its acquisition; no automatic manufacturing or profitable salvage recipe |

Do not use a material-wide `shape`/`addition` setting to decide whether gold means plating, a new weapon, or a rare upgrade. In particular, keep the fully gilding template's villager acquisition meaningful.

Support multi-step equipment upgrades with a deterministic fixed-point/dependency pass, not accidental alphabetical recipe order. Multiple source recipes may lead to one item; keep meaningful alternatives. Use stable recipe IDs derived from source IDs and semantic variants rather than only the output item ID.

Mixed-metal construction should use one verified primary forge family plus actual secondary components where appropriate. Do not feed two molten families into the existing single-family forge. Keep alloy production in the upstream furnace/blast furnace/process; copper plus tin must not become free bronze inside an ordinary steel forge.

### 6.4 Suppress only verified replacements

Maintain an explicit set or mapping from **source recipe ID → validated replacement recipe(s)**. Remove a source route only after its replacement is valid, enabled, synchronized, and usable with obtainable inputs.

Never remove another route merely because it produces the same item. Preserve native special recipes and NBT-sensitive transformations. Keep armor trims, decoration removal, template duplication, dyes, and zero-metal processes intact.

Make reloads idempotent. Rebuilding twice from an already modified recipe manager must not lose source recipes, shrink coverage, or duplicate outputs. Keep a well-defined source snapshot for the current reload generation; do not restore recipes a newer datapack removed. Build all indexes and suppression decisions before committing one consistent snapshot.

## 7. Phase C — Item roles, quality, and data preservation

### 7.1 Fix blocking-weapon classification first

Introduce or reuse a shared semantic role resolver used by eligibility, pattern selection, quality, shield treatment, and reporting.

- Identify true Epic Knights shields through actual shield classes/verified mappings.
- Identify `MedievalWeaponItem` and relevant subclasses as weapons even when they can block.
- Keep capabilities available for combat; do not “fix” this by disabling weapon blocking.
- Make both `EquipmentClassifier.isShield` and `QualityHooks.isShield` agree. A pattern-only classifier hook does not solve the quality early return.
- Check other integrations for the same ambiguity, while avoiding blanket changes to unrelated modded items.

Keep durability quality on normal damage paths. Apply melee efficacy once to damage while preserving upstream attack speed, reach, armor piercing, two-handed penalties, knockback, mounted/lance behavior, and elemental or silver effects. Do not multiply every attribute or every damage event.

True shields should retain the existing default of durability quality with efficacy-neutral defense unless an intentional, tested shield adapter is supplied. Explain that behavior in tooltips/guide text; do not silently alter block thresholds or shield cooldown balance.

Classify metal horse armor and non-damageable metal wearables explicitly. Preserve their native use even when they have no durability to scale. If the horse's protection uses a direct property rather than normal wearer attributes, add a narrowly scoped equipment-aware efficacy hook; otherwise do not claim that a tooltip score affects protection. Keep decorative metal accessories neutral unless they genuinely provide supported stats.

### 7.2 Assemble upgrades from reserved input stacks

Extend the upgrade result path so the server retains the exact base stack and its role in escrow, creates a fresh target stack, and applies a documented transfer policy before consuming escrow. Do not reconstruct the base from a registry ID or search the player's inventory after reservation.

Preserve as appropriate:

- Enchantments, custom name/lore, repair cost, damage/wear, trim, dye/color, heraldry, and attached decorations.
- Immersive Smithing quality, fractional durability carry, maker UUID/name, signature state, title, and inscription for equivalent/cosmetic transformations.
- Supported upstream NBT/capabilities, including persistent projectile or firearm state where relevant.

Never blindly overwrite the target's whole tag with the base tag. Preserve target-required defaults and apply explicit transfers or the upstream recipe's own assembly logic on copies. Do not copy arbitrary capabilities, projectile identities, or a loaded firearm's ammunition into another item without understanding ownership.

Distinguish three operations:

1. **Cosmetic/equivalent transform:** keep quality, wear, and original maker unchanged; no new smithing achievement or quality reroll.
2. **Structural forge upgrade:** consume the base, use the new minigame results under the existing reforging policy, and disclose the new maker/quality behavior. Preserve supported personal/upstream data; damage handling must be explicit and cannot become a free repair through reversible transforms.
3. **Melting:** retain the existing destructive recycling behavior and metal accounting; do not also return the same base/attachments as free items.

For cosmetic transformations, compare the full before/after state, including `DurabilityCarry`. Copying only score fields can subtly reset wear. For decorations removed from armor, ensure the returned decoration does not acquire the armor's quality/identity accidentally.

Preserve cancellation safety: abort, timeout, disconnect, death, distance failure, block break, chunk unload, reload, and restart must not duplicate or lose a base stack. Reserve and refund complete stacks exactly once. Preserve remainder/catalyst behavior if a supported recipe returns a tool/container; the existing auxiliary list is not proof that those semantics are implemented.

### 7.3 Visors, pavises, and presentation

- Test Open Helms 1.3 together with Swappable Helms. The author removed open-helmet crafting from the 1.2-era workflow; native swapping is the intended route.
- Verify custom configured helmet pairs as well as built-in pairs. Preserve exact damage where max durability matches; document a safe proportional wear rule when it differs.
- Verify quality, signing rights, decorations, color, name, enchantments, and trim after repeated swaps and reconnects. Patch the smallest server-side swap boundary only if the actual addon fails to preserve them.
- Test placed pavise → chunk save/load → pickup/break/drop. The inspected base mod already stores stack tags in `PaviseBlockEntity`; reuse that behavior and patch only a reproduced loss. Check item damage, banner state, maker mark, and quality on every pickup route.
- Render the real target stack in forge previews, hot workpieces, tongs, anvils, and JEI. Long polearms, large shields, bows, and custom-rendered guns must fit and remain identifiable.
- Keep FoxRall visor overlays and resource packs working. Test menus while wearing a closed helmet and after opening it.
- Choose sensible existing anvil patterns for swords, polearms, axes/hammers, armor, shields, and ranged weapons. Do not classify every Epic Knights weapon as the same short sword merely because its Java base class is `SwordItem`.

## 8. Phase D — Addon-specific requirements

### 8.1 Main Addon, Slavic Armory, Antique Legacy, Dark Ages, Japanese Armory

Use the shared material and recipe policy layer, with small verified exceptions per addon. Inventory armor assemblies, material components, shields, gilded variants, decorated variants, and conversions involving the base mod or another addon.

Separate metal armor from robes, tunics, gambesons, hats, kimono, cloaks, plumes, leather pieces, and other textiles. An `ArmorItem` subclass or defensive stat does not make cloth smithable. Composite metal armor can be forged while retaining its textile costs or consumed underlayer.

Support metal decorations and fittings through their native component recipes or explicit metalworking mappings. Keep adding/removing them on equipment native. Do not require leather and cloth crafts to pass through molten steel, and do not introduce new dependencies on Immersive Tanning.

Verify legacy Japanese Armory against the pinned Epic Knights API. If an upstream binary incompatibility prevents startup even without Immersive Smithing, document that baseline separately and choose a supported version combination; a conditional recipe JSON cannot repair missing upstream classes.

### 8.2 Ores and Alloys and Ingots Addition

Test each supplier alone and both together, plus one unrelated provider of the same Forge metal tags. All valid steel/tin/bronze/silver ingots must resolve consistently without duplicate material families or recipe multiplication.

Preserve mining, smelting, blasting, alloying, raw-metal recipes, and their configured availability. Do not silently change Ingots Addition's intentional supply shortcuts. Check added tools and armor too; these projects supply equipment as well as materials.

### 8.3 Ice and Fire

Verify all registered weapon/material combinations rather than relying on marketing counts. The inspected source differentiates dragonsteel, dragonbone/alchemical dragonbone, and myrmex/chitin families.

- Forge actual metal equipment, including supported silver/copper and fire/ice/lightning dragonsteel variants.
- Keep dragonsteel families distinct and use existing material definitions/heat requirements where available.
- Preserve the upstream route that creates dragonsteel ingots. Do not provide an iron-to-dragonsteel shortcut.
- Keep bone and chitin nonmetal. Preserve blood/venom enhancement recipes, their required base, special ingredients, and effects.
- Retain native burn/freeze/lightning, undead bonuses, and other weapon effects through original item classes. Apply quality only at the supported physical damage layer, once.
- Test with the selected original Ice and Fire version; separately verify a compatible CE profile if supported. Report dependency mismatches instead of treating an identical item namespace as proof of binary compatibility.

### 8.4 Firearms, thrown weapons, and Fletchery

For **Artilleries and Firearms**, **Medieval Boomsticks**, **Thirty Years War**, and **Epic Fletchery**, inventory the actual firing implementation for each weapon family. Determine whether damage comes from an `AbstractArrow`, custom projectile, direct raycast, or another server-side procedure.

The inspected Thirty Years War 1.3 `GunItem.performShooting` uses a raycast and calls its armor-piercing damage helper directly. Add a narrow optional adapter at the firing/damage boundary if firearm efficacy is enabled; changing `ProjectileEfficacy.onJoin` alone will do nothing for these shots.

Required rules:

- Forge eligible metal handheld weapons or verified metal assemblies, with complete wood/stock/cord/mechanism costs. Keep wooden bows and ordinary nonmetal gear native.
- Keep powder, cartridges, arrows, bolts, fuses, and consumable explosive assembly native. Do not turn every ammunition unit into a three-station minigame.
- Handle mounted weapons through their native block/entity assembly; forge appropriate metal parts only when supported. Do not force a siege engine into an equipment-only output path.
- Preserve loading state, loaded ammunition, reload requirements, offhand tools, cooldowns, animation, and configuration. Do not reset or duplicate loaded ammunition during upgrade/refinement/cancellation.
- Snapshot the actual firing or thrown stack at launch. Do not use whatever the shooter happens to hold when a later hit occurs.
- Apply efficacy once to the intended base damage. Preserve armor-piercing ratio, penetration, spread, projectile count, range, reload time, and native secondary effects unless a separate documented design explicitly changes them.
- Mark/track applied quality across save/load and chunk transitions; do not rescale repeatedly. Handle NPC shooters and both hands without granting an unrelated held weapon's quality.
- Preserve the full thrown weapon on recovery/return. Verify durability, enchantments, maker, color, and unique upstream state.
- Avoid multiplying both launcher and ammunition quality by default. Leave ammunition quality-neutral unless the mod already has an explicit compatible model.

Test these as independent addon profiles. Do not treat Boomsticks 1.2 alpha as interchangeable with its release build. Items described as placeholders or unavailable in the selected build must not gain arbitrary recipes merely to fill a coverage table.

### 8.5 Seven Kingdoms and rare equipment

Preserve ordinary craftable metal equipment, heraldry, and decoration behavior. The project describes limited world-spawn weapons, and its inspected source has a unique-loot manager; preserve that restriction.

- Do not synthesize recipes from item names, repair materials, or armor/weapon classes for unique weapons and rare loot-only armor.
- Do not turn a unique item into generic metal that can recreate more copies, or create a reforge loop that bypasses the unique-loot ledger.
- Treat salvage, acquisition, and quality policies separately. Default rare/restricted items to no new manufacturing and no newly introduced recycling route.
- Preserve native item identity and lore through repairs and allowed transformations. Do not overwrite unique names with generic quality labels.
- Verify the actual released 1.5.0 behavior; the public 1.20.1 source head advertises 2.0.0 and must not be assumed identical.

### 8.6 Combat and alternative manufacturing systems

**Better Combat / Epic Fight / Better Combat Rework:** retain native item IDs and their attribute/preset resources. Verify attack combos, two-handed behavior, offhand attacks where supported, reach, block behavior, and damage at low/standard/high quality. Prevent duplicate scaling between an attribute hook and a combat event. Do not install both combat overhauls simply to satisfy a single test profile.

**Overgeared x Epic Knights:** its documented purpose is replacing Epic Knights crafting with another forge/cast/assembly workflow. Detect this ownership before automatic recipe generation. Default affected routes to `EXTERNAL_WORKFLOW`, keep its components and blueprints, and prevent Immersive Smithing from reintroducing bypass recipes or stacking quality systems. Provide an explicit server/pack recipe-owner override only if its behavior is fully documented and tested.

**TerraFirma / TerraFirmaGreg:** use separate valid profiles. Preserve their material units, heat, progression, native recipes, and existing quality effects. Do not equate TFC/GregTech fluid units to Immersive Smithing nugget units. Keep their owned equipment routes native by default; test safe coexistence, display, and state preservation. Routing their entire metallurgy into Immersive Smithing is a separate overhaul, not a prerequisite for coexistence compatibility. Label that boundary honestly.

## 9. Phase E — Recycling and economy safety

Compute recycling from **recoverable material content**, not automatically from the metal added in the last upgrade. A steel item plated in gold is not a solid gold item; an upgraded steel chestplate contains its base plus added metal, not just its latest addition.

Where a finished item contains multiple metals, keep the existing single-family forge model. Use a documented primary-metal recovery rule and forfeit minor plating, or a specific supported dismantling route; do not invent alloy conversion or return extra materials without tracking them.

For outputs obtainable by several routes, recover no more than the lowest proven compatible material content unless server-authored provenance safely identifies the actual route. Cover native-crafted, forged, traded, and looted copies. Never trust client-supplied value NBT. Recompute or validate provenance after recipe changes.

Fix precedence deliberately: the inspected `SmithingData` inserts explicit smithing-recipe salvage before datapack recycling overrides using `putIfAbsent`. Ensure a documented explicit recycling override can actually override inferred content, and let `recyclable: false` win over all generation. Add a test for this rather than relying on the word “override.”

Test every cycle involving components, upgrades, darkening/gilding, visor swaps, repair, and recycling. For same-family production/recovery cycles, recovered metal must not exceed paid metal at the configured efficiency. Preserve intentional upstream alloy/smelting recipes rather than falsely treating every material conversion as an exploit.

## 10. Configuration, UI, reload, and performance

Use the existing server configuration conventions. Add only useful controls: master integration enablement, per-addon enablement/exclusions, source recipe overrides, external recipe ownership, and targeted ranged/horse-armor behavior if needed. Ensure a disabled integration also cannot silently fall through to dangerous generic suppression.

Preserve `replaceMetalEquipmentRecipes`, alongside-crafting behavior, auto-detection settings, material blacklists, recipe blacklists, and existing unrelated integrations. Explain the relationship between global settings and addon policies. Client options must never decide server costs, recipes, or quality.

Extend existing `/immersivesmithing report`, `recipe`, and `material` diagnostics. Include:

- Loaded and recognized addon versions; supported versus unknown versions.
- Counts by role/disposition and exact uncovered IDs.
- Source recipes, operation policy, cost derivation, consumed base, preserved template, and suppression reason.
- Component ambiguity, missing dependencies, conflicts, intentionally native routes, and external workflow ownership.
- Recycling source, override precedence, and any lossy rounding.

JEI must show the actual forge cost and all required inputs, distinguish “new item” from “upgrade this item,” and keep native cosmetic/assembly recipes visible. Display real alternative ingredients, not only an arbitrary representative when that would mislead. Show where open helmets and unique gear come from. Add a short in-game guide chapter and translated tooltips/messages explaining these boundaries in plain language.

On `/reload`, rebuild component graphs, policies, recipes, suppression, recycling, and client presentation from one versioned snapshot. Cancel or safely pin in-flight sessions so an old recipe cannot consume new costs or lose its reserved base. Do not partially mutate live recipe state while validation is still running.

Perform inventory and graph work only at startup/reload. Gameplay checks should use indexed lookups. Avoid scanning every recipe/item each tick, repeated reflection in damage paths, per-projectile whole-inventory searches, and one warning per item per frame. Measure reload time and snapshot sizes against a representative addon stack.

## 11. Suggested implementation map

These are existing paths in the inspected Immersive Smithing source; adapt to the current branch rather than duplicating renamed classes.

| Area | Starting points | Expected change |
|---|---|---|
| Recipe analysis and policies | `recipe/AutoRecipeDetector.java`, `recipe/SmithingData.java` | Operation-aware generation, exact suppression mapping, dependency order, alternate routes, reload correctness |
| Material/component accounting | `material/MaterialRegistry.java`, `MaterialDefinition.java`, `MaterialResolver.java`, recycling loaders | Verified component metadata, rational analysis, canonical families, safe recovery |
| Upgrade result preservation | `recipe/AuxInventory.java`, `AuxiliaryIngredient.java`, `minigame/SessionManager.java`, `workpiece/WorkpieceCodec.java`, `block/SmithsTroughBlock.java` | Typed escrow/base selection, input-derived target, transfer policy, serialization and refund safety |
| Item roles and quality | `minigame/EquipmentClassifier.java`, `quality/QualityHooks.java`, `ProjectileEfficacy.java`, `CommonEvents.java` | Blocking-weapon fix, explicit ranged/horse roles, single-application efficacy, transformation preservation |
| Optional integrations | New `compat/epicknights/` and narrow addon adapters | Version/capability guards, metadata-preserving transforms, firearm-specific bridges where required |
| Public integration API | Existing interfaces under `api/` | Extend only when current hooks cannot express role/policy; keep existing callers compatible |
| Data and presentation | `data/immersive_smithing/recipes/compat/`, material/recycling/pattern folders, guide/lang, JEI category | Guarded mappings, complete inputs, diagnostics and understandable guide content |
| Tests and dependency profiles | `src/gameTest`, `src/packTest`, `build.gradle`, `gradle.properties`, `tools/packtest/` | Real-mod fixtures, isolated combinations, coverage/economy assertions, visual and multiplayer checks |

Use optional dependencies and lazy/guarded class loading. A common class must not reference an absent addon in eagerly resolved fields or signatures. Optional mixins, if necessary, must be selected by verified target/version availability and tested with the addon absent. A silent no-op injection is not evidence that support works.

Guard optional JSON resources and tags using the supported Forge 1.20.1 mechanisms. Missing optional item tags should use optional entries where appropriate. Validate network serializers for any added policy/target metadata and update protocol/version handling when the wire format changes. Preserve old saved workpieces with defined decoding defaults.

## 12. Verification matrix and acceptance tests

### 12.1 Required installation profiles

Run at least:

1. Immersive Smithing alone, dedicated server and client.
2. Immersive Smithing + Epic Knights + its required dependencies.
3. Each listed content addon independently, with its real dependency closure.
4. A supported combined armor stack: Addon, Slavic, Antique, Dark Ages, and compatible Japanese Armory.
5. Materials alone, together, and with another steel/tin/silver provider.
6. Ice and Fire integration, with each separately claimed upstream/CE combination.
7. Each ranged addon separately; a valid combined ranged stack where upstream supports it.
8. Open Helms + Swappable Helms + FoxRall's Visors, with and without the main Addon where valid.
9. Seven Kingdoms with its released unique-loot behavior.
10. Better Combat; Better Combat plus Rework; Epic Fight, as separate profiles.
11. Overgeared bridge, TerraFirma bridge, and TerraFirmaGreg bridge, each in its own valid environment.
12. Datapack overrides, recipe removal/config-disabled content, repeated reload, save/reopen, and late client join.

Do not claim support for a profile that cannot launch without Immersive Smithing. Record upstream baseline failures separately from integration regressions.

### 12.2 Behavioral gates

| Test | Pass condition |
|---|---|
| Inventory coverage | Every target registered item and relevant route is accounted for; no unexplained skipped equipment |
| Blocking sword | Low/50/high quality changes actual melee damage correctly; blocking remains available; no shield exemption |
| Two-handed weapon / lance | Native penalties, reach, speed, mounted attacks, and special behavior remain correct |
| Component arithmetic | Ring, plate, mail and chestplate fixtures match exact upstream batch arithmetic; no rounding-profit loop |
| Same-metal upgrade | Shortsword-plus-ingot and halfarmor-plus-plates preserve the correct base and full added costs |
| Long upgrade chain | Coverage and costs are independent of recipe enumeration/order |
| Gilding/darkening | Native base, addition, template, display, mark, wear, and decoration state are preserved |
| Multiple output routes | No unrelated route disappears; distinct costs/NBT variants remain valid |
| Cancellation/refund | Full decorated/enchanted/damaged base returns exactly once; completion consumes it exactly once |
| Native transformations | Dye, heraldry, attachment/removal, trims, repair, rename, and allowed upgrades preserve intended state |
| Visor loop | Repeated swaps cannot repair, reroll quality, change maker, duplicate enchantments, or lose decorations |
| Pavise lifecycle | Placement, unload/reload, pickup, and block break preserve the complete item state |
| Horse armor | Native equip/render/protection work; any claimed quality effect is verified on the horse |
| Real shields | Block limits/cooldowns unchanged by default; durability quality applies without melee misclassification |
| Arrow/custom projectile | Actual source captured at launch; one efficacy application; state survives recovery and reload |
| Hitscan firearm | Thirty Years War shot damage changes at the intended boundary; no change to ammo/reload/penetration rules |
| Loaded gun | Reforge/refine/cancel/melt policies cannot duplicate ammunition or erase it unexpectedly |
| Ice and Fire | Metal items forge; bone/chitin remain native; special damage/effects execute once |
| Unique loot | No new craft/recycle route bypasses limited-spawn restrictions or duplicates unique gear |
| Recycling | Correct family/content, overrides work, alternate routes safe, efficiency cap honored |
| Pack removal | A deliberately removed recipe stays removed; upstream disabled items are not resurrected |
| Reload idempotence | Repeated reload/rebuild produces identical recipes, suppression, and counts |
| Multiplayer | Two users cannot share/steal/duplicate a reserved upgrade; client previews match server validation |
| Optional absence | Each absent addon leaves no missing-class, missing-registry, mixin, or recipe-load errors |
| External workflow | No bypass recipes or duplicate quality effects; native Overgeared/TFC/Greg progression remains intact |
| Existing integrations | Vanilla, Spartan Weaponry/Spartan Shields, and other established compatibility still pass |

Use real runtime recipes and actual item stacks for substantive tests. A test that merely asserts a JSON file exists is insufficient. Use controlled quality fixtures for arithmetic/damage assertions; test the player-driven workflow separately. Include crafted, looted, traded, enchanted, damaged, renamed, decorated, and previously ungraded equipment.

The inspected repository already provides these commands:

```bash
./gradlew compileJava
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer -PcompatMods
./gradlew runClient -PsmokeTest
```

The existing `-PcompatMods` profile contains Spartan Weaponry, Spartan Shields, and Immersive Armors; **it does not currently mean an Epic Knights test suite**. Add documented, pinned Epic Knights profiles/tasks rather than claiming that flag proves this integration.

The inspected `CLAUDE.md` also requires the isolated Ultima pack release gate:

```bash
./gradlew build packTestJar
python3 tools/packtest/ultima_pack_test.py --expect spartan,shields,upgrades
```

Extend that harness with relevant Epic Knights expectations after implementing them. Run the required release gate in an isolated copy of the pack, never against the user's live world. If a required external instance/runtime is unavailable, document the exact unrun gate and do not label it passed.

## 13. Implementation order and final deliverables

Implement in this order:

1. Reconcile source/release versions, pin test profiles, and produce the full inventory.
2. Fix blocking-weapon classification and add exact source-recipe policies/suppression.
3. Implement component arithmetic and safe base-plus-addition chains.
4. Implement complete stack transfer and escrow safety for structural upgrades.
5. Cover the core equipment addons and material suppliers.
6. Verify cosmetic transforms, visors, pavises, horse armor, rare items, and recycling.
7. Add and verify ranged/hitscan adapters and combat-mod interactions.
8. Implement alternative-workflow coexistence and pack overrides.
9. Finish JEI/guide/reporting, run the complete test matrix, and publish precise coverage documentation.

Deliver:

- Production code and guarded data in the existing project.
- Reproducible inventory tooling and the complete per-item/per-route coverage manifest.
- Exact tested mod versions/hashes, supported combinations, and any upstream limitations.
- Automated test results, multiplayer checks, and representative client screenshots for forge selection, upgrades, JEI, long weapons, decorated armor, shields, and firearms.
- Updated `README.md`, `CURSEFORGE.md`, `CHANGELOG.md`, `docs/API.md` where changed, and the in-game guide/translations.
- A concise change report stating what now works, which native routes remain native, and any unverified cases.

Do not claim “all addons supported” from automatic detection alone. Completion requires zero unexplained gaps for the declared versions and clear separation between full manufacturing integration, intentional native processes, and external-workflow coexistence.

## 14. Source references and reading pointers

Project links in the inventory are the primary references for addon scope and candidate releases. Useful implementation references follow.

### Immersive Smithing source

- [AutoRecipeDetector](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/recipe/AutoRecipeDetector.java): crafting, upgrade and chain analysis.
- [SmithingData](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/recipe/SmithingData.java): rebuilding, suppression, provider handling and recycling.
- [EquipmentClassifier](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/minigame/EquipmentClassifier.java), [QualityHooks](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/QualityHooks.java), and [ProjectileEfficacy](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/ProjectileEfficacy.java): role and efficacy limits.
- [SessionManager](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/minigame/SessionManager.java) and [CommonEvents](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/event/CommonEvents.java): escrow/target creation and existing inheritance.
- [API documentation](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/docs/API.md): current recipe, material, recycling, quality, tag and extension formats.

### Upstream implementations

- [Epic Knights Forge weapon behavior](https://github.com/Magistu/Epic-Knights/blob/aa772b7d156f6bd7e9ab58c8dbb7b660d70fdaf3/forge/src/main/java/com/magistuarmory/forge/item/MedievalWeaponItemForge.java).
- [Epic Knights Forge recipes](https://github.com/Magistu/Epic-Knights/tree/aa772b7d156f6bd7e9ab58c8dbb7b660d70fdaf3/forge/src/main/resources/data/magistuarmory/recipes): inspect `steel_ring`, `small_steel_plate`, `steel_plate`, `steel_chainmail`, `chainmail_chestplate`, `knight_chestplate`, `steel_bastardsword`, and `maximilan_chestplate_smithing`.
- [Main Addon Forge recipes](https://github.com/Magistu/Epic-Knights-Addon/tree/1ca1e20661f0c82e3b2417ab4babc705215c7e80/forge/src/main/resources/data/magistuarmoryaddon/recipes): gilding, darkening, alternate routes, and equipment construction.
- [Epic Knights custom crafting](https://github.com/Magistu/Epic-Knights/tree/aa772b7d156f6bd7e9ab58c8dbb7b660d70fdaf3/common/src/main/java/com/magistuarmory/item/crafting) and [PaviseBlockEntity](https://github.com/Magistu/Epic-Knights/blob/aa772b7d156f6bd7e9ab58c8dbb7b660d70fdaf3/common/src/main/java/com/magistuarmory/block/PaviseBlockEntity.java).
- [Thirty Years War GunItem](https://github.com/Magistu/Epic-Knights-Thirty-Years-War/blob/22c2a6bcd18b214e9d866894a0393088327e2b61/common/src/main/java/com/magistuarmory/thirtyyearswar/item/GunItem.java): direct raycast damage and firearm state.
- [Ice and Fire weapon implementations](https://github.com/Magistu/Epic-Knights-Ice-And-Fire/tree/7e964255769c1f34df193e8914c8dd24eceef17e/src/main/java/com/magistuarmory/iceandfire/item): metal versus organic families and special effects.
- [Seven Kingdoms unique-loot code](https://github.com/WitchkingsRu/EpicKnightsOfTheSevenKingdoms/tree/e8f57bc4102c3b84e10cd91dc83c60c5d4d52c21/common/src/main/java/net/ekotsk/loot): source-head reference; reconcile with the selected released jar.
- [Dark Ages 1.1 file metadata](https://www.curseforge.com/minecraft/mc-mods/epic-knights-dark-ages-addon/files/7107406): explicit Forge 1.20.1 support.
- [Forge 1.20.1 conditional data](https://docs.minecraftforge.net/en/1.20.1/resources/server/conditional/) and [custom recipes](https://docs.minecraftforge.net/en/1.20.1/resources/server/recipes/custom/): optional data, assembly on copies, and serializer/network responsibilities.

Use upstream files to understand behavior and create compatibility mappings. Keep third-party jars and artwork out of the distributed Immersive Smithing artifact; depend on installed originals and preserve their attribution.
