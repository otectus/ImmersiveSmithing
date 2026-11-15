# Immersive Smithing update plan: casting, shared server config, selective exclusions, netherite

## 1. Baseline and findings

**Checkout.** `/home/otectus/Projects/ImmersiveSmithing`, branch `main`, HEAD `0c5e781f53641a5cc9dcab72acde2a50e55a6fd3`. HEAD equals the pinned commit, but the working tree is far ahead of it and uncommitted.

| Item | HEAD (pinned) | Working tree (plan target) |
| --- | --- | --- |
| `mod_version` | 1.0.0 | 1.2.0 |
| Minecraft / Forge / Java | 1.20.1 / 47.4.23 / 17 | same |
| JEI | 15.20.0.135 | same |
| `ModNetwork.VERSION` | "2" | "3" |
| Uncommitted paths | | 188 (90 modified, 3 deleted, ~95 untracked) |
| Last recorded test run | | 86 GameTests pass in 18 mod profiles (`docs/compatibility/validation/gametests-1.2.0.txt`) |

The tree contains the 1.1.1 and 1.2.0 work described in `CHANGELOG.md`. This plan targets the working tree. Newer work that supersedes handoff assumptions:

- **Suppression is per source recipe.** A native recipe is removed only when a forge recipe was generated from it or makes the same item from the same metal, and `replaces` / `derived_from` fields pin it for bundled data (`SmithingData.java:229-304`, `384-416`; `SmithingRecipeSerializer.java:53-63`). Everything else is kept with a reason.
- **Rebuilds are already repeatable.** `SmithingData.sourceRecipes` keeps the datapack's recipe list and recognises its own output by identity (`SmithingData.java:359-382`), pinned by `RecipePipelineGameTests.rebuildIsRepeatableAndKeepsOriginals`. The handoff's "rebuilding from an already filtered manager" risk is solved; the plan must preserve it.
- **Paid-metal provenance exists.** `RecyclingData` records `RecyclingFamily`/`RecyclingUnits` at the quench (`SmithsTroughBlock.java:184`), caps recycling (`RecyclingData.java:29-36`) and survives crafting repairs. Smithing-table upgrades are not covered (see section 2A, economy).
- **Custom recipe classes are never adapted** (`SourceRecipes`), integrations have switches and curated namespaces, and `Scope.fromConfig` merges `modBlacklist` with disabled integrations (`AutoRecipeDetector.java:82-87`).
- **Config tests swap the spec for an in-memory copy** because Forge's file watcher reloads the world file on another thread (`TestSupport.java:30-46`).
- Station messages are `StationNotices` over `StationNoticePacket`; the forge screen is the vanilla-look `ForgeMinigameScreen`.

**Verified current behaviour relevant to the three requests.**

- Forge storage is a family id, unmelted deposits and an integer molten balance (`SmithsForgeBlockEntity.java:73-77`). Molten metal leaves only through `completeSession` (`:442-449`) or is lost in `dropContents` (`:477-490`); breaking is blocked while molten (`CommonEvents.java:141-147`); `insertMetal` is refused while a session holds the lock (`:192`). `isReady` means molten above zero and no deposits (`:142`).
- The selection screen opens only when at least one recipe is affordable, has its components and passes gates (`SessionManager.java:91-98`, `ForgeRecipeSelection.java:25-32`). Unaffordable recipes are silently absent; `whyNone` only speaks when nothing at all is affordable.
- `MaterialFamily.ingotItems()` is "any 9-unit source", which includes raw ore (`iron.json` lists `forge:raw_materials/iron` at 9). It is a display helper, not an ingot resolver. Thirteen bundled material files list sources by item rather than tag (Cataclysm, Ice and Fire dragonsteel, the Epic Knights plates), so a correct ingot resolver cannot rely on `forge:ingots` tags alone.
- Config is Forge `SERVER` type (`ImmersiveSmithing.java:49`); all 27 consumer files read through `ServerConfig.get(...)` helpers, which fall back to defaults while unbound; no direct `ConfigValue.get()` calls exist; no `ModConfigEvent` subscriber exists. Client-reachable consumers: `SmithingTier` (tooltips), `QualityCalculator`/`QualityHooks` (tooltips and attribute modifiers), `SmithsForgeRenderer` (pool height from capacity), `MakersMarkScreen` (lengths), `SmithingJadePlugin` (grindstone line).
- Forge 47.4.23 facts, read from the local sources and class files: SERVER configs load in `ServerLifecycleHooks.handleServerAboutToStart` from `<world>/serverconfig/` before `ServerAboutToStartEvent` is posted, and unload after `ServerStoppedEvent`; `ConfigSync.syncConfigs` sends the bytes of each SERVER config's file on disk to joining remote clients, who apply them with `ModConfig.acceptSyncedConfig`; nothing on the client unloads synced values on disconnect; `ServerAboutToStartEvent` is not cancelable; `ConfigFileTypeHandler` copies `defaultconfigs/` only when the world file is missing and installs a file watcher that reloads and fires `ModConfigEvent.Reloading` on a watcher thread.
- `recipes.recipeBlacklist` holds native source ids and both skips generation and protects the source from suppression (`AutoRecipeDetector.java:89-91`, `:294`, `:402`, `:543`; `SmithingData.java:243`). It never matches generated ids, which are built as `immersive_smithing:auto/<item namespace>/<item path>` (`:309`, `:472`, `:595`). `modBlacklist` matches the result item's namespace (`:93-95`). `non_smithable_equipment` deactivates explicit recipes too (`SmithingData.java:186`).
- Diagnostics: `/immersivesmithing recipe` prints smithing recipes and the natives they disabled (`SmithingCommands.java:89-113`); the report lists kept natives with reasons but no config selector per item. Silent skips: non-candidate results (`AutoRecipeDetector.java:290`, `:399`), upgrades whose addition is no known metal (`:429`), natives of items with no forge recipe (`SmithingData.java:251-252`).
- Netherite: the four armor JSONs cost 45/72/63/36 units, have no conditions, use the standard forge pattern and slot anvil patterns; the family needs `immersive_smithing:netherite_fuels` and no ignition. Vanilla upgrades are suppressed by `replacingUpgrade` (explicit netherite recipe, `shape` policy); `DataGameTests.conventionalRecipesReplaced` counts 9 disabled upgrades.

