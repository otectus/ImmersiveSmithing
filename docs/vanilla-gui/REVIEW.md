# Vanilla GUI: before and after

In 1.1.0 every Immersive Smithing screen uses Minecraft's own look in place of the Stoneborn theme (dark stone frames with gilded corners, brown fills, gold headings). The screens draw vanilla GUI textures: the container panel, slots, buttons, text fields, the creative scroller, and the written-book page. JEI uses JEI's own drawables. The theme's textures (`gui/stone.png`, `gui/slot.png`, `gui/guide.png`) are deleted; `tools/generate_textures.py --check` fails if any of them comes back.

## How these were captured

The images are real client screenshots from `./gradlew runClient -PsmokeTest`, which loads the pack-test agent (`src/packTest`), in a 1280 x 720 window with no shader pack.

- **Before:** the baseline run on this working tree before any GUI edit.
- **After:** the final run with the same agent and fixtures, on the plain profile (no weapon mods), so each pair shows the same state.
- **Minimum and scale 4:** for the 320 x 240 minimum, the agent sizes the window to 960 x 720 at GUI scale 3. For GUI scale 4 it sizes the window to 1280 x 960, the smallest framebuffer where Minecraft allows scale 4. The baseline agent had no scale-4 pass, so scale 4 has no before image.
- **Fixtures, not live play:** the agent builds the screens from packets it constructs itself. The live server flow is covered by the GameTests and the Ultima release gate.

## Pairs

| Surface | Before | After |
| --- | --- | --- |
| Forge, recipe selection | ![](before/packtest_forge_select.png) | ![](after/packtest_forge_select.png) |
| Forge, countdown (long names) | ![](before/packtest_forge_countdown.png) | ![](after/packtest_forge_countdown.png) |
| Forge, minigame | ![](before/packtest_forge_play.png) | ![](after/packtest_forge_play.png) |
| Forge, result | ![](before/packtest_forge_result.png) | ![](after/packtest_forge_result.png) |
| Forge, 320 x 240 | ![](before/packtest_forge_long_320x240.png) | ![](after/packtest_forge_long_320x240.png) |
| Forge, GUI scale 1 | ![](before/packtest_forge_long_scale_1.png) | ![](after/packtest_forge_long_scale_1.png) |
| Forge, GUI scale 3 | ![](before/packtest_forge_long_scale_3.png) | ![](after/packtest_forge_long_scale_3.png) |
| Forge, accessibility mode | ![](before/packtest_forge_accessibility.png) | ![](after/packtest_forge_accessibility.png) |
| Anvil, minigame | ![](before/packtest_anvil.png) | ![](after/packtest_anvil.png) |
| Anvil, countdown | ![](before/packtest_anvil_countdown.png) | ![](after/packtest_anvil_countdown.png) |
| Anvil, result | ![](before/packtest_anvil_result.png) | ![](after/packtest_anvil_result.png) |
| Anvil, 320 x 240 | ![](before/packtest_anvil_320x240.png) | ![](after/packtest_anvil_320x240.png) |
| Anvil, accessibility mode | ![](before/packtest_anvil_accessibility.png) | ![](after/packtest_anvil_accessibility.png) |
| Guide | ![](before/packtest_guide.png) | ![](after/packtest_guide.png) |
| Guide, search | ![](before/packtest_guide_search.png) | ![](after/packtest_guide_search.png) |
| Guide, GUI scale 3 | ![](before/packtest_guide_scale_3.png) | ![](after/packtest_guide_scale_3.png) |
| Guide, 320 x 240 | ![](before/packtest_guide_320x240.png) | ![](after/packtest_guide_320x240.png) |
| Maker's Mark | ![](before/packtest_makers_mark.png) | ![](after/packtest_makers_mark.png) |
| JEI category | ![](before/packtest_jei_smithing.png) | ![](after/packtest_jei_smithing.png) |
| Quality tooltip | ![](before/packtest_inventory_quality_tooltip.png) | ![](after/packtest_inventory_quality_tooltip.png) |

These are GUI scale 4 (320 x 240 logical) captures, with no before image:

| Forge selection | Forge minigame | Anvil | Guide | Maker's Mark |
| --- | --- | --- | --- | --- |
| ![](after/packtest_forge_select_scale_4.png) | ![](after/packtest_forge_play_scale_4.png) | ![](after/packtest_anvil_scale_4.png) | ![](after/packtest_guide_scale_4.png) | ![](after/packtest_makers_mark_scale_4.png) |

