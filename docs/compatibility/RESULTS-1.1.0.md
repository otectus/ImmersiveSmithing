# Verification results: 1.1.0

This covers the Epic Fight and Simply Swords integration and the vanilla GUI. Everything was run on 2026-10-10 against
the working tree on `0c5e781` plus the uncommitted 1.1.0 changes. The environment was:

- Minecraft 1.20.1, Forge 47.4.23, OpenJDK 17.0.19
- Linux under Hyprland, with an NVIDIA GeForce RTX 4050 Laptop GPU

The commands are the ones in `CLAUDE.md`. The evidence paths are in the repository unless they start with `/tmp`.

## Release artifact

| | |
| --- | --- |
| File | `build/libs/immersive-smithing-1.1.0.jar` (reobfuscated, `./gradlew build`) |
| Version | `1.1.0` (`mod_version` in `gradle.properties`, expanded into `mods.toml`) |
| Size | 824,595 bytes, 954 entries |
| SHA-256 | `a698bf951d3a7dc896b50e79a988fc8fd7bf7ec14d3031aa4db675aead08e5a7` |
| Contents checked | Mandatory dependencies are only Forge `[47,)` and Minecraft `[1.20.1,1.20.2)`; JEI and Apotheosis are optional. There is no pack-test agent, GameTest code, Epic Fight or Simply Swords class, or retired Stoneborn class or texture. The refmap maps `CraftingMenuMixin` to `m_150546_` (`slotChangedCraftingGrid`) and `m_6836_` (`ResultContainer.setItem`). |

## Pinned optional mods

These are the release jars the tests ran. They are the production builds from Modrinth, as Gradle downloads them.

| Mod | Version | Modrinth version | SHA-256 |
| --- | --- | --- | --- |
| Epic Fight | 20.14.17 | `KEBfkBat` | `69566cf70ae2d91d3f2564c608f014c87e290cef6215c2a27719851165485f73` |
| Simply Swords | 1.70.2-1.20.1 | `Na6e94J1` | `642e385ffda914f1b9411f0a457fe5fb5e05aa160dc44392dec9a549c2bcec21` |
| Architectury API (Simply Swords) | 9.2.14 | `1MKTLiiG` | `218b471d0b8a1f6cda14cfc1beb9eeb0df54304500acc6c5613d9b88ec65d9af` |
| Fzzy Config (Simply Swords) | 0.7.7+1.20.1+forge | `53kg5uoF` | `c4acf6fd3ce66ff28050dd9fdbff25c407f98a4911311ff3f59d651acccc9575` |
| Kotlin for Forge (Fzzy Config) | 4.12.0 | `Zsh14XeQ` | `6a66c6a68ec09f8717cdbf06883394f0ee24d7cd826b7134a0f81839e8e720ae` |
| Simply Tooltips (Simply Swords, client only) | 0.1.5-1.20.1 | `s87jNabF` | `a50357b4982132522f48afc6fa625c453359390f8180e4c72f6288ecd46dee3c` |
| Spartan Weaponry | 3.2.1 | `4U6gwadt` | `2736ac2dd52a7b3a188df454e61a4154cd36e273fa770f3ab89dcf6e732e88ad` |
| Spartan Shields | 3.1.1 | `kDAJWHFl` | `920368908e26e1a75186e06affd03274d0fe93770d5c5d12c177f53d7aa1deac` |
| Immersive Armors | 1.7.1+1.20.1 | `Ivcp8xyi` | `269293a11a13a3bd01bd91ee2817a0ce5740a8ed6eccaec5765c420230717f5d` |

## Results