**Confirmed gaps.**

1. No way to recover molten metal as ingots. `molten_cannot_be_removed` is the only answer.
2. No shared configuration source; the README documents per-world files.
3. No selector for generated ids, output items or tags; the report cannot tell a pack author which selector applies.
4. Unaffordable recipes are hidden without explanation unless nothing is affordable.
5. A diamond-based smithing-table upgrade that a pack keeps (upgrade suppression off, or a custom upgrade class) produces a piece without a paid-metal record, so it recycles at full shape value. Today that is bounded by needing equipment outputs; with casting it would become a direct ingot multiplier (1 netherite ingot in, 5 out through a helmet).
6. Remote clients keep the last synced server config after disconnect (Forge behaviour).

**Unresolved reproduction questions (netherite).** No runtime reproduction was attempted in this pass. The report cannot be confirmed from source. Section 2D lists the hypotheses and the minimum details to ask the reporter.

## 2. Decisions

### 2A. Casting (molten metal to ingots)

- **Interaction.** Empty Smithing Tongs on a ready forge keep opening the forge screen, but the screen now opens whenever the forge is ready and holds metal, even when no equipment recipe is eligible. The selection stage gains a **Cast** panel above the recipe list: output item, a quantity stepper from 1 to the maximum, an "All" shortcut, an exact preview ("Cast 3 × Iron Ingot, leaves 2 ingots + 4 nuggets") and a Cast button. When the family has valid nuggets a nugget row is offered for the remainder. Discovery: the guide chapter, the forge's JEI info text, the Jade line "Castable: N ingots", and the `forge_status_ready` message gains "or cast ingots".
- **No minigame, no cooling interaction.** Casting is one instant, validated server operation. Rationale: ingots carry no quality, Maker's Mark, XP, advancement or `ItemSmithedEvent`, so a minigame would reward nothing and a stack would take 64 rounds. Feedback is a new sound `block.smiths_forge.cast` mapped in `sounds.json` to vanilla lava extinguish, plus the existing `StationEffects.STEAM`. Tongs wear one point per cast action through `SmithingToolItem.wearWithoutBreaking`, matching the forge completion rule.
- **Heat and deposits.** Casting requires `isReady()`: molten metal and no unmelted deposits. With deposits present the existing `still_melting` / `needs_heat` messages apply, so the melt tick cannot change the balance under a cast. No fuel is consumed.
- **Conservation.** `remaining = previous − 9 × ingotsDelivered` (1 per nugget). The deliverable count is `min(requested, molten ÷ unitsEach, inventoryRoom)`. Room is computed over the 36 main inventory slots exactly as `Inventory.add` fills them (empty slots at the item's max stack size, plus partial stacks of the same item). Zero room rejects with `cast_inventory_full` and changes nothing. After `Inventory.add`, any remainder (defensive only) is credited back before the reply. Nothing is dropped on the ground and no output buffer exists, so save/load and removal are unchanged.
- **Remainder.** Units below one ingot stay molten with the family set. The player can cast nuggets where the family has a `forge:nuggets/*` source, add more of the same metal and melt again, or forge something small. The family is cleared only when `isEmpty()` (molten and deposits both zero). Copper and netherite get no nugget row because their nugget tags are empty in vanilla.
- **Not a `SmithingRecipe`.** Casting is a material-registry operation. Treating an ingot as a smithing recipe would inherit quality, XP, workpieces, patterns, suppression and JEI semantics. Consequences: no serializer or recipe-type change, no recipe sync, JEI gets an info line now (an optional casting category can come later), Jade gets one line.
- **Output resolution.** New `CastOutputs` on `MaterialFamily`, computed at rebuild:
  1. Ingot candidates: sources worth exactly 9 units whose rule is a `#forge:ingots/...` tag or whose item is in `forge:ingots`. Raw ore, plates and other 9-unit items never qualify. Nugget candidates: 1-unit sources from `#forge:nuggets/...` or `forge:nuggets`.
  2. Datapack override in the material file: `"casting": { "ingot": "mod:item", "nugget": "mod:item" }`, or `"casting": false` to disable casting for that family. An override item must be a source of the family with exactly 9 (ingot) or 1 (nugget) units; otherwise it is reported and ignored.
  3. Ordering when several candidates exist: override first, then the order of `casting.preferredNamespaces` (default `["minecraft"]`), then the family id's namespace, then the registry id string. Never registry iteration order.
  4. Every valid candidate is offered as a row (preferred first); the chosen item is re-validated against the current registry at execution. A missing, ambiguous or changed output refuses the cast with `cast_output_unavailable` and debits nothing. No substitution across families.
  5. The pool keeps family identity only. The plan does not add provenance of the originating mod's ingot; the documentation says so.
  Bundled item-listed families (Cataclysm's five metals, three dragonsteels) get explicit `casting.ingot` entries in their material files so casting works for them; plates (`sources_only`) correctly get nothing.
- **Config.** New section `casting`: `enabled` (true), `allowNuggets` (true), `preferredNamespaces` (`["minecraft"]`). Casting ignores `autoDetectEquipment`, every blacklist and the exclusion tags.
- **Multiplayer correctness.** Casting lives inside the forge session's selection stage, where the forge is already locked for the owner (`lockForSelection`) and hoppers are refused. The client sends `(sessionId, sequence, outputItem, count)`; the server re-validates session, player, dimension, distance, tool, forge presence and readiness, output, count and current data. `sequence` must exceed the last accepted one (duplicate or replayed packets are ignored, as `anvilStrike` does). Casting while the forge is empty of molten metal ends the session cleanly (`endSession`, family cleared by `castUnits`). Disconnect, unload, death and station removal reuse the existing cancel paths; a cast is atomic within one server tick, so a restart can never lose or duplicate metal. Escrow is untouched because casting happens before `beginSession`.
- **Economy audit.** Casting applies no efficiency; `recyclingEfficiency` still applies on insertion. Melt → cast of 64 ingots is exact at efficiency 1.0 and lossy below it. Explicit `addition`-policy recipes (ignitium) already record and recycle for their one ingot (`SmithingData.java:325`), so the consumed netherite helmet is not refunded. The gap is smithing-table upgrades that packs keep: add `mixin/SmithingMenuMixin` (`onTake` head, `require = 0`) calling a new `RecyclingData.recordUpgrade(result, base, addition)` that records `min(normalValue, baseUnitsInFamily + additionUnitsInFamily)`. A diamond helmet plus one netherite ingot records 9 netherite units; a recorded netherite base plus an ingot records base + 9, capped at the shape value. Loot pieces without records keep their current value, as today.

### 2B. Shared authoritative server configuration

- **Policy.** The presence of `config/immersive_smithing-server.toml` (Forge's `FMLPaths.CONFIGDIR`) selects **shared mode** for every world of the installation. No file means **world mode**, exactly Forge's current per-world behaviour, including the `defaultconfigs/` template copy for new worlds. Both files present: the shared file wins; world files are never written by the mod and become active again when the shared file is removed. No extra source-mode key is needed; the file is the switch, and it is read once at server start.
- **Architecture: keep Forge SERVER registration, overlay the effective source, own the sync.** Registration stays `ModConfig.Type.SERVER` so world mode keeps Forge's loading, correction and watcher unchanged. At `ServerAboutToStartEvent` (verified to fire after Forge bound the world file) a new `ServerConfigSource` opens the shared file as a `CommentedFileConfig` (sync, insertion order, autosave, replace writing mode, **no file watcher**), loads it, and binds it with `ServerConfig.SPEC.setConfig(...)`, which validates, corrects, writes missing keys with comments and resets value caches. Forge's `ModConfig` still points at the world file, which is why the mod must sync itself: Forge's handshake sends the world file's bytes. A new `ConfigSyncPacket` carries the effective TOML text (serialised with NightConfig's `TomlWriter`, the pattern already used in `TestSupport`) to every remote client on login and after every rebuild, in both modes; the client applies it with `ServerConfig.SPEC.setConfig(parsed)` on the main thread, the same call Forge's own sync makes. The integrated-server host shares the JVM and the spec and receives nothing. LAN guests and dedicated-server players are remote connections and receive it. On `ClientPlayerNetworkEvent.LoggingOut` a remote client clears the spec (`setConfig(null)`), so no stale values survive into the next server or world. `ServerStoppedEvent` resets the source; Forge's own unload then nulls the spec, which already covers switching integrated worlds in one process.
- **Why not `COMMON`.** A `COMMON` registration would create `config/immersive_smithing-server.toml` on first launch and silently override customised worlds, and it would still need custom sync. Rejected.
- **Invalid shared file.** Parse failure at startup: a dedicated server refuses to start with a log line naming the path, line and error (an exception from the event handler, since the event cannot be cancelled). The integrated server falls back loudly to the world file: ERROR log, a red notice to the host on join, and `/immersivesmithing config` reporting `SHARED_INVALID`. Never a silent switch. This split is the one product choice flagged in section 8.
- **Reload and restart.** Switching mode (creating or deleting the shared file) requires a world or server restart. Value edits: `/immersivesmithing reload` first re-reads the active source on the server thread, validating a fresh parse before anything is swapped; a parse error keeps the last-known-good config, reports the path and error to the operator and aborts the reload with no datapack reload. On success it binds the new config atomically (one reference swap plus `afterReload`), then runs the existing datapack reload; the rebuild in `onDatapackSync(null)` uses the new values, and the same handler sends `ConfigSyncPacket` to all remote players after the recipe packets. In world mode Forge's watcher still applies live file edits on its own thread, as today; the mod subscribes to `ModConfigEvent.Reloading` only to log that `/immersivesmithing reload` applies recipe-affecting changes. Active sessions keep their captured timings; a selection-stage session that selects a recipe the rebuild removed is already cancelled with `recipe_unavailable`; cast options are revalidated per request.
- **Visibility.** Startup logs `Immersive Smithing config: source=SHARED path=... (world file ... inactive)` or `source=WORLD path=...`. New `/immersivesmithing config` prints mode, path, last error and whether a shared file exists.
- **Migration examples.** Existing world: copy `saves/<World>/serverconfig/immersive_smithing-server.toml` to `config/`, restart. Several worlds with different files: pick one to copy, or stay in world mode. New installation: nothing to do; world mode. Revert: delete or rename the shared file, restart; the untouched world files apply again. Dedicated server: `config/` beside `world/serverconfig/`.

### 2C. Selective exclusions

Existing keys keep their semantics; comments are clarified. Two keys are added.

| Intent | Key | Behaviour |
| --- | --- | --- |
| Ignore one native recipe | `recipes.recipeBlacklist` (existing) | Native crafting or smithing-table ids. Not generated from and never disabled. Another allowed source for the same item may still generate; the blacklisted one stays. |
| Disable one smithing recipe by id | `recipes.smithingRecipeBlacklist` (new) | Exact `immersive_smithing:smithing` ids, explicit (`immersive_smithing:smithing/netherite_helmet`, `immersive_smithing:compat/...`, pack ids) or generated (`immersive_smithing:auto/<ns>/<item>`). Removed before suppression, so natives it would have replaced stay. |
| Exclude an item's automatic conversions | `recipes.autoDetectExclusions` (new) | Item ids and `#tag` ids. No automatic recipe for that result in the crafting, upgrade or chain pass. Natives stay. Explicit recipes unaffected. |
| Exclude a tagged group | same key, `#namespace:tag` entries | Resolved after tags bind at every rebuild. |
| Exclude a mod | `recipes.modBlacklist` (existing) | Matches the result item's namespace: the mod's items are neither detected nor disabled, even through recipes from other namespaces; explicit datapack recipes for its items still apply. |

Rules kept distinct: `non_smithable_equipment` still removes explicit recipes too (contract unchanged, documented as the "all smithing" switch). An automatic-only exclusion leaves the conventional route; if an explicit recipe for the same item exists the exclusion is moot and the report says so, with the native recipe's fate. Material-family exclusions remain the only material-level switch. Excluding forging never disables melting: recycling values from crafting analysis stay unless `non_recyclable` or a `recyclable: false` override says otherwise.

Consistency across passes: one `ConfigSelectors` object is built per rebuild and consulted in `generate`, `generateUpgrades` and `pieceRecipe` before any recipe is created, and once more on the computed generated id. An excluded piece that a chain needs is reported as "reworks a piece the forge does not make", so excluded outputs are never reintroduced. Deterministic precedence is unchanged: sources sorted by id, first OK analysis wins, passes in the order crafting → upgrades → chains, explicit always first. Entries that fail to parse are warned once; unknown items or empty tags are notes, never fatal, because optional packs make them normal.

Diagnostics: a per-item `RecipeDecisions` record (source recipe id, generated id, output item, type/serializer, explicit versus automatic, outcome, reason, matching selector) populated during the rebuild. `/immersivesmithing recipe <item>` prints it; `report` gains "Configuration selectors" (each entry with its match count) and "Exclusions applied"; the catalog JSON gains `origin` and `selectors`. The silent skips listed in section 1 become report lines.

### 2D. Netherite investigation

Hypotheses, ranked by what the code supports:

1. **Affordability filtering.** A forge holding fewer than 36 units shows netherite weapons but no armour and gives no message. Fix: send every family recipe with an `affordable` flag and shortfall; draw unaffordable rows dimmed with "needs N more"; the server already refuses selection, but selecting a dimmed row should answer with a notice instead of cancelling the session.
2. **A pack removed the explicit recipes by output** (KubeJS `remove({output})` style filters match `result.item` of unknown recipe types). Symptom: `recipe minecraft:netherite_helmet` reports nothing and the log has no "inactive" line. If the vanilla upgrade also went, no automatic route exists either.
3. **Exclusion by tag, API predicate or `materialFamilyBlacklist`.** Symptom: "Inactive smithing recipes: result is excluded from smithing" or "unknown material family".
4. **Modded netherite armour:** custom upgrade class or serializer (kept, reported), item failing candidacy (currently silent), addition not a known metal (currently silent), disabled integration or curated namespace without bundled data, `autoDetectEquipment` off (upgrade not redirected, native route kept).
5. **Stale client presentation.** The rebuild runs before the recipe packet on reload and login; JEI reloads on recipe updates. Least likely; verify with the pack-test's client-versus-server recipe count check.

Nothing here changes the full-shape costs, the no-template rule, the two upgrade policies or armor trims. Minimum details to request from the reporter: mod list, both config files, the output of `/immersivesmithing recipe minecraft:netherite_helmet`, `logs/immersive_smithing_report.txt`, the forge's molten amount when the list was empty, and whether JEI lists the recipe.

## 3. Change map

Paths are relative to `src/main/java/com/otectus/immersivesmithing/` unless stated.

**Feature A, casting**

| File | Change |
| --- | --- |
| `material/CastOutputs.java` (new) | Candidate resolution, override validation, preference ordering; `record Output(Item, int unitsEach, Kind)`. |
| `material/MaterialFamily.java`, `MaterialDefinition.java`, `MaterialRegistry.java` | `casting` field parse and merge; outputs computed in `Builder.build`; report notes for rejected overrides. |
| `blockentity/SmithsForgeBlockEntity.java` | `castUnits(int)` debit with family clear on empty; `refundCast(int)`; sync and block state update. |
| `minigame/ForgeSession.java`, `SessionManager.java` | `castOptions`, `lastCastSequence`; `castIngots(...)`; `openForge` opens with offers or cast options; session end when emptied. |
| `recipe/ForgeRecipeSelection.java` | `offers(...)` returning all family recipes with units, affordability, shortfall, missing components and denial; `eligible` kept. |
| `util/InventoryRoom.java` (new) | Deliverable count over the main inventory. |
| `network/ModNetwork.java` | `VERSION = "4"`; register `CastIngotsPacket`, `CastResultPacket`, `ConfigSyncPacket`. |
| `network/packet/OpenForgeScreenPacket.java` | Entry flags; `castOptions` list. |
| `network/packet/CastIngotsPacket.java`, `CastResultPacket.java` (new) | Request and result; sequence numbers. |
| `client/screen/ForgeMinigameScreen.java`, `client/ClientPacketHandler.java` | Cast panel, dimmed unaffordable rows, molten counter updates, close on empty. |
| `registry/ModSounds.java`, `assets/.../sounds.json` | `block.smiths_forge.cast`. |
| `compat/jade/SmithingJadePlugin.java` | Castable ingots and nuggets flags and line. |
| `client/compat/jei/ImmersiveSmithingJeiPlugin.java`, lang | Forge and tongs info text mention casting. |
| `config/ServerConfig.java` | `casting` section. |
| `mixin/SmithingMenuMixin.java` (new), `immersive_smithing.mixins.json`, `material/RecyclingData.java` | Paid-metal record for smithing-table upgrades. |
| `data/.../materials/compat/cataclysm/*.json`, `iceandfire/*.json` | `casting.ingot` overrides. |
| `guide/GuideData.java`, `lang/en_us.json` (and the four other lang files for new keys) | Chapter `casting`; messages, screen, Jade and JEI keys. |

**Feature B, shared config**

| File | Change |
| --- | --- |
| `config/ServerConfigSource.java` (new) | Mode detection, shared file binding, reload with last-known-good, effective TOML bytes, status, reset, test binding hook. |
| `event/CommonEvents.java` | `ServerAboutToStartEvent` hook; config sync in `onDatapackSync`; reset on stop. |
| `ImmersiveSmithing.java` | Mod-bus `ModConfigEvent.Reloading` listener (log only). |
| `network/packet/ConfigSyncPacket.java` (new), `client/ClientConfigSync.java` (new) | TOML transport; apply on main thread; clear on remote logout. |
| `command/SmithingCommands.java` | `config` subcommand; `reload` sequence (config first, abort on error). |
| `src/gameTest/.../TestSupport.java` | Isolation and restore through `ServerConfigSource`. |

**Feature C, exclusions and diagnostics**

| File | Change |
| --- | --- |
| `config/ServerConfig.java` | `autoDetectExclusions`, `smithingRecipeBlacklist`; clarified comments. |
| `recipe/ConfigSelectors.java` (new) | Parse, resolve, match counts, problems. |
| `recipe/AutoRecipeDetector.java` | `Scope` carries selectors; checks in all three passes; silent skips reported. |
| `recipe/SmithingData.java` | Explicit-id blacklist in step 2; decisions recorded in every step; `decisionsFor(Item)`. |
| `recipe/RecipeDecisions.java` (new), `recipe/CompatibilityReport.java` | Decision records, new report sections. |
| `recipe/WeaponCatalog.java` | `origin` and `selectors` per entry. |
| `command/SmithingCommands.java`, lang | Decision lines in `recipe <item>`. |

**Netherite**: covered by `ForgeRecipeSelection.offers`, the screen, the silent-skip reports, and tests in section 6.

## 4. Ordered implementation work

0. **Repository hygiene (user's call, see section 8).** Commit or branch the uncommitted 1.2.0 tree before new work so the update is reviewable; set `mod_version` to 1.3.0 in `gradle.properties` at the start.
1. **Config source and sync (B).** `ServerConfigSource`, events, `ConfigSyncPacket`, client apply and clear, `config` subcommand, `reload` sequence, `TestSupport` update. Reviewable alone; compile with `compileJava`; verify with the shared-config GameTests and one dedicated-server smoke run.
2. **Diagnostics infrastructure.** `RecipeDecisions`, report sections, catalog fields, `recipe` command output, silent-skip reports. No behaviour change; depends on nothing.
3. **Exclusions (C).** Config keys, `ConfigSelectors`, `Scope` checks, explicit-id blacklist, reload path from step 1. Depends on steps 1 and 2.
4. **Casting and screen (A, netherite visibility).** Material outputs and overrides, block entity debit, session action, packets and protocol bump, screen panel and dimmed rows, sounds, Jade, JEI text, guide chapter, config section. Depends on step 2 only for report notes.
5. **Recycling provenance mixin.** Independent and small; land it with step 4 so casting never ships without it.
6. **Tests, documentation, release gate.** Section 6 and 7.

## 5. Data and protocol impact

- **Network.** `ModNetwork.VERSION` "3" → "4". Clients and servers must update together; a mismatch is refused at the handshake, as the 1.1.1 bump was. Changed: `OpenForgeScreenPacket` (entry flags, cast options). New: `CastIngotsPacket` (C2S), `CastResultPacket` (S2C), `ConfigSyncPacket` (S2C, TOML bytes).
- **Config.** Additive keys: `casting.*`, `recipes.autoDetectExclusions`, `recipes.smithingRecipeBlacklist`. Forge's correction writes them into existing files with defaults; no renames. The shared file, when present, is corrected and completed the same way. No `defaultconfigs` semantics change.
- **Persisted state.** No block-entity NBT changes (no output buffer), no workpiece or item-tag format changes. `RecyclingFamily`/`RecyclingUnits` gain one more writer (smithing-table takes) with the existing keys.
- **Datapack format.** Optional `casting` object or `false` in material files; everything else unchanged. Recipes of type `immersive_smithing:smithing` are untouched.
- **API and optional mods.** No Java API signature changes. Casting is not a recipe, so KubeJS cannot remove it; `casting.enabled` and the per-family `casting: false` are the switches. JEI: info text now, category optional later. Jade: one line. The mixin uses `require = 0` so a replaced smithing table loses only the recycling cap.
- **Version handling.** Suggested 1.3.0 because of the protocol change; the CHANGELOG states that both sides must update.

## 6. Validation

All GameTests run through `./gradlew runGameTestServer` (plain and `-PcompatMods`; the Epic profiles stay as regression). Server-side tests switch options through `TestSupport.isolateServerConfig` and rebuild with `SmithingData.rebuild(server)` as the existing tests do. No check listed here has been run in this planning pass.

| Area | Automated GameTests (new unless noted) | Expected |
| --- | --- | --- |
| Casting conservation | `CastingGameTests.castsExactlyNinePerIngot`: 64 iron ingots → 576 units → cast all → 64 ingots, 0 left, family cleared, session ended; partial cast of 3 leaves 549; 7 remaining units stay molten and cast as 7 nuggets; a request with 5 units refuses with no change. | exact integers, no drop entities |
| Material compatibility | `castOutputsResolve`: iron, gold, copper, netherite (ingot only, no nugget row for copper and netherite); test family with a tagged ingot; two ingots tagged into one family offered in documented order; `casting.ingot` override honoured; override naming a non-source rejected and noted; `emeraldine` (item-listed, no `forge:ingots`) offers nothing; definition changed between open and cast refuses without debit. | per case |
| Casting lifecycle | full inventory refuses; 20 free slots with a 16-stack item limit (test item) caps delivery; two fake players: second cannot open while locked; replayed sequence ignored; cast during a playing session ignored; chunk unload and logout end the session with no metal change; a reload between open and cast revalidates. | no duplication, no loss |
| Economy | melt → cast loops at efficiency 1.0 and 0.5; smithing-table upgrade of a diamond helmet records 9 netherite units and recycles for 9 (`SmithingMenuMixin`); forged helmet still recycles for 45; `addition`-policy piece recycles for its addition only; no advancement or `ItemSmithedEvent` from casting. | values as stated |
| Shared config | `SharedConfigGameTests` through `ServerConfigSource.bindForTest(path)`: a temp shared file with `forgeCapacityUnits = 99` applies and a second bind with a different temp world file does not; malformed file reports path and error and keeps last-known-good; missing shared file selects world mode; `effectiveToml()` round-trips through the client apply path. | per case |
| Config lifecycle | reload publication order: config bound before rebuild, `decisionsFor` reflects new exclusions after one reload; repeated reloads idempotent (`rebuildIsRepeatableAndKeepsOriginals` extended). | stable sets |
| Exclusions | `ExclusionGameTests` using the test datapack: source id (`immersive_smithing_test:alt_metal_golden_sword` already present) still protected; generated id in `smithingRecipeBlacklist` removes `auto/...` and keeps its native; explicit id in the same list keeps `minecraft:netherite_helmet_smithing`; `autoDetectExclusions` by item and by `#tag` across the crafting, upgrade and chain passes (`chain_shape.json`, `chain_addition.json`); duplicate sources with one excluded; mod blacklist; explicit recipe coexistence reported. | per case |
| Reload consistency | exclude → rebuild → restore → rebuild: manager ids equal the original set; no duplicate generated ids; `recipe`/`report` agree with `suppressedRecipes()`. | equality |
| Netherite | all four vanilla pieces active with 45/72/63/36 and slot patterns; offers with 18 units flag armour unaffordable with the right shortfall and still send them; a synthetic modded `SmithingTransformRecipe` under both policies (existing `lapisine`/`emeraldine` families) generates and suppresses; a custom upgrade subclass is kept; an upgrade with an unknown addition is now reported; 16 trim recipes untouched (existing assertion). | per case |

Client and server checks that need a real run:

- `./gradlew runClient -PsmokeTest`: extend `ClientPackTest` with a cast flow (open with molten iron, cast one ingot, screenshot `packtest_forge_cast.png`, assert inventory count and molten counter) and a dimmed-row screenshot with 18 netherite units.
- Dedicated-server fixture (`MultiplayerPackTestFixture`, `tools/visual-test/multiplayer_visual_test.py`): write a hash of the server's effective TOML to the shared directory; the two clients compare their applied spec after login, after `/immersivesmithing reload` with a changed capacity, and after a disconnect (spec unbound) and reconnect. Run once in world mode and once with a shared file whose values differ from the world file and from the clients' own local files.
- Singleplayer manual: two worlds with different world files plus one shared file: both report `SHARED`; delete the shared file, restart, each world reports its own values.
- Release gate: `./gradlew build packTestJar`, then `python3 tools/packtest/ultima_pack_test.py --expect spartan,shields,upgrades`. Record the summary and evidence folder in `docs/compatibility/validation/`. If the gate cannot run in the implementing environment, say so and do not claim it.

## 7. Player and pack-author documentation

- **Player text.** Guide chapter `casting` (after `reforging`): how to open the screen with empty tongs, the Cast panel, batch and single casts, nuggets, remainders, and that casting gives no quality or mark. Update `smiths_forge` ("Molten metal must be forged into an item" becomes "or cast back into ingots"), `troubleshooting` (empty list now shows dimmed rows with the shortfall) and `netherite` where it describes upgrades. New `message.*`, `screen.immersive_smithing.forge.cast.*`, `jade.*.forge.castable`, `jei.*.info.forge` keys in `en_us.json`; the other four language files get the English fallbacks.
- **README.** "How it works": one sentence on casting. "Configuration": the shared-file policy, the opt-in copy procedure, reversion, the startup log line, `/immersivesmithing config`, the new keys with examples, and the reload-versus-restart table. "Commands": `config`. "Other mods": one sentence on the new exclusion keys.
- **docs/API.md.** Material `casting` field with validation rules; the five exclusion keys with TOML examples and matching rules (exact ids, `#tags`, mod namespaces); what `recipe <item>` now prints; a note that casting is not a recipe and cannot be removed by recipe tools.
- **TOML examples** for the README and API docs:

```toml
[recipes]
    recipeBlacklist = ["minecraft:iron_sword"]
    smithingRecipeBlacklist = ["immersive_smithing:auto/examplemod/steel_dagger", "immersive_smithing:smithing/netherite_helmet"]
    autoDetectExclusions = ["examplemod:steel_dagger", "#examplemod:ceremonial_armor"]
    modBlacklist = ["examplemod"]

[casting]
    enabled = true
    allowNuggets = true
    preferredNamespaces = ["minecraft"]
```

- **CHANGELOG 1.3.0** (concise): cast molten metal back into ingots and nuggets from the forge screen; one shared server configuration file for every world with client sync and a `config` command; `autoDetectExclusions` and `smithingRecipeBlacklist`; `recipe` and `report` show every selector and decision; unaffordable recipes shown with their shortfall; smithing-table upgrades record their paid metal; network protocol 4 (update both sides).
- **CURSEFORGE.md.** One paragraph on casting and one line on the shared config file.
- **MODMAP.md.** Hand-maintained section: decisions on casting as a non-recipe action, the config source overlay and own sync, and the selector precedence.

## 8. Risks and unresolved decisions

Decisions I recommend but which the user may want to overrule:

1. **Malformed shared file on an integrated server:** loud fallback to the world file (dedicated servers refuse to start). The alternative, failing both, crashes a singleplayer client to the title screen. One constant switches it.
2. **Committing the uncommitted 1.2.0 tree first.** Without it the update's diff is unreviewable; the plan assumes a commit or branch happens before step 1.
3. **Version 1.3.0** for the protocol bump, with the "update both sides" notice.

Risks and mitigations:

- Forge's handshake still syncs the world file bytes before our packet. Window is milliseconds and ends before the player can act; documented in `ServerConfigSource`.
- Third-party config GUIs edit Forge's `ModConfig`, meaning the world file; in shared mode those edits do nothing. The startup log and `config` command make the active path visible.
- The casting room calculation covers the vanilla main inventory only; accessory inventories are ignored on purpose, so delivery is conservative.
- `SmithingMenuMixin` targets `onTake(Player, ItemStack)` with slots 1 (base) and 2 (addition), confirmed in the mapped jar; `require = 0` keeps packs with replaced smithing tables working.
- Netherite remains unreproduced; the plan ships the visibility fix and the diagnostics that would identify the other causes rather than a guessed behaviour change.
- `GuideData.CHAPTERS` insertion renumbers later chapters; the guide addresses chapters by name, so only saved page numbers in screenshots change.