With Epic Fight and Simply Swords loaded (`-PepicFight -PsimplySwords`), the JEI category lists their forged weapons. The first page shows the Epic Fight glove on the Cestus pattern: ![](after/with-weapon-mods/packtest_jei_smithing.png)

## What each screen became

- **Forge:**
  - A vanilla container panel, a black vanilla search field, and recipe rows drawn as vanilla buttons, as in a villager's trade list. Scrolling uses the creative-inventory scroller.
  - Each row shows the result, its metal cost, and the components it takes.
  - The timing track and the readouts sit in dark display boxes, so the zone colours keep their contrast on the light panel.
  - The result stage ends on a vanilla Done button.
- **Anvil:** the same panel, with the board in a dark display. The right-hand column holds the time and strike bars, the efficacy readout, and the aiming hint.
- **Guide:**
  - The chapter list sits in a vanilla panel, with a search field and a scroller.
  - The page is drawn on the written-book texture, with the book's page arrows, its "Page x of y" line and a Done button.
  - Below the two-pane width the guide shows one pane at a time, switched with Contents, Read and Done buttons.
- **Maker's Mark:** vanilla text fields, each with a character counter that turns dark red at the server's limit. The piece sits in a vanilla slot, and Sign and Leave unsigned are vanilla buttons.
- **JEI:** standard slots, the output slot and JEI's recipe arrows on a plain background, with dark grey notes for material, pattern and quality.
- **Tooltips and overlays:** the vanilla tooltip, as before.
- **Unchanged:** accessibility mode (high contrast, colourblind-safe targets, large targets), keyboard control, translation keys and the 320 x 240 minimum. `WorkshopScreen` still scales one fixed-size canvas, so mouse input and drawing share coordinates.

## Review findings fixed during this pass

1. **Empty forge panel.** The forging stages (countdown, minigame, result) kept the full panel height of the recipe list and left about the bottom 40% empty. They now share one compact height, fitted to the tallest of the three stages, so the panel holds still from countdown to Done.
2. **Cost wording.** Recipe rows read "Costs 3 ingots metal · 9 ingots remains". They now read "Costs 3 ingots · 9 ingots left". The old wording predates this change.
3. **Maker's Mark margin.** The buttons sat 6 px from the panel's bottom edge, against 8 px at the sides. They now match.
4. **Guide page with only a title.** In the new book layout, a page could carry just the chapter title, orphaned from its first section. The smoke test caught it. Pagination now keeps the title with the section that follows it.
5. **GUI scale 4 was never rendered.** At 1280 x 720, Minecraft caps the scale at 3, so the agent's earlier scale checks stopped there. The new pass resizes the window first, because Minecraft clamps the scale option to what the current window allows. It then renders the forge, anvil, guide and Maker's Mark at scale 4, and the run fails if the window does not reach 320 x 240 logical at that scale.
6. **Accessibility settings could stay switched on.** The agent turns accessibility mode on for two captures and then restores the player's settings. Forge autosaves each option, and its file watcher can reload an older copy of the file between two quick writes. In one run that kept high contrast switched on after the restore, while the agent still reported success. Now each setting is written only if it differs and re-checked on later ticks. `accessibility_settings_restored` reports the values actually read back at the end of the run.

## Checks in the final runs

Both runs ended with `pass: true` in `run/packtest/packtest-result.json`:

- **Plain profile:** `./gradlew runClient -PsmokeTest`, 5 mods.
- **Both weapon mods:** `./gradlew runClient -PsmokeTest -PepicFight -PsimplySwords`, 11 mods.

Checks covered in both runs:

- **Viewport and scales:**
  - The minimum viewport is exactly 320 x 240: `gui_minimum_probe`, `gui_fitted_minimum_viewport`.
  - GUI scales 1, 2, 3 and 4 rendered: `gui_scale_1` through `gui_scale_4`, `gui_scale_4_rendered`.
- **Guide navigation:** search, keyboard and pagination work in both guide layouts: `guide_search_keyboard_pagination_both_layouts`.
- **Accessibility:** the mode was applied for its captures, and the player's settings were restored afterwards: `accessibility_mode_applied`, `accessibility_settings_restored`.
- **Other screens:**
  - The JEI category opens.
  - A resource reload completes with the screens intact.
  - The inventory quality tooltip renders.
