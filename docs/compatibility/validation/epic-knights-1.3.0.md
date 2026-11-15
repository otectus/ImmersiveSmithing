# Epic Knights compatibility — 1.3.0 validation

Completed on branch `update/1.3.0`; changes remain uncommitted. Existing committed and uncommitted casting work is retained.

## Implemented behavior

- Active-recipe policies and exact suppression, bounded rational component valuation, distinct alternate routes, reload idempotence, provider publication and pack override precedence.
- Exact reserved equipment bases, complete auxiliaries, personal-data transfer, proportional wear, new structural-upgrade quality/maker and exactly-once escrow refund. Knight chestplate: halfarmor plus 45 additional steel units; handoff corrected to the pinned 10.15 jar.
- Curated item roles and guarded addon mappings; native alloys, ammo, assemblies, cosmetic transformations, visors, unique loot and unavailable content remain explicit native dispositions.
- Quality-preserving native customization and server-validated visor packets; native pavise and horse-armor lifecycles; real blocking-weapon, mounted, offhand, Better Combat/Rework and Epic Fight damage paths.
- Pinned Thirty Years War hitscan, Fletchery, Artilleries and both Boomsticks launch/hit adapters. Actual weapon snapshots survive delayed launch, hand changes, save/load and thrown recovery; destructive operations reject loaded weapons.
- Overgeared, TerraFirma and TerraFirmaGreg ownership prevents forge/salvage/progression bypasses and duplicate equipment effects.
- Complete-input forge/JEI presentation, input-derived previews, diagnostics, five-language guide/tooltips, API documentation, packet protocol 5 and backward-compatible workpiece version 2.

## Verification outcome

All **30 final installation profiles passed**. Normal suites contain 134 or 135 tests (the latter adds direct native elemental-effect checks); external workflows use three dedicated tests. Tests for absent optional mods return early. These counts are not a claim that every addon case runs in the plain profile.

Base and combined were rerun after publishing their fixtures and passed the fixture comparison. Other exports preserve the passing runtime catalogs; they are not described as additional fixture-comparison runs.

| Profile | Required tests passed | Final log |
| --- | ---: | --- |
| `plain` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-194958.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-194958.log) |
| `compat` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-195049.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-195049.log) |
| `base` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202512.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202512.log) |
| `addon` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202113.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202113.log) |
| `antique` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202143.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202143.log) |
| `slavic` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-195621.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-195621.log) |
| `darkages` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-195717.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-195717.log) |
| `japanese` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-195813.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-195813.log) |
| `oresalloys` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-195910.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-195910.log) |
| `ingots` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200007.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200007.log) |
| `iceandfire` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200055.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200055.log) |
| `thirtyyearswar` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200158.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200158.log) |
| `fletchery` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200235.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200235.log) |
| `artillery` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200323.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200323.log) |
| `boomsticks` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200408.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200408.log) |
| `boomsticks-release` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202226.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202226.log) |
| `sevenkingdoms` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-200933.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-200933.log) |
| `bettercombat` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201017.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201017.log) |
| `rework` | 134 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201111.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201111.log) |
| `armor` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201200.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201200.log) |
| `materials` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201301.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201301.log) |
| `materials-provider` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201347.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201347.log) |
| `ranged` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201442.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201442.log) |
| `visors` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201529.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201529.log) |
| `visors-addon` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201605.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201605.log) |
| `epicfight` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201651.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201651.log) |
| `combined` | 135 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202534.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202534.log) |
| `overgeared` | 3 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-202250.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-202250.log) |
| `terrafirma` | 3 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201815.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201815.log) |
| `terrafirmagreg` | 3 | [gradle-ImmersiveSmithing-runGameTestServer-20261031-201846.log](/tmp/gradle-ImmersiveSmithing-runGameTestServer-20261031-201846.log) |

The static mod check passed with 0 errors, 0 warnings and the same 13 notes for loop-registered tool models. The texture/model check and generated-data check passed. Full build and packaged agent build passed.

Production artifact: [`immersive-smithing-1.3.0.jar`](/home/otectus/Projects/ImmersiveSmithing/build/libs/immersive-smithing-1.3.0.jar), **1,513,986 bytes**, SHA-256 `5c8912b99e9c6c11768fbaeb61b89af53594ea6831fe3f4d89f5056dc30681ce`.
Its hash matches the production jar used in the successful Ultima and multiplayer gates. Later test-fixture/reporting changes do not invalidate those production-jar checks.

## Runtime delivery

