# Visual review and refinement

Reviewed against the working tree as supplied, on Minecraft 1.20.1 / Forge 47.4.23. Existing uncommitted implementation and compatibility work was preserved. The baseline was captured with the real client before edits; comparisons use the same acceptance fixture, with naturally varying particle timing.

The review covered all 31 texture exports, four stations and their moving parts, all 13 workpiece profiles, tool tiers and loaded tongs, inventory/held views, the work HUD, Pattern Book, Maker's Mark, guide, JEI, notices, particles and camera framing. Twenty-two texture exports changed: all 15 block materials, three item sprites and four spark frames. The remaining tool silhouettes, loaded overlay, hot-item glow and legacy GUI atlas were retained after inspection.

| Finding | Resolution |
| --- | --- |
| Flat stone rectangles, heavy black plank grooves and empty metal surfaces looked inconsistent beside vanilla blocks. | Reworked the editable pixel source with narrow mortar, chipped stone clusters, interrupted oak grain, end grain and restrained iron scuffs. All material textures remain 16×16, with hand-selected palettes and no filtering or random noise. |
| Anvil bevels and wheel rings were painted outside the regions actually sampled by the models. | Aligned the artwork with the model UVs. The wheel's ring is centred on its axle within its 10×10 sampled area. |
| Coal read as a bright slab; molten metal had large rectangular islands. | Dark charcoal with small ember seams, and connected oxide clusters with four subtle animation frames. |
| Netherite's broad gold stripe overwhelmed its material; the guide emblem was indistinct; sparks resembled flags. | Restrained netherite facets/collar, clearer diamond facets, a small hammer emblem, and compact shrinking spark pixels. |
| Every workpiece segment repeated the same UV patch, creating regular bands and seams. | Object-space UVs continue across segments at the station's texture density. Atlas coordinates remain bounded for oversized resource-pack profiles. |
| Dished plates had disconnected top strips, flat caps/bottoms and centres that could pass below the anvil. | Joined upper and lower facets, matching end caps, preserved sheet thickness and raised rims. Surface picking and chalk use the same piecewise profile. Curved/lifted segment transitions are closed. |
| The wheel had a coarse cross-shaped outline, and the sword stood upright above it. | Five stepped courses form a rounder silhouette; matching cached collision/picking geometry. The displayed item now lies across the contact surface. The camera better exposes the working edge. |
| The hammer overwhelmed the work, and tools pivoted around sprite centres instead of contact points. | Smaller hammer, head-anchored strike pose, jaw-anchored bare tongs and steeper grips that clear station rims. Local tool material follows the held tongs. The station's workpiece remains the only displayed piece. |
| A fast server acknowledgement could begin rebound before the hammer reached the metal; effects arrived during wind-up. | Rebound cannot start before contact. Accepted local impact effects wait for the predicted contact time; remote effects remain immediate. This changes presentation only. |
| The forge rim hid the billet tray in the working view. | Steeper forge framing exposes the tray and its heat zones while retaining camera obstruction checks. |
| Steam obscured the entire trough and piece; bellows flames obscured the tray. | Smaller, shorter-lived vanilla cloud sprites and a lower emission rate; restrained small flames on pumping. Existing distance, particle and accessibility budgets still apply. |
| HUD bars could run into long labels; small views lost controls or the selected part. | Wrapped labels above full-width bars, bounded panels, current part near the top, optional detailed checklist, and a stable control legend. Small screens use a wider footer; notices respect that footer. |
| Three old notices covered the apparatus, including behind menus. | Focused work shows the latest notice; normal menus suppress the overlay. Normal world notices and narration remain available. |
| Pattern Book hover prose covered neighbouring rows; a long material total could consume the title. | Kept all costs/components and upgrade consequences in the tooltip; moved repeated new-equipment instructions to help. Bounded both header columns, with full text on hover. |

The container frames, slots, buttons, scrollbars, written-book pages, Maker's Mark fields and JEI layout already use Minecraft resources and read consistently with vanilla. They were retained. The water surface continues to use Minecraft's biome-tinted animated water. First/third-person item views and resource reloads were checked by the client fixture.

Geometry remains bounded: the four stations, including both moving meshes, use **40 cuboids / 192 faces**. The wheel adds two cuboids and fourteen faces, staying below the previous combined face ceiling of 228. The model checker now includes the moving meshes rather than only the static blocks. All 222 authored model faces have 1:1 UV spans; the grindstone's cached collision decomposition remains within the existing 12-box ceiling in every facing.

## Evidence

- [Texture comparison](textures-before-after.png)
- [Forge comparison](forge-before-after.png)
- [Anvil comparison](anvil-before-after.png)
- [Quench comparison](quench-before-after.png)
- [Grindstone comparison](grindstone-before-after.png)
- [Small HUD comparison](hud-before-after.png)
- [All workpiece profiles](workpiece-profiles.png)

Complete baseline and final screenshots/results are retained locally under `build/visual-review/before/` and `build/visual-review/after/`. The pre-edit source/assets snapshot is `build/visual-review/baseline.tar`; `changed-from-baseline.txt` identifies this task's changed files separately from the pre-existing dirty tree.

## Validation

Commands use `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh /home/otectus/Projects/ImmersiveSmithing` for Gradle tasks.

- `python3 tools/generate_textures.py --check`: all 31 exports match their source; 222 face UV checks and five emissive item-model checks pass.
- `python3 tools/model_preview.py --check`: static/moving budgets, model references and texture references pass.
- `python3 /home/otectus/Projects/.mcmod-tools/check_mod.py /home/otectus/Projects/ImmersiveSmithing`: zero errors and zero warnings. Thirteen informational notes concern dynamically registered tier models and auxiliary loaded-tongs models.
- `build packTestJar runGameTestServer`: build/package pass and all **145 required GameTests pass**. Log: `/tmp/gradle-ImmersiveSmithing-build-20261114-003653.log`. One earlier assertion sampled the old wheel shoulder boundary; it was updated to test the new rounded shoulder and empty corner explicitly.
- Client validation covers a complete hands-on smithing workflow, all four station facings, day/night, GUI scales 1–4, a 320×240 logical canvas, accessibility modes, first/third-person tools, JEI, resource reload, and a 32-active-station frame measurement. The added gallery checks all 13 profile surfaces and picking at zero, half and full shaping.

- Final `runClient -PsmokeTest build packTestJar`: **passed**, 193 seconds. The [client result](../../build/visual-review/after/result.json) reports `all visual, data and runtime checks passed`, including `workpiece_surface_geometry_and_picking`. [Client/build log](../../build/visual-review/logs/gradle-ImmersiveSmithing-runClient-20261114-004406.log) · [GameTest log](../../build/visual-review/logs/gradle-ImmersiveSmithing-build-20261114-003653.log).
- Final artifacts: [production mod](../../build/libs/immersive-smithing-1.3.0.jar) and [separate acceptance fixture](../../build/libs/immersive-smithing-1.3.0-packtest.jar). The profile gallery is test-only and is excluded from the production mod.
- `git diff --check`: passed.

Scope of validation: the default renderer and the dev JEI runtime were exercised. Shader packs, external texture packs and a new two-client multiplayer session were not exercised in this visual pass. No release was published or installed into a user's game instance.