| Area | Profile | Command | Result |
| --- | --- | --- | --- |
| GameTests | plain | `./gradlew runGameTestServer` | 63/63 passed. 28 explicit, 3 automatic, 31 disabled. |
| GameTests | Spartan Weaponry, Spartan Shields, Immersive Armors | `./gradlew runGameTestServer -PcompatMods` | 63/63 passed. The explicit (126), automatic (19) and disabled (145) recipe sets are identical to the run before any change. |
| GameTests | Epic Fight | `./gradlew runGameTestServer -PepicFight` | 63/63 passed. 16 Epic Fight forge recipes. |
| GameTests | Simply Swords | `./gradlew runGameTestServer -PsimplySwords` | 63/63 passed. 46 Simply Swords forge recipes. |
| GameTests | both | `./gradlew runGameTestServer -PepicFight -PsimplySwords` | 63/63 passed. The live catalogs match `docs/compat/epicfight.json` and `simplyswords.json`. |
| Client, dev | plain | `./gradlew runClient -PsmokeTest` | Passed, 5 mods. All screens and states; 320 x 240 exact; GUI scales 1 to 4; accessibility applied and restored; guide search and paging; JEI; resource reload. |
| Client, dev | Epic Fight and Simply Swords | `./gradlew runClient -PsmokeTest -PepicFight -PsimplySwords` | Passed, 11 mods. The same checks, plus 9 integration checks: Epic Fight's longsword at 36 units with its crafting recipe replaced, its spear remake kept and the uchigatana graded; Simply Swords' twinblade pattern and replaced crafting, the runic reroll kept, and a unique graded but not recyclable. |
| Release gate, production jar | Ultima, isolated copy | `./gradlew build packTestJar`, then `python3 tools/packtest/ultima_pack_test.py --label weapons-vanilla-gui-1.1.0-final --mod-jar build/libs/immersive-smithing-1.1.0.jar --expect spartan,shields,upgrades,cataclysm,dragonsteel,botania,signing,loot` | Passed (exit 0, `freeze=OK`). Every agent check passed with 377 mods. No stack traces, errors or crash reports from this mod. |
| Dedicated server, production jar | plain | A fresh Forge 47.4.23 server from the official installer (`forge-1.20.1-47.4.23-installer.jar --installServer`; SHA-256 `75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a`, which matches Forge's published value), with a flat world and console commands, driven by a local test script that is not checked in | Passed. Started in 46 s. 28 explicit recipes and 28 disabled. No client class loaded, no error from this mod, clean stop. Mixin's class export shows `CraftingMenuMixin` and `ItemStackMixin` merged. Evidence in `build/prodserver/plain/`. |
| Dedicated server, production jars | Epic Fight, Simply Swords, Architectury, Fzzy Config, Kotlin for Forge | The same | Passed. Started in 55 s. 62 automatic recipes, 87 disabled, 18 kept. `/immersivesmithing catalog epicfight` (36 items) and `simplyswords` (133 items) match `docs/compat` field for field. The same error, client-class and mixin checks pass. Evidence in `build/prodserver/weapons/`. |
| Two clients, production jar | dedicated server and two clients | `python3 tools/visual-test/multiplayer_visual_test.py --work-dir build/visual-test/two-client-1.1.0 --server-libraries build/prodserver/base/libraries` | Passed. Loaded-tongs and hammer swings sync to the other client, and taking a workpiece from the anvil clears it on both clients. 10 screenshots; no error from this mod in the server or either client. It ran the final jar (same SHA-256). |
| Control, pre-change jar | Ultima, isolated copy, same session | `python3 tools/packtest/ultima_pack_test.py --label control-pre-change-1.0.0 --mod-jar build/libs/immersive-smithing-1.0.0.jar --expect ...` (its own agent) | Passed. Used for the comparisons below. |
| Assets | | `python3 /home/otectus/Projects/.mcmod-tools/check_mod.py .` | 0 errors and 0 warnings. 13 notes, all false positives: models of items registered in a per-tier loop. |
| Artwork | | `python3 tools/generate_textures.py --check` | Verified 30 textures and 546 model faces at 1:1 UV; none of the retired theme textures is present. |

The smoke-test runs record their checks in `run/packtest/packtest-result.json`. The screenshots are in
[`docs/vanilla-gui/`](../vanilla-gui/REVIEW.md). The release-gate evidence is in
`build/packtest/weapons-vanilla-gui-1.1.0-final/`: `summary.json`, `client.log`, `packtest-result.json` and
`immersive_smithing_report.txt`.

## Release gate: what changed in Ultima

The comparison is the pre-change jar (1.0.0, the Stoneborn build) against the final jar, in the same Ultima copy and
the same session. Only this work differs between the two.

- **Explicit recipes** are identical (326).
- **Eleven items are no longer forged.** Their native recipes are restored, and the report gives each reason:
  - Ten are repaired with something that is not metal (`not predominantly metal: its repair material is not metal`):
    - Alex's Mobs moose headgear;
    - Botania's glass pickaxe;
    - Cataclysm's azure sea shield;
    - Ice and Fire's hippogryph sword;
    - Iron's Spells' boreal blade, spellbreaker and twilight gale;
    - Legendary Delicacies' withered knife and Legendary Monsters' fiery jaw;
    - Mowzie's Mobs' naga fang dagger.
  - Botania's Terra Shatterer is a custom `botania:mana_upgrade` recipe, which moves mana between items, so it is
    never converted.
- **Four remakes gained forge recipes.** Legendary Delicacies' frost knife and knife of lightning, and Legendary
  Monsters' chorus blade and the great frost, can now be forged from the piece's metal plus every component. Their
  native remakes are kept, and no native recipe was disabled for them.
- **No recipe changed form**, and no native recipe is disabled without a forge recipe for its item (all 442 disabled
  recipes have one).

Against the last passing gate before this work, `build/packtest/integration-1.1.0b` (24 September, same expectations),
the counts also moved because the pack changed: 377 mods load now, 411 then. The 80 extra explicit recipes are
`immersive_jewelry:smithing/...` recipes, which Immersive Jewelry ships for this mod's recipe type. Butchery's copper,
gold and iron tools are newly detected.
- **A regression found and fixed.** The first run of this gate showed Iron's Botany's manasteel wizard armour priced
  as a 36–72 unit manasteel shape. That armour is Iron's Spells cloth built on leather armour. It must instead rework
  its base for one ingot, as before. Fixed in `AutoRecipeDetector.baseShape` and pinned by
  `clothBasesAreReworkedNotPricedAsMetal`. With the old rule, exactly that test fails ("36 units"). After the fix, no
  automatic recipe differs in form from the baseline.
- **Frame times** (32 stations in view, vsync off, average fps):

  | Jar | Empty stations | Active stations |
  | --- | --- | --- |
  | pre-change 1.0.0 | 144 | 175 |
  | final 1.1.0 | 150 | 232 |

  - **Same conditions, no regression.** Both runs used the same GPU with the window visible (`freeze=OK`), and the
    p95 frame interval was 11.2 ms and 11.1 ms in the empty scene.
  - **The 24 September numbers aren't comparable.** That run rendered on the Intel iGPU; these render on the NVIDIA
    dGPU through XWayland.
  - **Window visibility swings the numbers.** On this desktop the game window opens on Hyprland's hidden special
    workspace `gaming`, where the compositor throttles it. The first 1.1.0 gate run measured only 71 and 102 fps that
    way, and its freeze check could not observe the window (`UNOBSERVED`).

## Not covered

- **Epic Fight combat.** Battle-mode and off-hand hits are verified in GameTests through Epic Fight's own server-side
  attack path. No person played in battle mode on a client.
- **Simply Swords abilities.** Abilities scaled by attack damage are verified through its `abilityScaledDamage`.
  Abilities scaled by spell power don't read attack damage, are not affected by quality and were not tested.
- **Production clients with the weapon mods.** Clients with Epic Fight and Simply Swords ran in the dev environment,
  where the Simply Swords jars are remapped by `tools/devmods`. For those two mods, the production coverage is the
  dedicated server. Ultima contains neither mod, so the release gate covers the jar without them.
- **Smoke-test timing.** The client smoke tests ran before the final `baseShape` fix. That fix changes only how
  upgrade bases nested in recipes are priced. The final GameTests show that the vanilla, Epic Fight and Simply
  Swords recipe data is unchanged.
- **Screens and window sizes.**
  - The screenshots come from screens fed by the agent's own fixtures; the live flows are covered by the GameTests
    and the release gate.
  - The window sizes were 1280 x 720 (scales 1 to 3), 960 x 720 (320 x 240) and 1280 x 960 (scale 4). No other aspect
    ratio was captured.
