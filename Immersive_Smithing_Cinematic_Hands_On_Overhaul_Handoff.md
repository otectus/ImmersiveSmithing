# Immersive Smithing — Cinematic, Hands-On Overhaul
## Implementation handoff for a coding agent

**Target:** [otectus/ImmersiveSmithing](https://github.com/otectus/ImmersiveSmithing)  
**Reference:** [Pocky-l/handson-delight](https://github.com/Pocky-l/handson-delight) and its [Diegesis](https://github.com/Pocky-l/diegesis) interaction library  
**Research date:** November 13, 2026, America/New_York  
**Scope:** A complete interaction and presentation redesign on the existing Minecraft 1.20.1 / Forge / Java 17 platform.

The request mentions “Immersive Brewing” once, but both target links and the forge/anvil examples identify **Immersive Smithing**. This document therefore targets Smithing. Do not implement this work in the Brewing repository.

## 1. Your assignment

Replace the current screen-based forge and anvil minigames with a coherent smithing process performed on visible objects in the world. Move the camera into a comfortable working position, let the player manipulate the forge and workpiece directly, and make each action visibly change the material. Extend that approach through carrying, quenching, fitting components, optional refinement, and signing.

You may replace minigame rules, station interactions, layouts, assets, and internal architecture. Preserve the value of existing worlds, finished equipment, recipe coverage, material accounting, and public integration contracts wherever possible. Changes to interaction design are intentional; accidental loss of items, quality, or compatibility is unacceptable.

The defining acceptance test is:

> A player can make a sword by operating the forge, drawing out a glowing billet, shaping it with a hammer, quenching it, and fitting its handle while watching those actions happen at the actual workstations.

A transparent version of the old minigame, a world-space progress bar, or a prettier screen does not satisfy this assignment. A book or compact inventory drawer may assist planning; it must not become the main crafting activity.

Implement the complete scope in this handoff. The initial sword demonstration is a foundation, not the final deliverable.

### 1.1 Research boundaries

This handoff is based on source inspection, repository metadata, checked-in screenshots, and a frame from Hands-On Delight’s cutting demonstration. No Minecraft build, interactive playtest, or new performance test was run while preparing it. Existing validation reports describe earlier work and are not evidence that this proposed overhaul works.

The exact source snapshots inspected were:

| Repository | Branch | Inspected commit | Relevant baseline |
|---|---|---|---|
| ImmersiveSmithing | main | `0c5e781f53641a5cc9dcab72acde2a50e55a6fd3` | `mod_version=1.0.0`, Minecraft 1.20.1, Forge 47.4.23, Java 17 |
| handson-delight | main | `54697d517938fa9a5876b4a65ab2a38abf331952` | `mod_version=1.4.1`, main targets Minecraft 1.20.1, Diegesis beta.9 |
| diegesis | main | `7b9b343b49c31bbaf56e2b078668166fb7e5a068` | `mod_version=1.0.0-beta.9`, main targets Minecraft 1.20.1 |

Recheck HEAD before editing. Read the current repository instructions, `CLAUDE.md`, `MODMAP.md`, `docs/API.md`, and relevant tests. Record differences from these snapshots. Use build metadata as the version authority: the historical visual report refers to an older artifact name/version that differs from current `gradle.properties`.

Source references S1–S12, H1–H3 and D1–D3 are linked in section 22. New class names, configuration fields, formulas, and schemas below are **proposed implementation contracts**, not APIs claimed to exist already.

## 2. What exists, and what must change

The baseline already has substantial useful work. Reuse it deliberately.

| Area | Verified current behavior | Overhaul instruction |
|---|---|---|
| Forge interaction | Upper/lower hit position separates metal and fuel. Empty tongs open recipe selection and a timed forge screen. [S2], [S3] | Preserve physical loading; replace work screens with scene controls and visible billet preparation. |
| Anvil interaction | Tongs place/retrieve a workpiece. A hammer opens a shrinking-ring target screen. [S3], [S4] | Aim hammer blows at the actual billet, with deformation and process-specific operations. |
| Recipe planning | Eligible recipes and inventory auxiliaries are resolved server-side. Auxiliaries enter persisted forge escrow when a recipe is selected. [S3], [S10] | Keep validated selection and exact item accounting, but move planning to a small station book and reserve ingredients only at explicit job commitment. |
| Workpieces | `FORGED` and `SHAPED` states; the target stack, family, metal units, scores and pattern are persisted. There is no temperature or progressive shape field in `WorkpieceData`. [S5] | Add persistent process stages, temperature, geometry progress and recovery state. Do not confuse the existing hot appearance with a thermal simulation. |
| Quenching | A use action consumes water, creates the finished result, applies quality and maker information, invokes crafting hooks, and transfers the result. [S6] | Add a visible lowering/immersion/lifting sequence. Move finalization to assembly when a recipe needs post-quench components. |
| Grindstone | A plain `Block`, with a second use confirming an XP purchase of score improvement. [S7] | Add a real held-against-wheel action and station state; retain an explicit cost and optional role. |
| Signing | The default server config opens the signing screen after quenching. [S8] | Default to a small completion cue and optional engraving action. Never interrupt every craft with a text dialog. |
| Visuals | Custom block models, volumetric piles, heated rendering, category silhouettes, particles, and first/third-person poses already exist. [S9] | Build upon these assets; add coherent transformations, tool contact and camera composition. |
| Multiplayer | `SessionManager` owns sessions, locking, sequence/timing validation, cancellation and escrow recovery. [S3] | Separate durable work from temporary player control. Retain and strengthen server authority. |
| Recipes/integrations | Explicit and automatically detected recipes, material definitions, upgrades, recycling, quality hooks, JEI/Jade and public extension interfaces exist. [S10], [S11], [S12] | Reuse their economic and integration rules instead of replacing them with a sword-only crafting system. |

At the inspected commit, the tree contains 40 anvil-pattern JSON files, one forge pattern, 31 material-definition files, and 494 recipe JSON files. These are **source-file counts**, not a promise that all are enabled simultaneously in a particular pack. Produce an actual runtime coverage report after the overhaul.

### 2.1 Lessons from Hands-On Delight

Apply the reference’s underlying ideas:

- A workstation stays visibly in the world while the player works.
- The camera helps frame manipulation.
- A tool’s working point meets the material at the moment the action occurs.
- Different tools have distinct motions.
- Material appearance explains state and progress.
- Nearby players can see the activity.
- A nearby recipe book helps choose work without replacing it.

These are supported by its station definitions, scene JSON, `ToolStroke`, behavior classes and server-side processing. [H1], [H2], [H3]

Do not copy cooking mechanics into smithing. Stirring, serving bonuses, food progression, and the reference’s exact camera framing are not smithing requirements. Its large behavior classes are examples to learn from, not a required architecture.

## 3. Product decisions and boundaries

### 3.1 Keep these foundations

- The four existing station IDs and existing tool/item IDs.
- Forge material families, exact integer metal units, fuel rules, recycling vetoes and configured recovery ratios.
- Explicit recipes, automatic detection, consumed-equipment upgrade inputs, result NBT and excluded recipes.
- The two finished-item quality scores and existing multiplier hooks.
- Existing finished-item maker marks, titles, lore, enchantments, damage and durability carry.
- Preparation automation: feeding metal/fuel and filling water. Automatic pipes must not gain a new way to complete skilled crafting.
- Forge 1.20.1 and Java 17. This is not a loader or Minecraft-version port.

### 3.2 Replace these defaults

- Sweeping-marker forging.
- Shrinking-ring anvil targets.
- Global “finish before the timer expires or become Faulty” pressure.
- Modal recipe selection as the normal workstation interaction.
- Immediate, invisible quench completion.
- Mandatory post-quench signing popups.
- Quality improvement that is only a repeated click with no visible work.

### 3.3 Do not expand into unrelated systems

No new ore economy, compulsory alloy chemistry, structural building system, general physics engine, mandatory shader pack, or large tree of consumable tools is required. Integrate a bellows/air control and billet tray into the existing forge rather than requiring several new blocks to make the first sword.

Thermal behavior is readable game design, not a claim of metallurgical accuracy. Modded fantasy materials must be authorable through data.

## 4. The intended player experience

### 4.1 A complete iron sword

The inspected recipe costs **18 metal units and one stick**. [S10]

1. **Prepare.** Put two iron ingots into the forge’s metal area, fuel into its firebox, and ignite it. The deposits heat, soften and join the existing molten reservoir.
2. **Choose the work.** Open the small pattern book attached to the forge. Choose the sword drawing. The book shows its silhouette, exact cost and the missing/present stick. It then folds away, leaving the selected drawing pinned beside the billet tray.
3. **Commit.** Use the tray or its handle to begin this job. The server reserves the metal and exact auxiliary stack. A small visible supply bundle represents the stick without putting wooden parts into molten metal.
4. **Prepare the billet.** Work the forge’s air control and turn or reposition the billet with tongs to heat it evenly. The metal changes from dark to red to bright orange. An audible/visual cue indicates workable heat. Draw the billet out with the tongs.
5. **Carry.** The billet is visibly gripped in the tongs. Walk to the anvil normally. Do not teleport the player or remotely transfer the item through a camera move.
6. **Shape.** Place the billet, take the hammer, and enter the working view. Draw out the blade, form the tang and establish the edge through aimed blows. The shape grows toward the intended silhouette. Flip or rotate it with a clear action when necessary.
7. **Recover if needed.** If it becomes too cold, carry it back to the forge’s reheat area. Progress remains. Opening chat or leaving the station does not erase the work.
8. **Quench.** Grip the shaped blade, lower it into the trough, and hold it while the hiss and bubbles subside. Lift the cooled blade. It is still an unfinished workpiece when it needs a handle.
9. **Fit.** Return the cooled piece to the anvil’s fitting area. Attach the reserved stick using one or two clearly indicated actions. The exact target item appears only when the job is complete.
10. **Collect.** Take the sword. A brief cue identifies its grade. Engrave it or refine it later if desired.

**Initial tuning target:** roughly 45–75 seconds of engaged work for an ordinary tool or sword, including preparation from a ready workshop; an experienced player should spend less time navigating and waiting, without bypassing the actual operations. Small parts should be shorter, and larger armor should gain meaningful operations rather than identical repeated clicks. These are playtest targets, not current measurements.

### 4.2 Planning without another oversized interface

The pattern book is a bounded aid:

- Show craftable recipes first, with search, category tabs, favorites and a “repeat last pattern” action.
- Explain missing material, heat source, tool or auxiliary with a specific reason.
- Use actual result items, tags and recipe names. Never maintain a separate hardcoded recipe catalogue.
- A selected plan does not spend resources or lock the forge indefinitely.
- Show the cost before the explicit commitment action.
- Exact item counts can appear when inspecting the plan or supply bundle.
- An optional compact inventory drawer may open when selecting or placing supplies; close it during active hammering.
- Support JEI selection where its actual API permits it; server validation remains identical.
- Typing in search, chat or engraving must suppress workstation shortcuts.
- Every selection and action must be possible with keyboard navigation.
- A full recipe browser may remain available as an optional reference. It must not become necessary for each repeated craft.

## 5. Shared controls and interaction routing

Keep controls consistent across stations. Use Minecraft key mappings and render the player’s actual bindings in prompts.

| Context | Primary action | Secondary action | Other controls |
|---|---|---|---|
| Normal world, held material/fuel/vessel | Existing use action inserts or fills the relevant region | Sneak-use performs the documented alternate action | Sneak-place blocks must remain possible |
| Normal world, station work surface | Explicit “Work at station” interaction enters focus | Inspect/status when no valid work action exists | A separate remappable focus key can disambiguate busy item interactions |
| Focused forge | Operate the selected handle, bellows or tongs | Release the current manipulation | Cycle/select physical regions using keyboard |
| Focused anvil | One deliberate hammer blow at the selected point | Grip/reposition the workpiece | Rotate/flip bindings; optional modifier for heavy blow |
| Focused trough | Lower/hold the gripped piece | Lift/release | Toggle-action alternative to holding |
| Focused fitting area | Attach the indicated reserved component | Undo only an uncommitted placement | Cycle sockets/components |
| Focused grindstone | Hold the piece to the turning wheel | Lift it away | Select the refinement aspect explicitly |
| Any focused view | — | — | Escape exits immediately; a movement command exits and returns locomotion |

Routing requirements:

1. Choose the intended action using station, region, held item, hand, state and modifiers **before** starting camera movement.
2. Resolve a physical input once. Do not process the same click through vanilla block use, a scene event and a custom packet.
3. Intercept attacks only while the workstation interaction owns the input. A hammer click must not also break the anvil or attack a bystander.
4. Offhand support must be designed explicitly: support a hammer in one hand and tongs in the other without processing both hands twice. The existing block handlers only accept main-hand interaction; this is a deliberate extension to test.
5. Disambiguate molten-metal handling, reheating and recycling as separate regions/actions. Returning a workpiece to the forge should normally reheat it. Require a clearly indicated recycling action to destroy its progress.
6. A loaded tool cannot silently switch jobs, discard its contents, or be consumed as a recipe ingredient.
7. Plain clicks must suffice. Dragging and holding add tactile control but cannot be the only playable input scheme.
8. Keep ordinary use, build placement and inspection usable when cinematic camera motion is disabled.

Do not assume sneaking should open the old minigame just because the reference uses sneak-use for its own fallback. Define and document Smithing’s controls deliberately.

## 6. Camera and focus system

### 6.1 Three presentation modes, one simulation

| Mode | Intended use | Behavior |
|---|---|---|
| Cinematic work view — default | Clear, close observation of the process | Short transition to an authored, collision-safe view of the real station |
| Shoulder/near-eye view | More awareness of the surroundings | Small offset and modest zoom; retain a clear sightline to the work |
| World view | Motion sensitivity, camera-mod conflicts, preference | No camera movement; the same physical actions use the normal view |

All modes call the same server action service and can achieve the same quality. Disabling a cinematic effect must not disable the craft.

### 6.2 Shot design

- **Forge:** a three-quarter view slightly above the basin, showing the billet, tongs contact, air control and firebox cues. The hand/tool should not cover the heat indication.
- **Anvil:** an oblique overhead view that shows the billet’s entire working area, hammer impact and near-side grip. Retain enough side angle to read thickness.
- **Trough:** a three-quarter side view showing the waterline, immersion depth, tongs and steam clearing.
- **Grindstone:** show the contact edge and wheel rotation without hiding the work behind the wheel.
- **Fitting/engraving:** a stable anvil close-up, with the blade/plate and attachment region readable.

Start with 200–350 ms entry and 150–250 ms exit blends. Avoid camera roll, forced depth of field, black bars, repeated dramatic cuts or zoom pulses for every strike. Use a moderate effective FOV, initially around 45–60 degrees, then tune it against the player’s preferred FOV and viewport shape. Do not overwrite saved video options to implement a temporary view.

Give milestone actions a restrained visual payoff: follow the billet a short distance as it leaves the forge, let a finished contour catch the light, and reveal the cooled surface as steam clears. Keep these motions optional and never delay the next input for a victory shot.

Keep useful surfaces inside a safe viewport area at 4:3, 16:9 and ultrawide ratios, and at large GUI scales. Prefer moving the camera slightly farther back to cropping a long weapon. Left-handed users can mirror tool placement or choose the opposite shoulder without rotating scoring coordinates.

### 6.3 World correctness and interruption

The camera is presentation. The player’s body remains at its real position, with ordinary collision, vulnerability and permissions.

- Validate interaction range and line of sight from the **real player**, never the detached camera.
- Test the entire camera transition against world collision, not only its endpoint.
- Try the opposite shoulder or a nearer view if the preferred shot is blocked. If neither works, use World view.
- Do not hide arbitrary walls, roofs or neighboring blocks to make a cinematic shot work.
- A local camera ray may choose a point on the station’s bounded work surface. The server separately validates that this surface is reachable and unobstructed from the player; it must not require the player’s head to physically point along the detached camera ray.
- Keep the world unpaused. Never create a dummy body that grants immunity or remotely extends reach.
- Damage, knockback, death, teleport, dimension change, station destruction, disconnection and explicit exit must release input/camera ownership.
- Opening chat or a real inventory screen pauses manipulation and preserves the work. Restore bindings, cursor capture, perspective, FOV overrides and HUD state on every exit path.
- A render-only camera shake must not move the collision/raycast frame or change the scoring point.
- Station-to-station focus changes are allowed only after an explicit action and fresh reach/visibility checks. Do not enable surprise edge-hover travel by default. Camera travel never carries the real workpiece.

Make exit immediate even during an animation. A cosmetic release animation may continue after control has returned.

## 7. Forge: prepare metal through direct manipulation

### 7.1 Preserve loading and material accounting

Retain the current family-locked reservoir and integer unit system. Metal deposits and fuel keep their own areas; molten metal is never represented by spawning hundreds of item entities.

Expand the forge with integrated visual parts:

- A fuel opening with visible fuel.
- A metal basin/reservoir with a readable fill level.
- A short billet-forming tray and extraction point.
- A bellows handle or air control.
- A separate, clearly recognizable reheat rest.
- A small pattern-book rest and reserved-supply display.

These can be subparts of the existing block model and interaction scene. Update shape, rotation, render bounds and hit regions together. Moving parts must not imply that the block has a different collision shape unless that change is deliberately implemented.

### 7.2 Forming and thermal work

Keep melting as preparation. Once enough molten metal exists, a recipe job reserves its cost and forms a **rough billet**, not a finished weapon floating in the fire.

Use a simple deterministic thermal model:

- Material profile defines a workable band, optimum band, cooling rate and overheat response.
- Fuel and air control affect the forge’s heat input.
- The billet has a small number of thermal regions, initially 3–5, not a full heat equation.
- Turning/repositioning the billet improves even heating.
- A valid air-control action drives a physical compression/release cycle. It contributes at a bounded rate; packet spam is not extra airflow.
- Drawing the billet in a broad appropriate band records a heat preparation component of quality.
- Heat, oxidation/scale and unevenness are visible, but the game does not demand remembering real-world temperatures.
- Advanced numeric readouts may be available through inspection; they are not the primary presentation.

A proposed default normalized heat model can use `0..1` with a broad workable interval, such as `0.45..0.80`, and a narrower optimum interval within it. These numbers are starting data, not universal material rules. Pick final values through playtesting and use integer/fixed-point state for deterministic gameplay.

Do not reward arbitrary mouse speed. The player manages a physical process: apply air, reposition the stock, observe readiness, extract it. Correct actions should be legible before the player makes them.

### 7.3 Heat is pressure, not an expiration punishment

- There is no short global failure countdown.
- Cooling makes the piece harder or impossible to shape; it never automatically deletes progress or turns it Faulty.
- Reheating costs fuel/time, not another set of metal or auxiliaries.
- Reheating a blade must not melt it into the reservoir unless the player deliberately chooses recycling.
- A fresh billet should remain workable long enough to walk between reasonably spaced stations.
- Leaving the workstation allows ordinary cooling while the holder is being simulated. Unloaded chunks and offline players do not accumulate punitive real-time decay.
- Define one thermal time rule for all holders. A practical baseline is simulation ticks while the piece is loaded/held; record the last simulated tick and initialize safely on load. Do not subtract offline wall-clock time.
- Transfers never reset temperature, restore quality, or reroll an advantageous state.
- Overheating should first create recoverable scale or a bounded quality penalty. Destructive failure, if offered at all, is an explicit server option with advance warning.

The standard recipe should work with a few purposeful interactions. Do not replace the old timing game with 30 identical bellows clicks.

## 8. Anvil: shaping the actual workpiece

### 8.1 Replace targets with meaningful working regions

The billet is the interactive object. The player aims at its face, edge, tang, rim, socket or other current working region. Use subtle chalk lines, stamped guides, raised areas, unfinished contours and changing surface highlights.

Do not draw floating circles that spawn at unrelated random coordinates. Marks stay attached to the workpiece and disappear or change as that part is worked.

Each accepted blow must have:

1. A valid active workpiece and tool.
2. A point in canonical workpiece coordinates.
3. A relevant operation and permitted face.
4. Sufficient working heat, unless the operation is explicitly cold work.
5. A server-approved strength and recovery interval.
6. A visible deformation/progress result.

### 8.2 Hammer behavior

Default action: point and click for a normal blow. Initial recovery around 0.45–0.65 seconds makes blows deliberate without becoming sluggish. Tune by tool class and accessibility testing.

Optional heavy/light actions may help draw or finish metal, but normal clicks must remain sufficient to finish every default recipe. A hold-and-release scheme is optional; expose keyboard/discrete equivalents.

Animate a short anticipation, downward arc, precise contact, material compression and rebound. Tie the accepted strike’s material change, impact sound and spark origin to the same impact event. During prediction, animate intent promptly; do not permanently deform the piece before server acceptance. Give the acting client a single sound/effect, not prediction plus an echoed duplicate.

Target coordinates may be selected at wind-up. Once committed, do not allow the hammer to teleport to a different region at the last frame. Define whether the strike samples heat at accepted contact; use that definition in prediction, simulation and tests.

### 8.3 Shape representation

Use a small, deterministic shape model:

- Authored intermediate meshes or a bounded segment/grid representation.
- Regions accumulate deformation/coverage toward an intended profile.
- Material volume is approximately conserved visually; a blade lengthens while becoming thinner.
- Temperature is separate from shape.
- Flipping rotates the same piece; it does not reset stage progress.
- Geometry has a stable local coordinate frame independent of camera, GUI scale, screen resolution or decorative tool motion.
- Completed regions saturate. Repeatedly striking one easy spot cannot farm progress or unlimited quality.
- Bad blows can leave visible unevenness or reduce precision. A few corrective operations should recover ordinary mistakes.
- Partial shaping persists when the player exits or changes stations.
- Unknown/custom modded equipment has a useful generic physical profile and remains craftable.

Never mutate arbitrary third-party baked meshes to obtain progressive deformation. Use Smithing-owned intermediates and resolve the real item model for the completed result.

### 8.4 Cover the existing equipment families

Every existing pattern ID needs an explicit mapping or documented generic fallback. The following groups cover the 40 inspected IDs:

| Physical profile | Existing pattern IDs | Characteristic work |
|---|---|---|
| Straight/short blade | `sword`, `longsword`, `greatsword`, `dagger`, `parrying_dagger`, `rapier`, `throwing_knife` | Draw length, form tang, establish tip and edge |
| Curved blade | `katana`, `saber` | Draw blade, controlled curvature, finish edge |
| Chopping head | `axe`, `battleaxe`, `tomahawk` | Spread head, form attachment/eye region, dress edge |
| Mining/farming head | `pickaxe`, `shovel`, `hoe` | Shape working points or plate, form handle attachment |
| Hammer/mace/club hardware | `battle_hammer`, `warhammer`, `flanged_mace`, `club` | Face/head work, flanges or ferrules as appropriate |
| Polearm hardware | `glaive`, `halberd`, `javelin`, `lance`, `pike`, `scythe`, `spear`, `quarterstaff` | Forge metal head/fittings; attach nonmetal shaft after quench |
| Armor plate | `helmet`, `chestplate`, `leggings`, `boots` | Dish or curve plate sections, work rims, attach reserved straps/components |
| Shield/fist hardware | `shield`, `cestus` | Plate/boss or metal fittings, then backing/straps if supplied |
| Curved thrown form | `boomerang` | Balance arms and shape their join |
| Ranged weapon hardware | `longbow`, `heavy_crossbow` | Metal limbs/fittings only; assemble remaining materials afterward |
| Fallbacks | `generic_weapon`, `generic_tool`, `generic_armor`, `generic_equipment` | Sensible bounded metal blank, broad shaping regions, actual result preview |

Do not melt leather armor, wooden stocks or bow strings visually just because the output is one item. Use the metal portion as the hot workpiece and show auxiliaries during fitting. Preserve recipe costs: visual subcomponents are not an excuse to add hidden charges.

Aim for roughly 10–24 meaningful accepted blows for ordinary items as an initial tuning range, with fewer for small pieces. Different profiles should change what the player does, not merely rearrange the same ten dots.

## 9. Quenching, fitting, refinement and signing

### 9.1 Quenching is a short physical operation

The player grips a shaped workpiece with tongs, positions it above the water, lowers it and lifts it after the hiss subsides.

- Target approximately 2–4 seconds of active immersion for a normal piece; profile data can vary it.
- Use actual water height and the workpiece’s canonical immersion depth.
- Show an initial splash, bubbles along the submerged section, steam and cooling metal.
- Let steam clear enough that the player can still read immersion.
- A broad safe immersion interval and clear end cue are sufficient. Avoid a tiny surprise timing window.
- A simple click-to-lower/click-to-lift or toggle action must produce the same legal operation as dragging.
- Reserve the configured water cost when starting. Consume it at a defined immersion boundary; never charge per animation frame or repeated packet.
- If interrupted before immersion begins, release the reservation. Once meaningful immersion starts, preserve progress and consumed water; exiting/reopening must not refund it or reroll quality.
- Use one accepted quench result for the piece. Repeated dips after success cannot increase its score.
- A shaped piece that has become too cold can be reheated safely before quenching. Never let the player lose the already completed shaping step.
- Tool breakage, death, full inventory and station removal must leave either a recoverable workpiece or the one committed result.

The trough must have durable job/operation state if it temporarily owns the piece. Do not animate a real piece in both the tongs and trough at once.

### 9.2 Fit components after the metal is cool

Give wooden handles, straps, backing, bow strings and other nonmetal ingredients a visible role.

- Use the existing anvil as a fitting surface; no fifth required workstation.
- Display only relevant attachment points and a small tray/bundle of the exact reserved auxiliaries.
- One or two assembly gestures are enough for a simple tool. Armor and ranged equipment can use a small sequence of meaningful attachments.
- Inventory bookkeeping must follow actual ingredient instances, including NBT, container remainders and consumed-equipment rules.
- For a fully metal item with no fitting steps, quenching may directly complete the item.
- For an item with fitting steps, keep a non-usable cooled workpiece until fitting is complete. Do not issue a functional sword and also retain an unfinished duplicate.
- Automatically detected recipes use a generic assembly action when their auxiliaries cannot be sensibly classified. They must remain craftable.
- Decorative fasteners are not new ingredient requirements.
- Existing version-1 workpieces have already paid their auxiliaries. Their migration must not charge for fitting again.

### 9.3 Grindstone: optional refinement with visible contact

Keep the grindstone optional. A normally completed tool is usable without another mandatory quality minigame.

Convert the block into a station with a block entity if it needs persistent held-item state. Preserve the existing registry ID and facing.

1. Place or grip a finished, eligible item.
2. Choose the existing durability/efficacy refinement aspect with an explicit label.
3. Show the exact XP-level price and score change before commitment.
4. Turn the wheel and draw the relevant edge/surface across it for a short pass.
5. Complete one paid refinement, capped at the current score limit.

Preserve the configured score step, cost formula, enchantments, lore, maker mark, damage, other mod data and Faulty-item restriction unless a separately documented balance decision changes them. Do not charge XP on focus entry. Commit the XP deduction and score update together at accepted completion; revalidate the specific item and available XP then. An interrupted pass grants no free improvement and no double charge.

Wheel rotation, sparks and scraping sound must follow actual accepted contact. Turning an empty wheel is cosmetic. Refinement must not invoke the original crafting reward again.

### 9.4 Maker’s Mark

Keep automatic author attribution, but separate it from voluntary inscription.

- Default completion feedback: a brief grade cue and “Engrave later” affordance.
- Engraving at the anvil can open a compact text editor. Text entry is a legitimate use of an interface.
- A visible stamp or engraving stroke accompanies a successful signature.
- Continue using the server-side authorization and sanitization in `SigningService`.
- Bind a request to the exact item/revision being edited, not only an inventory slot that may now hold a different item.
- Faulty items remain unsignable under the existing policy.
- Existing signatures survive all non-destructive transfers.
- Preserve `openAfterQuench` as a documented legacy preference where feasible; make automatic popup behavior off by default. Explain migration of existing saved config values instead of assuming a changed Java default rewrites them.

## 10. Quality, difficulty and pacing

### 10.1 Preserve the finished-item contract

Continue producing `ForgeScore`, `AnvilScore` and `Faulty`. Keep existing grade thresholds and stat multipliers as the initial balance baseline. The current defaults map forge scores to 0.75–1.25 durability and anvil scores to 0.85–1.15 efficacy; retain the distinct Faulty multipliers and existing quality hooks. [S11]

The meanings can become more physical:

- **Forge score:** heat preparation and quench control.
- **Anvil score:** shaping accuracy, coverage and finishing of required regions.

A starting evaluation model, to be tuned:

```text
ForgeScore = round(100 * clamp(0.70 * heatPreparation + 0.30 * quenchControl))
AnvilScore = round(100 * clamp(sum(requiredRegionWeight * regionQuality)
                              / sum(requiredRegionWeight)))
```

For these formulas, `clamp` means `clamp(value, 0, 1)`. All components are server-derived. Define how they are accumulated, finalized and corrected; do not send final scores from clients.

- Weight required work, not raw click counts.
- Saturate completed regions.
- Record mistakes and permitted corrections in persistent work state.
- Finalize each phase once.
- Reheating cannot reset poor work to a fresh perfect score.
- A correction can improve a region within a bounded rule; it must cost actual work.
- Reopening the camera, changing input mode or relogging has no effect on scores.
- Zero relevant actions must not yield a default Masterwork result.
- Normal competent work should reliably produce Standard/Fine results; Masterwork should reflect careful work across the required process.
- Legacy stored scores are preserved rather than recomputed through the new formula.

Remove the deadline-induced Faulty default. Faulty should arise only from clearly communicated severe process damage or an explicit decision to finish damaged work. Ordinary interruption or a missed click must not produce catastrophic quality loss.

### 10.2 Tool tiers

Retain stone, iron, diamond and netherite tools and their existing repair/enchantment behavior.

The old tier-dependent 30–45 second deadlines no longer fit the new design. Replace them with modest, explicit differences such as durability, grip stability, useful working reach within the station, or recovery efficiency. Stone tools must complete every basic process. Better tools must not automatically award quality or silently widen a client-only scoring area.

Keep old time config keys readable with deprecation messages and a migration table. Do not convert “more time” into “slower crafting” by mistake.

### 10.3 Repetition and production

- Remember the last selected recipe locally per station or player, but revalidate it server-side.
- Let one prepared reservoir support several individual pieces without reopening a large browser.
- Keep quality and payment separate for each finished piece.
- Do not add bulk production that multiplies one high-quality action into many free Masterwork items.
- Small-item batch profiles may be added later with explicit per-output material accounting and quality rules.
- Never require repeated book opening, countdown screens or result dismissal.
- Watch first-time and experienced players complete several consecutive crafts, not only one showcase sword.

## 11. Visual and audio requirements

### 11.1 A visual state contract

For every important state, define its appearance, sound and allowed interaction together:

| State/action | Required visual explanation | Supporting feedback |
|---|---|---|
| Fuel loaded, unlit | Distinct fuel in firebox; cold stock above | Fuel placement sound; ignition affordance |
| Heating | Increasing emissive metal, moving coals, restrained heat wisps | Fire grows with air input |
| Uneven heat | Difference along the billet plus pattern/inspection cue | Short cue or status phrase; not color alone |
| Ready to work | Broad recognizable glow and tool-ready pose | Subtle readiness sound |
| Hammer contact | Hammer face meets stock; local deformation and scale | Material-dependent impact and rebound |
| Wrong area/cold stock | No impossible deformation; clear local reaction | “Reheat the billet” or “Work the raised edge” |
| Correct shaping | New contour/flattened region persists | Consistent impact tone, restrained sparks |
| Quenching | Waterline, splash, bubbles, steam, cooling | Hiss decays with progress |
| Fitting | Reserved part moves into the correct socket | Fitting/tap/click |
| Refinement | Moving wheel, correct edge contact, directional sparks | Scrape linked to pressure/contact |
| Completion | Finished actual item settles into a collectible position | Brief grade cue, no modal result screen |

Forge glow must reflect temperature, not an always-full-bright material. Cold equipment must look cold. Shader effects can enhance this, but ordinary Forge rendering must communicate every gameplay state.

### 11.2 Tools and hands

- Use existing tool models as the starting point.
- Add open/closed tongs jaws, a correct grip location and secure workpiece attachment.
- Maintain one animation authority for each visible hand/tool. Avoid rendering a scene hammer over a second vanilla hammer.
- Coordinate with the current `IClientItemExtensions` implementations and `HumanoidModelMixin` rather than stacking unrelated poses.
- Support both handedness settings and offhand combinations.
- The acting player sees precise tool contact; observers see plausible synchronized work.
- Use a bounded station tool representation when a third-party first-person/body renderer prevents precise hand rendering.
- A cosmetic proxy is never a second inventory item or an entity that can be collected.
- Suppress a conflicting local pose narrowly while focused, then restore it on exit.

### 11.3 Assets to author

At minimum:

- Forge moving controls, billet tray and reheat rest.
- Tongs articulation and attachment transforms for all tool tiers.
- Hammer swing/contact/recovery curves.
- Billet-to-blade and billet-to-head intermediate geometry.
- Plate, curved plate, metal fitting and generic fallback intermediates.
- Scale flakes and restrained sparks.
- Temperature/material appearance layers.
- Trough immersion, water response and steam.
- Grindstone wheel/contact animation.
- A small pattern book, pinned drawing and component socket cues.
- Fitting and optional maker-stamp animations.

Use Minecraft-like geometry and pixel density, with consistent material ramps and readable wear. Keep editable sources and resource-pack replacement paths. Extend the existing `art/` workflow and validate exports; do not run a placeholder generator that overwrites finished art.

Use the actual modded item’s renderer for the final item when safe. Retain a bounded silhouette/icon fallback for custom, missing or oversized renderers. External renderers must not crash the workstation or turn hit geometry into arbitrary third-party geometry.

### 11.4 Sound and decorative effects

Create or select properly licensed sound assets for metal impacts, bellows, coals, tongs, water and grinding. The current sound definitions reuse vanilla events; custom audio is a meaningful improvement, not a prerequisite for functional milestones.

- Vary pitch/volume slightly without changing timing information.
- Scale fire, scrape and quench loops with actual state.
- Stop loops on unload, exit, destruction and disconnect.
- Add subtitles.
- Prevent multiple nearby stations from producing a wall of repeated ready sounds.
- Let users reduce flashes, sparks, smoke, shake and motion independently.
- No single flash, particle or audio cue may be the only way to understand a required action.

## 12. Architecture and dependency decision

### 12.1 Start with a bounded Diegesis integration proof

Diegesis already exposes station types, server logic, scene JSON, camera rigs and client behavior hooks; Hands-On Delight uses these directly. [H2, D1–D3]

**Recommended route:** use Diegesis behind a small Smithing-owned adapter if a short integration proof demonstrates that it satisfies the requirements below. Start from the inspected beta.9 API and select a reproducible, pinned compatible artifact. Do not treat a current beta version as guaranteed compatible without running it.

The proof must demonstrate:

1. An anvil scene on the existing Smithing block and rotation.
2. Camera focus and a correct physical cursor/work-surface mapping.
3. One server-authorized hammer action.
4. One canonical workpiece in the existing block entity.
5. A second client observing the action.
6. Immediate exit, death/disconnect cleanup and reload.
7. World-view input and reduced-motion operation.
8. Coexistence with Hands-On Delight on the same client.
9. Dedicated-server startup without loading client classes.
10. Rendering with the intended body/camera and shader/performance mods.

Use its public API. Do not depend on `com.pockyl.diegesis.impl` classes or transplant its internal manager. Do not copy Hands-On Delight’s `includeBuild('../diegesis')` into production as an undeclared sibling-repository requirement. Resolve the exact production/dev dependency through a documented repository/artifact setup.

The target uses Forge 47.4.23 while the inspected reference/library builds use 47.4.26. Validate the actual intended minimum and packaged runtime. Any minimum-version increase must be explicit and justified; do not quietly change loaders or import 1.21.1 examples.

If the proof finds a material blocker, implement one native Forge focus/scene adapter with the same Smithing domain contracts. Record the blocker and decision. Do not maintain two complete gameplay engines or leave the user to choose a library before any work is done.

Hands-On Delight and Diegesis declare MIT licenses; Immersive Smithing declares GPL-3.0. Preserve required notices if reusing their source/assets, record provenance, and inspect any separately licensed bundled assets. The desired behavior can also be implemented independently. Farmer’s Delight and Hands-On Delight must not become required dependencies merely because they inspired this overhaul.

### 12.2 Separate persistent work from temporary control

Use four clear responsibilities:

| Layer | Responsibility | Example proposed types |
|---|---|---|
| Domain simulation | Heat, shaping, quench, assembly, quality, resource conservation | `SmithingJob`, `ThermalRules`, `ShapingRules`, `QuenchRules` |
| Authoritative storage and actions | Block/tool ownership, transactions, persistence, permission and action checks | `SmithingActionService`, `WorkpieceTransferService`, `WorkSessionManager` |
| Client interaction | Focus, picking, tool intent, input ownership, prediction | `SmithingFocusController`, `WorkSurfacePicker`, `ToolController` |
| Presentation | Scene definitions, animation, models, sound and particles | `ForgeScene`, `AnvilScene`, `WorkpieceRenderer`, `SmithingEffects` |

Names are suggestions; responsibilities are requirements.

A focus session is a temporary control lease. The workpiece/job outlives it. Closing a session releases control and input capture; it does not clear thermal/shaping state or refund already transformed materials.

If using Diegesis, decide exactly which state it transports and which component persists it. Prefer bindings/projections onto the Smithing-owned inventory and workpiece. Never let both a library slot and a Smithing block entity independently own the same stack. Resource-pack scenes describe appearance; they do not dictate recipes, prices, hit validity or scoring.

### 12.3 Concrete repository changes

| Existing location | Required work |
|---|---|
| `block/SmithsForgeBlock.java` | Route distinct load, fuel, focus, reheat and recycle actions through the shared action service |
| `blockentity/SmithsForgeBlockEntity.java` | Extend escrow into a durable job/reservation; conserve available/reserved/formed units |
| `block/SmithsAnvilBlock.java` and its block entity | Persist progressive work and fitting; direct physical hit dispatch |
| `block/SmithsTroughBlock.java` and its block entity | Quench operation/reservation; extraction of finalization from the one-click handler |
| `block/SmithsGrindstoneBlock.java` | Introduce appropriate block entity/station state and paid physical refinement |
| `minigame/SessionManager.java` | Extract reusable validation and lifecycle rules; replace ephemeral minigame ownership with control leases |
| `minigame/ForgeRun.java`, `AnvilRun.java` | Retire from normal gameplay after the replacement and migration tests pass |
| `client/screen/ForgeMinigameScreen.java`, `AnvilMinigameScreen.java`, `WorkshopScreen.java` | Remove from the default crafting flow; retain only any explicitly useful reference/accessibility UI |
| `client/renderer/*` | Extend workpiece stages, tool contact, temperature and scene/BER ownership |
| `client/ClientEvents.java`, `ClientPacketHandler.java` and client item extensions | Central input/camera lifecycle and predicted/accepted action handling |
| `workpiece/WorkpieceData.java`, `WorkpieceCodec.java` | Versioned state, job identity, stage progress, snapshots and safe legacy migration |
| `network/ModNetwork.java` and packet classes | Explicit new actions, bounded decoding, authoritative snapshots and incompatible protocol bump |
| `recipe/SmithingRecipe*`, `SmithingData.java` | Optional process-profile references, fallback resolution and reload-safe snapshots |
| `material/*` | Add optional heat behavior without changing unit/fuel/economic interpretation |
| `quality/*` and `api/event/ItemSmithedEvent.java` | Preserve final quality behavior; centralize once-only completion and document event timing |
| `config/*`, `guide/*`, lang, JEI/Jade integrations | Replace old instructions and expose appropriate options/status |
| `src/gameTest`, `src/packTest`, `tools/visual-test` | Update real tests and capture workflows for the new interactions |

Do not put all four stations into one giant behavior class. Pure rule classes should be testable without a renderer or live network.

Centralize local-to-world transforms for each station. In the inspected code, anvil placement uses the player direction rotated clockwise, whereas forge/trough placement uses the opposite direction. Preserve the intended model alignment and share the exact transform between drawing, picking, operation regions and observer effects; do not assume every block has an identical north-facing convention.

## 13. Persistent state and data-driven processes

### 13.1 Proposed workpiece schema

Version the workpiece payload independently of the existing finished-quality compound. A proposed version-2 record contains:

- `workpieceId` / `jobId`: server-generated unique identities.
- Original recipe ID, material family and exact paid metal units.
- Original target-stack snapshot and enough resolved process data to finish after a datapack reload.
- Stage: `BILLET`, `SHAPING`, `SHAPED`, `QUENCHING`, `COOLED` or `FITTING`.
- Heat regions, last simulated time, and relevant heat history.
- Shape/region progress, orientation, permitted corrective work and bounded scoring accumulators.
- Finalized phase scores and flags.
- Reserved/unassembled auxiliary stacks and their status.
- Recipe remainder/recovery policy captured at commitment.
- Legacy migration flags, where required.
- Actual quench location, if quenching has happened.
- Mutation revision and completion/recovery status.

A completed usable item should no longer also be an active workpiece. Represent output ownership explicitly during finalization.

Keep tool inventory references and transient camera/session data out of this durable payload. A tool’s hand/slot may change; the workpiece identity and resource history do not.

Validate numeric bounds, finite values, stage transitions, array lengths and stack counts. Never allocate unbounded structures from NBT or packets. Preserve original raw data when a required mod/item is missing; quarantine or expose a recoverable error state instead of silently replacing valuable content with an empty stack.

### 13.2 Process profiles and visual profiles

Keep gameplay profiles in datapacks and presentation in resource packs.

Proposed gameplay locations:

- `data/<namespace>/immersive_smithing/process_profiles/<id>.json`
- Optional thermal fields in existing material definitions, with safe defaults.
- An optional `process_profile` field in ordinary `immersive_smithing:smithing` recipes.

Proposed presentation locations:

- `assets/<namespace>/immersive_smithing/workpiece_profiles/<id>.json`
- Scene/model/texture resources, or Diegesis scene files if that route is selected.

Illustrative recipe extension — **implement this schema before expecting it to work**:

```json
{
  "type": "immersive_smithing:smithing",
  "material": "minecraft:iron",
  "metal_units": 18,
  "auxiliary": [
    {"ingredient": {"item": "minecraft:stick"}, "count": 1}
  ],
  "result": {"item": "minecraft:iron_sword"},
  "process_profile": "immersive_smithing:straight_blade",
  "forge_pattern": "immersive_smithing:standard",
  "anvil_pattern": "immersive_smithing:sword"
}
```

Example process-profile shape:

```json
{
  "schema_version": 1,
  "workpiece_profile": "immersive_smithing:straight_blade",
  "heat": {
    "workable_min": 0.45,
    "workable_max": 0.80,
    "optimal_min": 0.58,
    "optimal_max": 0.72
  },
  "operations": [
    {"id": "draw_blade", "region": "blade", "kind": "draw", "work": 6},
    {"id": "form_tang", "region": "tang", "kind": "shape", "work": 3},
    {"id": "dress_edge", "region": "edge", "kind": "finish", "work": 4}
  ],
  "quench": {"duration_ticks": 60, "immersion_min": 0.65},
  "assembly": [{"auxiliary_index": 0, "socket": "grip"}]
}
```

Define units and limits for every field. Validate ordered thermal bounds, positive operation weights, nonempty required work, valid region/socket references and bounded durations before accepting a profile. Here, `work` is an abstract simulation work requirement, not necessarily an exact click count. The authoritative region geometry must come from validated server data/common rules; the client visual profile may decorate it but cannot expand it.

Resolution order:

1. Explicit valid process profile.
2. Mapped legacy anvil pattern/category.
3. Existing equipment classifier/public integration result.
4. Generic equipment fallback.

Keep old recipes valid. Treat legacy pattern coordinates as migration input for work regions where useful, not as a reason to keep the old ring game. Unknown pattern IDs get a documented fallback and report entry. Invalid newly supplied profiles receive actionable errors naming the resource and field.

On reload, validate the entire new snapshot before swapping it into service. Existing jobs finish from their frozen snapshot; new jobs use the new rules. Resource reload must rebuild visuals without modifying gameplay state.

## 14. Resource ownership and transactions

### 14.1 Conservation rules

Use these invariants as implementation and test requirements:

- There is exactly one authoritative holder of each active workpiece.
- Available molten units plus reserved molten units equal the stored reservoir total until units become a workpiece.
- Forming a billet moves its cost from the reservoir into the workpiece exactly once.
- An original auxiliary stack is either in player inventory, reservation, the active workpiece’s unassembled bundle, a committed component, a returned remainder, or a recovery drop.
- A cosmetic model, cursor preview or remote snapshot is never a second holder.
- Reheating cannot add metal or recreate auxiliaries.
- No path both returns original inputs and issues a result.
- Refinement does not create a second item.
- Completion cannot reward crafting twice.

Keep mutations on the server thread. Inventory adapters should expose actual storage, not mirrored writable copies.

### 14.2 Commitment boundaries

| Boundary | What becomes durable | Interruption behavior |
|---|---|---|
| Choose pattern | No economic commitment | Close freely |
| Begin job | Metal reservation and exact auxiliary escrow | Before transformation, cancel releases reservations and refunds once |
| Form/extract billet | Paid units removed from reservoir; workpiece becomes the owner of progress and unassembled supplies | Preserve/recover the piece; no full recipe refund |
| Transfer holder | Source removed and destination assigned in one authoritative action | Reject without partial movement if destination is invalid/full |
| Start meaningful immersion | Water consumption and quench state | Continue/recover the same operation; no water refund exploit |
| Attach component | Specific auxiliary becomes committed | Persist attachment; no reusable ingredient |
| Finalize | One mutable final stack, quality, hooks and output ownership | Resume delivery/recovery without repeating completion |
| Refine | XP deduction and score change together | No partial payment/update |

Consumed-equipment upgrades need special care. Preserve the existing `consume_equipment` and upgrade policies. Decide when the original equipment becomes assimilated into the new workpiece, and record it. Once assimilated, it cannot be refunded intact while its value is also recovered from recycling. Do not introduce unconditional enchantment/quality inheritance that differs from the current recipe rules.

Unassembled handles/straps may be returned on deliberate recycling if the recovery policy allows it. Keep them distinct from assimilated equipment. Apply existing melt vetoes and recycling efficiency rather than inventing a second recovery economy.

### 14.3 Recovery cases

Implement and test: full inventory, occupied destination, last-durability tools, death, logout, station break, explosion, chunk unload/reload, server stop, datapack change and missing resources.

A dropped recoverable workpiece carries the exact durable payload. It remains unusable as finished gear. Remote animation state and temporary locks are discarded safely.

Use job/action IDs and persisted completion/recovery markers for idempotency. A UUID alone is not a duplication solution. Exercise reloads at each transaction boundary.

Do not claim arbitrary power-loss atomicity across separately saved player, chunk and world files unless a durable journal/reconciliation mechanism actually provides it. At minimum, guarantee normal lifecycle and consistent-save recovery, document any remaining crash window, and never delete ambiguous player items to conceal a recovery problem.

## 15. Server authority, networking and multiplayer

### 15.1 One input service

Both direct world interaction and focused scenes must call the same domain actions.

| Client intent | Authoritative checks/results |
|---|---|
| Request focus/lease | Current dimension, station instance/type, permission, player state, body reach and visibility |
| Select plan / begin job | Current recipe snapshot, resource availability, exact inventory and tool exclusions |
| Set air control / turn billet | Legal region/state, bounded value/rate, valid job ownership |
| Strike | Session/job identity, workpiece revision, tool, coordinate bounds, operation, timing/recovery, heat |
| Rotate/flip | Legal stage, free tool state, permitted orientation change |
| Transfer/reheat | Source/destination holders, capacities, compatibility and exact piece identity |
| Lower/lift for quench | Valid shaped piece, water reservation, legal immersion progress |
| Fit/refine/sign | Exact item/auxiliary/XP/author state and permitted transaction |

Never accept a client-supplied output item, score, arbitrary NBT, paid cost, temperature or “success” flag.

For a new packet protocol, include appropriate station position/dimension context, station-instance identity, job ID, session nonce and monotonic action sequence. Use revisions for structural mutations; continuous pose updates should not conflict merely because another visual snapshot arrived.

The existing network version is `"1"` with exact matching. Bump it when changing packet layouts/semantics, and reject incompatible client/server pairs with a clear message. Do not reuse an old discriminator for a differently decoded action without a protocol change.

### 15.2 Validation and prediction

- Validate finite coordinates and map them into a bounded authoritative work surface.
- Check actual player permissions, item use restrictions and claims integration where present. Packet handlers must not bypass protections merely because normal block use was protected.
- Revalidate critical tool, distance and workpiece state on each accepted action, not only every few ticks.
- Reject duplicate/replayed sequences and stale station instances.
- Apply bounded cooldowns and per-player action budgets.
- Timestamp compensation must be bounded by observed/server timing, monotonic and rate-limited. Do not trust a client clock simply because the reference sends one.
- Simulate gameplay on server ticks. Do not let a low-TPS server expire work through an unrelated wall-clock timer.
- For tactile timing, maintain a bounded mapping between client monotonic input time and the simulation timeline; resynchronize when drift exceeds tolerance.
- Predict local tool movement, not inventory commitment or permanent quality.
- Deduplicate accepted effects by action ID.
- Corrections restore the accepted state without a second impact or sudden camera jump.
- Dropped/invalid actions must not leave the player believing a transfer succeeded when the server rejected it.

No interface can prove a client’s physical mouse motion. The goal is validated legal gameplay, bounded rates and conservation, not a promise that macros are impossible.

### 15.3 Observers and shared workshops

The worker sees detailed local guidance. Observers see the workpiece, tools, heat, strokes and fluid effects, without the worker’s private inventory, recipe search or cursor.

- Synchronize semantic events and compact state; interpolate between snapshots.
- Continuous tool poses can start around 10 updates/second, with changes coalesced. Discrete strikes/transfers remain explicit.
- Limit recipients to relevant tracking clients.
- Late joiners get the complete current state.
- Distant stations use lower detail and stop unnecessary animation/network work.
- One writer controls a given work surface at a time. Other players may inspect; they cannot grab or change the active piece during a committed operation.
- A lease has explicit expiry/release. Disconnects cannot permanently lock a station.
- After release, use existing/public-workshop access rules or a documented permissions policy; do not invent an indefinite ownership lock.
- Nearby helpers may prepare another forge or fill water where it does not interfere with a locked transaction. Simultaneous multi-user shaping is not required.

### 15.4 Completion and public events

Extract a single `SmithingCompletionService` from the current trough handler.

Preserve the important order: create the result from its original snapshot, apply quality, run required item crafting initialization, stamp maker data, expose the live mutable stack to `ItemSmithedEvent`, optionally fire the vanilla craft event, then commit the final mutated stack to its output/inventory.

For recipes needing fitting, fire completion after fitting rather than on the earlier quench. This changes the old event’s documented timing and must be documented and tested:

- Keep existing constructor/getter signatures working.
- Preserve `getTroughPos()` as the actual recorded quench position; do not return an anvil position under that name.
- Add completion location/stage information through a compatible extension if needed.
- Preserve listener mutations.
- Give crafting stats, advancements and progression rewards once.
- Attribute the item to the server-validated completing smith, consistent with the old quenching actor rule; never trust a name from the client.
- Existing completed items, refinement and inscription do not refire crafting rewards.
- Test a listener that changes the result’s NBT so event-order mistakes are observable.

## 16. Migration and saved-world compatibility

Treat migration as part of the implementation, not a release note added after development.

| Existing data | Required migration |
|---|---|
| Finished equipment with version-1 quality | Leave scores, Faulty status, multipliers, durability carry and unrelated NBT unchanged |
| Signed/named finished equipment | Preserve smith identity, title, lore and signing permissions |
| Version-1 `FORGED` workpiece | Enter a new shaping-compatible state with its target, paid cost, stored forge score and pattern preserved; mark auxiliaries as already paid/assimilated |
| Version-1 `SHAPED` workpiece | Remain eligible for the legacy completion path; preserve both scores and Faulty flag; do not require shaping or ingredient payment again |
| Workpiece in tongs, anvil or dropped item | Apply the same migration exactly once at the payload boundary |
| Existing forge deposits/molten units/fuel/lava | Preserve quantities and material family; add new fields with safe defaults |
| Old forge escrow with no surviving session | Follow the existing once-only refund/recovery semantics; do not misinterpret it as a newly committed billet |
| Existing trough water | Preserve stored amount and configured quench cost |
| Old grindstone block | Remain the same block ID; initialize its new block entity when encountered without losing adjacent data |
| Legacy pattern JSON | Resolve through mapped process profiles or documented fallback; keep recipe IDs/costs valid |
| Missing/unknown future payload version | Preserve raw data, prevent unsafe use and report a recoverable problem; never silently erase |

Legacy workpieces should carry an explicit migration flag. For a version-1 FORGED piece, new shaping determines the anvil score, while its old forge score stays fixed; new quench timing must not retroactively alter that stored score. A version-1 SHAPED piece may use the new quench animation but completes without fresh shaping, fitting costs or changed scores.

Check the old `WorkpieceCodec` carefully: it writes a version field, but its present loader does not implement a version-dispatch migration framework. Add explicit handling before extending the data format. Preserve recoverable bytes before attempting to decode target stacks from missing mods.

Do not reuse the finished-quality compound’s version field as the workpiece schema version. They serve different records.

Write migration fixtures from the inspected old formats. Test load, transfer, save, restart and complete for each fixture. Repeating migration must not duplicate ingredients, reapply penalties or change the target.

Before removing the old minigame classes, update or replace all code/tests that instantiate them. Keep readers for old stored data even when the old interaction is gone. Do not promise that downgrading the upgraded save is safe; validate updates on copied worlds.

## 17. Accessibility, configuration and usability

### 17.1 Mandatory accessibility

- Cinematic/shoulder/world camera modes.
- Reduced motion with no camera travel, camera shake, view bob amplification or unnecessary tool sway.
- Independent flash, particle and sound intensity controls.
- High-contrast material guides and non-color temperature/ready indicators.
- Remappable controls displayed using actual key names.
- Left-handed presentation and both hand slots.
- Click/toggle equivalents for drag and hold operations.
- Keyboard region selection and fine aim adjustment.
- Narratable status, instructions and errors.
- Readable text at large GUI scales and small windows.
- No quality cap for using an accessibility presentation/input mode.

Motor-assistance modes must remain honest about their rules. Presentation can enlarge a marker without enlarging its scoring hit area; if a mode actually changes valid targets or timing, make that an explicit server-authorized gameplay option. Prefer broad, readable base mechanics that do not need a punitive “easy mode.”

### 17.2 Configuration ownership

| Server/gameplay | Client/presentation |
|---|---|
| Work/thermal rules and valid operation rates | Camera mode, transition strength and preferred side |
| Quality weights, correction limits, severe-failure policy | Particles, shake, flashes, motion and sound intensity |
| Tool-tier behavior, fuel/time costs | Guide visibility and expanded status text |
| Material/process overrides and recipe replacement | Book layout, favorite recipes and local search |
| Claim/permission and session lease rules | Keyboard/pointer preference |
| Quench water and refinement XP costs | Focused HUD layout and nonessential observer detail |
| Any assistance that changes scoring validity | Purely visual targeting aids |

Continue to support existing recipe, material, integration, automation, loot-quality and grindstone settings. Document every obsolete minigame option and its replacement or retirement.

Do not hide all HUD information unconditionally. Provide compact health/danger cues while focused, preserve subtitles and necessary chat access, and keep the hotbar/inventory available when the interaction requires them.

### 17.3 Teach through the work

Update the Smithing Guide and advancements to match the new flow. The first interaction should teach one action at a time:

- “Add fuel below.”
- “Pump the bellows.”
- “Draw out the billet.”
- “Work the raised edge.”
- “Reheat before shaping.”
- “Lower the blade into the water.”
- “Fit the handle.”

Use translated components, not hardcoded English. Avoid telling players about packet revisions, JSON state or implementation classes in ordinary gameplay. Diagnostic details belong in commands/logs.

## 18. Compatibility and performance

### 18.1 Required compatibility matrix

| Area | What to preserve or verify |
|---|---|
| Vanilla recipes/equipment | Iron/gold/netherite paths, trims unaffected, repair/enchant/dye behavior, damage and inventory movement |
| Explicit/custom recipes | Exact costs, target NBT, condition handling, disabled recipes and result-specific process selection |
| Automatic detection | Every generated recipe resolves to a usable process; unsupported recipes stay unsuppressed with an explanation |
| Material families | Existing pack materials, special fuel restrictions, conflicting tags and absent optional mods |
| Upgrade chains | `shape` versus `addition` policy, consumed bases, recipe remainders and current inheritance behavior |
| Recycling | Vetoes/exclusions, configured yield, incomplete workpieces and no double recovery of bases/components |
| Spartan Weaponry/Shields and Immersive Armors | Existing test mods; all relevant process families and final quality effects |
| Other equipment packs, including Epic Fight/Epic Knights if installed | Inventory recipe audit and representative outputs; verify current availability and adapters before claiming support |
| Runic/progression consumers | `ItemSmithedEvent` once, live result mutations preserved, optional vanilla event once |
| JEI | Recipe visibility, costs, replaced recipe hiding and updated process description |
| Jade | Station contents, actual progress/heat/water and refinement cost without exposing private inventory |
| Hoppers/pipes | Existing insertion/filling faces and simulation semantics; no automation completion bypass |
| Hands-On Delight + Diegesis | No duplicate focus/cursor ownership or shared-state interference |
| First-person body/camera/animation mods | Correct tool count, exit restoration and fallback behavior |
| Embeddium/Oculus and intended shader packs | Picking, emission, translucency, water, fog and depth correctness |
| Resource packs/languages | Reload-safe models, readable fonts, missing-resource fallbacks and translated guidance |
| Dedicated server | No client class loading, no visual asset requirement for simulation |

The earlier visual report specifically discusses first-person body rendering in Ultima. That is useful risk information, not proof that the new camera/tool system is compatible. Reproduce the relevant cases.

Do not advertise universal compatibility from a successful sword test. Generate a report containing recipe ID, output, material, process profile, visual fallback, auxiliary mapping and any suppressed/unhandled case.

### 18.2 Performance rules and measurement

Maintain one bounded simulation per active piece. No mesh reconstruction, recipe scanning or arbitrary item-renderer inspection on every frame.

- Cache baked/intermediate geometry by profile/material/resource generation.
- Invalidate caches on relevant reloads.
- Use bounded region counts and compact snapshots.
- Do not send full NBT or inventory contents per animation frame.
- Reuse the existing renderer’s distance/frustum behavior and particle budgets as a baseline.
- Make action effects local to the point of contact and pool/cap cosmetic work.
- Skip idle stations without meaningful animation.
- Avoid mandatory full-screen post-processing.
- Do not animate every distant tool at the focused view’s detail level.
- Keep dynamic lighting optional; emissive surfaces and standard block light must be sufficient.

Benchmark 1, 8 and 32 active stations with 1 worker and multiple observers; include unloaded/loaded transitions and idle cases. Record CPU frame time, render work, allocation rate, server tick cost, packet rates and particle counts, with hardware, settings and workload.

Use an initial investigation threshold around **1 ms of extra focused-view CPU frame work** and **1 ms total extra server tick time for 32 active stations** on the recorded reference machine. These are provisional engineering targets, not guaranteed cross-hardware limits or current measurements. Report measured regressions against the same scene and baseline. Include p95/p99 behavior and a sustained sample after warmup, not only average FPS.

## 19. Implementation order and exit criteria

### Phase 0 — Establish the baseline

- Record HEAD, toolchain and dependency versions.
- Run the existing build/GameTests and capture the current workflow if the environment supports them.
- Inventory current recipes, patterns, material definitions, APIs and migration payloads.
- Record existing failures separately.
- Make the dependency proof from section 12 and write a short architecture decision.

**Exit:** a reproducible baseline and a chosen interaction adapter with demonstrated input/camera cleanup.

### Phase 1 — Physical anvil proof

- Existing anvil, existing tongs/hammer, one persistent billet.
- Collision-safe working view and World view.
- Real surface picking, one accepted strike and one visible shape change.
- Two clients observing the same work.
- Exit/reenter retains progress.

**Exit:** no minigame panel; accepted tool contact and material response agree; no duplicated items.

### Phase 2 — Complete iron sword

- Forge planning/reservation and physical billet preparation.
- Tongs transfer and safe reheating.
- Full blade shaping.
- Physical quench.
- Handle fitting from the reserved stick.
- One final result, quality application, crafting hooks and optional signing.

**Exit:** the entire section-4 sequence works in survival on a dedicated server, including interruption and full inventory.

### Phase 3 — Generalize all recipes

- All 40 existing pattern IDs mapped.
- Material thermal defaults and authorable overrides.
- Armor, shields, ranged hardware, upgrades and generic modded outputs.
- Datapack/resource schemas, fallback reports and JEI/Jade updates.
- Legacy workpiece/forge/quality migration.

**Exit:** every enabled recipe in the runtime audit is either craftable through a documented process or explicitly excluded without breaking its original acquisition path.

### Phase 4 — Finish the workshop experience

- Grindstone, fitting polish, engraving and book navigation.
- Tool/handedness/body-renderer coverage.
- Final models, textures, audio, particles and readable progress cues.
- Keyboard/accessibility controls and updated guide/config migration.

**Exit:** repeated crafting is comfortable; no required action depends on a large modal crafting screen.

### Phase 5 — Release validation

- Test lifecycle, resource conservation, malformed packets and event idempotency.
- Run the compatibility/visual/performance matrix.
- Produce ordinary survival and two-client recordings.
- Remove obsolete default minigame pathways and dead packet registrations only after replacement coverage passes.
- Update documentation, changelog, migration notes and public API timing documentation.

**Exit:** all required checks have real results, known limitations are stated, and the complete experience is demonstrated. Do not call the project complete at Phase 1 or 2.

## 20. Validation plan

### 20.1 Build and existing checks

The inspected repository provides these commands:

```bash
./gradlew compileJava compileGameTestJava
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer -PcompatMods
./gradlew runClient -PsmokeTest
./gradlew build packTestJar
python3 tools/generate_textures.py --check
```

Its repository instructions also define the packaged Ultima gate:

```bash
python3 tools/packtest/ultima_pack_test.py --expect spartan,shields,upgrades
```

Run this only with the required local instance/dependencies available, in the harness’s isolated copy. Adapt the existing multiplayer harness at `tools/visual-test/multiplayer_visual_test.py` using its current help/options and actual installed Forge libraries; do not copy historical machine-specific paths into new documentation.

These commands are **required implementation checks**, not checks claimed to have run for this handoff. Update old smoke assertions so they exercise the new flow. Preserve actual safety/recipe invariants instead of simply deleting failing legacy tests.

### 20.2 Deterministic domain tests

| Test | Observable expected result |
|---|---|
| Same initial state and accepted actions | Same heat/shape/quality result |
| Repeated action sequence | One mutation, one payment and one accepted effect |
| NaN/infinite/out-of-range coordinates | Rejected without state changes |
| Excess strike rate / invalid charge | No faster progress or inflated quality |
| Finished region struck repeatedly | No unlimited score/work gain |
| Reheat | Same job, ingredients and shaping progress |
| Exit/reenter / input-mode change | No score reset or free completion |
| Bad strike then valid correction | Bounded, reproducible improvement |
| Low TPS and jittered arrivals | No deadline ruin; bounded compensation |
| Quality endpoints/legacy scores | Existing multipliers and saved values preserved |
| Recipe reload mid-job | Current job uses its snapshot; next job uses new data |
| Missing visual profile | Gameplay remains valid with fallback |
| Invalid gameplay profile | Specific diagnostic; no partially installed rule set |

### 20.3 GameTests and transaction tests

- Forge: enough/insufficient metal, wrong family, fuel restriction, simultaneous begin requests.
- Cancel before billet: exact refund once.
- Cancel after billet: recover piece; no full refund plus piece.
- Two players try to claim/strike/take the same piece.
- Holders transfer through anvil, tongs, dropped item and recovery.
- Tongs/hammer at final durability; ensure carried content survives break handling.
- Reheat versus deliberate recycle, including consumed-equipment upgrades.
- Quench: empty/insufficient water, start/abort/resume, duplicate completion and destroyed trough.
- Fitting: exact auxiliary NBT, correct remainder, partial progress, output inventory full.
- Refinement: insufficient/changed XP, replaced item, duplicate completion and partial exit.
- Maker’s Mark: wrong owner, changed inventory slot, sanitized text and Faulty restriction.
- Event listener mutates final stack; mutation survives actual collection.
- Crafting/progression/advancement counters increase once.
- Hopper/pipe insertion simulation versus actual insertion; reserved capacity is respected.
- Block break/explosion/unload/save/restart at every commitment boundary.
- Old save fixtures for both workpiece states and all known holders.

Use the real action service and storage where possible. Avoid tests that merely restate a formula using the same implementation helper.

### 20.4 Manual and visual matrix

| Dimension | Required cases |
|---|---|
| Display | 4:3, 16:9, ultrawide; small viewport; all supported GUI scales |
| Settings | Low/high FOV, left/right hand, World view, reduced motion, particles off, subtitles |
| Environment | Each station rotation; low ceiling; tight wall; corner; neighboring blocks; day/night; rain |
| Inputs | Pointer, keyboard, remapped bindings, toggle instead of hold, chat typing |
| Lifecycle | Escape during wind-up; movement exit; damage/knockback; teleport; death; disconnect; resource reload |
| Network | Local, 100–250 ms latency, jitter and temporary stalls; second client and late join |
| Content | Sword, pickaxe, helmet/chestplate, shield, polearm, ranged weapon, custom renderer, generic fallback |
| Mods | Bare Forge, required compat mods, intended renderer/body/camera stack, packaged Ultima |
| Workload | One station, adjacent workshop, 8 and 32 active stations |

Capture actual framebuffer screenshots and short recordings. Still images cannot establish smooth camera transitions, tactile contact, correct effect timing or safe exit.

Required demonstrations:

1. Complete ordinary sword with no forge/anvil minigame screen.
2. Partial shaping, exit, reheat, resume and finish.
3. Armor or a modded weapon with a distinct work profile.
4. Quench followed by actual handle/strap fitting.
5. Paid grindstone refinement and optional inscription.
6. Another client seeing the work.
7. A camera-blocked corner falling back safely.
8. Equivalent successful craft with motion disabled and keyboard/toggle controls.
9. Migration of a saved shaped workpiece without changed scores or extra payment.

If a required runtime environment is unavailable, state that check as **not run**, preserve the executable fixture, and distinguish it from passing checks. Do not substitute a historical screenshot or build log for a new result.

## 21. Definition of done and handback

The coding agent’s final handback must include:

- Implemented source and authored assets.
- A concise explanation of the chosen interaction/dependency architecture.
- Updated guide, recipe/process schema documentation and controls.
- Config and saved-data migration notes.
- Public event timing/API notes.
- Recipe-to-process coverage and compatibility reports.
- Actual build/GameTest results with commands and logs.
- Local and two-client recordings plus representative screenshots.
- Measured performance results and any unresolved limitations.

Final acceptance checklist:

- [ ] Forge and anvil work occur on actual world objects.
- [ ] Cinematic close-up is polished, optional and safe to exit.
- [ ] Tools meet materials, and accepted work causes persistent visible change.
- [ ] Heat, shaping, quench and fitting are readable without floating minigame targets.
- [ ] All current recipe families have a usable process/fallback.
- [ ] Cold or interrupted work can be resumed.
- [ ] Existing equipment, workpieces, signatures and quantities survive migration.
- [ ] Resource transfers and rewards remain authoritative and idempotent.
- [ ] Other players see coherent work without duplicate tools/items.
- [ ] World-view, keyboard, handedness and reduced-motion paths are complete.
- [ ] The grindstone and inscription fit the same interaction language.
- [ ] Performance and compatibility are demonstrated with current evidence.

**The finished experience should feel like operating a smithy: approach the tools, understand the material, work it, and see the result taking shape.**

## 22. Source index

All links below are pinned to the inspected commits. Use them to understand the baseline; compare against current HEAD before implementation.

### Immersive Smithing

| Ref | Source and relevance |
|---|---|
| S1 | [gradle.properties][S1] and [build.gradle](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/build.gradle): actual platform, toolchain and test configuration |
| S2 | [SmithsForgeBlock.java][S2] and [SmithsForgeBlockEntity.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/blockentity/SmithsForgeBlockEntity.java): loading, fuel, melting, storage and escrow |
| S3 | [SessionManager.java][S3], [SessionTiming.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/minigame/SessionTiming.java) and [ModNetwork.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/network/ModNetwork.java): authority, time, cancellation and protocol |
| S4 | [SmithsAnvilBlock.java][S4], [ForgeMinigameScreen.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/client/screen/ForgeMinigameScreen.java) and [AnvilMinigameScreen.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/client/screen/AnvilMinigameScreen.java): current interaction loop |
| S5 | [WorkpieceData.java][S5] and [WorkpieceCodec.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/workpiece/WorkpieceCodec.java): existing durable payload and states |
| S6 | [SmithsTroughBlock.java][S6] and [ItemSmithedEvent.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/api/event/ItemSmithedEvent.java): quench finalization, result mutations and event contract |
| S7 | [SmithsGrindstoneBlock.java][S7]: current refinement payment and score rules |
| S8 | [ServerConfig.java][S8], [ClientConfig.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/config/ClientConfig.java) and [SigningService.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/SigningService.java): defaults, accessibility and signing |
| S9 | [SmithsAnvilRenderer.java][S9], [SmithsForgeRenderer.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/client/renderer/SmithsForgeRenderer.java) and [historical visual report](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/docs/visual-overhaul/REPORT.md): reusable rendering and earlier evidence/limitations |
| S10 | [docs/API.md][S10], [SmithingRecipeSerializer.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/recipe/SmithingRecipeSerializer.java) and [iron sword recipe](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/resources/data/immersive_smithing/recipes/smithing/iron_sword.json): economic and data contracts |
| S11 | [QualityCalculator.java][S11], [QualityData.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/QualityData.java) and [QualityHooks.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/QualityHooks.java): finished-item semantics |
| S12 | [SmithingData.java][S12], [EquipmentClassifier.java](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/minigame/EquipmentClassifier.java), [anvil patterns](https://github.com/otectus/ImmersiveSmithing/tree/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/resources/data/immersive_smithing/immersive_smithing/anvil_patterns) and [CLAUDE.md](https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/CLAUDE.md): resolution, coverage and repository workflow |

### Hands-On Delight and Diegesis

| Ref | Source and relevance |
|---|---|
| H1 | [Hands-On Delight README][H1] and [cutting demonstration](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/docs/showcase/chop.gif): the reference experience |
| H2 | [CookingPotStation.java][H2], [HandsOnDelightClient.java](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/client/HandsOnDelightClient.java) and [cutting_board.json](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/resources/assets/handson_delight/diegesis/scenes/cutting_board.json): server stations, client behavior and scene composition |
| H3 | [ToolStroke.java][H3], [StirSkill.java](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/station/StirSkill.java), [gradle.properties](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/gradle.properties) and [settings.gradle](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/settings.gradle): tool contact, server evaluation and dependency baseline |
| D1 | [Diegesis README][D1], [gradle.properties](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/gradle.properties) and [LICENSE](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/LICENSE): capabilities, target and licensing |
| D2 | [StationType.java][D2], [StationLogic.java](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/api/station/StationLogic.java), [StationBehavior.java](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/api/client/StationBehavior.java) and [CameraRig.java](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/api/scene/CameraRig.java): public extension points |
| D3 | [StationManager.java][D3] and [ActionHandler.java](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/impl/action/ActionHandler.java): inspected implementation behavior; not APIs to depend on |

[S1]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/gradle.properties
[S2]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/block/SmithsForgeBlock.java
[S3]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/minigame/SessionManager.java
[S4]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/block/SmithsAnvilBlock.java
[S5]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/workpiece/WorkpieceData.java
[S6]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/block/SmithsTroughBlock.java
[S7]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/block/SmithsGrindstoneBlock.java
[S8]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/config/ServerConfig.java
[S9]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/client/renderer/SmithsAnvilRenderer.java
[S10]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/docs/API.md
[S11]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/quality/QualityCalculator.java
[S12]: https://github.com/otectus/ImmersiveSmithing/blob/0c5e781f53641a5cc9dcab72acde2a50e55a6fd3/src/main/java/com/otectus/immersivesmithing/recipe/SmithingData.java
[H1]: https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/README.md
[H2]: https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/station/CookingPotStation.java
[H3]: https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/client/ToolStroke.java
[D1]: https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/README.md
[D2]: https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/api/station/StationType.java
[D3]: https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/common/src/main/java/com/pockyl/diegesis/impl/server/StationManager.java