| Gate | Result | Evidence |
| --- | --- | --- |
| Plain production client | PASS | [summary](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-1-3-0-plain-client-retry/summary.json) |
| Open Helms + Swappable Helms + FoxRall, without Addon | PASS | [summary](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-1-3-0-visors-client/summary.json) |
| Same visor stack + Addon + Thirty Years War/Fletchery/Artilleries | PASS | [summary](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-1-3-0-visors-addon-final/summary.json) |
| Dedicated server and two real clients | PASS | [summary](/home/otectus/Projects/ImmersiveSmithing/build/visual-test/epic-knights-1.3.0-upgrades/summary.json) |
| Isolated Ultima release gate, 350 pack jars plus pinned base Epic Knights | PASS | [summary](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-knights-1.3.0-gate/summary.json) |

Multiplayer exercised authoritative personalized upgrade selection, a hostile second-client select/cancel attempt, resource reload during escrow, recipe synchronization, preview state, repeated cancellation and one exact full-stack refund. Both clients passed. The real Ultima instance/world was not modified.

Screenshots were inspected for forge selection and upgrade inputs, personalized previews, JEI, long equipment, decorated armor, shields, firearms, native horse armor and closed/open first-person visor overlays. The final dedicated client captures correct three earlier fixture presentation errors (creative inventory, synthetic material translation, wrong JEI category). Ultima’s runtime assertions remain valid; its older gallery captures are not the final presentation evidence.

- [Final Epic Knights screenshots](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-1-3-0-visors-addon-final/screenshots)
- [Two-client screenshots](/home/otectus/Projects/ImmersiveSmithing/build/visual-test/epic-knights-1.3.0-upgrades)
- [Ultima real-window captures](/home/otectus/Projects/ImmersiveSmithing/build/packtest/epic-knights-1.3.0-gate/captures)

## Coverage and measurements

[Coverage exports and pinned manifests](../epic-knights-1.3.0/README.md) contain every registered target item and route disposition. All seven exports have **zero unresolved rows**. The combined profile has 1,835 registered items and 1,977 route rows, including six input-dependent native processes. Separate exports cover material providers, release Boomsticks and each external owner.

The final combined snapshot has 735 smithing recipes, 13 material families and 735 exact suppressed sources. Its real smithing-only recipe-sync packet is 290,298 bytes; serialization took 8,182 microseconds in this run. The first startup rebuild in the final log took 236 ms. These are measured observations, not a benchmark guarantee or the entire native manager/heap size.

## Failed attempts and remaining limits

- Early failures exposed real adapter defects and test-fixture assumptions: obsolete costs, complete-state inheritance, server visor validation, Artilleries delayed launch/throw paths and fake-player lifecycle setup. The command ledger retains them and the passing replacements.
- The first final-matrix base/Addon checks failed because a test compared different Java collection implementations. The corrected semantic comparison passed. Antique was interrupted during a ForgeGradle HTTPS startup probe before tasks; cached offline dependencies plus a temporary init script disabled that probe only. TLS downloads were not disabled.
- One combined startup failed before tests in upstream Architectury concurrent creative-tab registration (Thirty Years War constructor). Both subsequent combined runs passed. This observed upstream race is not patched or claimed repaired.
- Two client wrappers initially reported false failures: plain clients lack FancyMenu’s startup log; malformed Addon recipe parsing placed the test launcher deeper in a vanilla stack. The harness now recognizes the agent’s actual TitleScreen observation and attributes the native resource error correctly. Own-code, own-resource and direct-agent failures remain rejected. Final client reruns passed.
- Known upstream invalid recipes/assets remain listed in [support limits](../epic-integrations.md#upstream-limitations), including Addon’s obsolete smithing serializers, Japanese/Ingots missing or invalid recipes, Artilleries/Fletchery model resources, Addon + Ice-and-Fire AutoConfig conflict, placeholder items and missing default visor pairs.
- TerraFirmaGreg’s pinned bridge references unavailable `gtceu:cast_iron`. Its coexistence tests pass; this does not certify the pack’s metallurgy. No Forge Ice and Fire Community Edition combination is claimed. Unknown versions remain outside the verified version set.
- No implementation gate is left unrun. Upstream limitations above remain unresolved and are not hidden as integration passes. No commits were created.

## Complete commands

[Human-readable command ledger](epic-knights-1.3.0-commands.md) and [machine-readable argument/result records](epic-knights-1.3.0-commands.json) include development failures, all profile attempts, build/static/generator checks, client/multiplayer/Ultima runs, coverage generation and final file validation. Each entry identifies its result and retained log/artifact path.

Final file checks:

- `python3 /tmp/immersive-smithing-epic-knights-audit/finish_validation.py` — PASS: 1340 new/changed JSON files parse; coverage and provenance consistency verified; [log](/tmp/immersive-smithing-epic-knights-audit/changed-json-check.txt).
- `git diff --check` — PASS; [log](/tmp/immersive-smithing-epic-knights-audit/final-diff-check.txt).
