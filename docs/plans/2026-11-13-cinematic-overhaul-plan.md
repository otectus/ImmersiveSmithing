# Cinematic, hands-on overhaul: implementation plan (2026-11-13)

Source: `Immersive_Smithing_Cinematic_Hands_On_Overhaul_Handoff.md` (repository root). Implemented on branch
`update/1.3.0`; the version stays `mod_version=1.3.0` (the request said "1.2.0", but 1.2.0 already shipped in
`afddd4c` and the working tree is the unreleased 1.3.0 update).

## 1. Baseline (Phase 0)

| Item | Handoff snapshot | Working tree |
|---|---|---|
| HEAD | `0c5e781` (1.0.0) | `9ff32f3` + uncommitted 1.3.0 Epic Knights work |
| Network protocol | "1" | "5" |
| Workpiece NBT | version 1 | version 2 (`RecoverableUnits`) |
| Anvil patterns | 40 | 44 (`chakram`, `flail`, `twinblade`, `warglaive` added) |
| Forge 47.4.23, Java 17, MC 1.20.1 | same | same |

The new workpiece schema is therefore **version 3**, the protocol **"6"**.

## 2. Architecture decision: native Forge adapter, not Diegesis

Diegesis 1.0.0-beta.9 (MIT, CurseForge 1790227, Forge range `[47,)`) was evaluated against section 12 of the handoff.
Blockers found without needing a runtime proof:

1. **State ownership.** A Diegesis station is a `StationType` bound to blocks whose block entity implements its
   `StationHost`; slots and controls are declared in its `StationSchema`, persisted and synchronised by the library
   ("once somebody works at the station the server's state takes over"). Our forge escrow, anvil workpiece and the
   new quench/grind holders already have versioned, GameTested persistence and conservation rules. Routing them
   through library slots would either make two owners of one stack (forbidden by the handoff) or move saved-world
   item persistence into a beta library's format.
2. **Hard runtime dependency.** Our block entities would have to implement library interfaces, so every client and
   server (Ultima included) would need a beta library with exact version matching. A missing jar would fail at class
   loading, not degrade.
3. **Distribution.** No Maven repository; Hands-On Delight builds it with `includeBuild('../diegesis')`. Only
   CurseForge/Modrinth jars exist (usable through `curse.maven`, but no published API/source artifact contract).

Decision: one native Forge focus/scene adapter with the Smithing domain contracts of the handoff. No Diegesis or
Hands-On Delight code or assets are copied; the behaviour is implemented independently.

## 3. Layers

| Layer | Package | Classes |
|---|---|---|
| Domain rules (pure, deterministic, integer state) | `work` | `ThermalProfile`, `ThermalRules`, `ProcessProfile`, `ShapingRules`, `QuenchRules`, `WorkQuality`, `ProcessState`, `WorkStage`, `StationLayout`, `WorkAction`, `ActionResolver` |
| Data | `work` | `ProcessProfiles` (+ loader, built-ins, legacy pattern map), `ProcessResolver` |
| Authoritative services | `work` | `WorkLeases`, `SmithingActionService`, `SmithingCompletionService`, `ForgeJob`, `WorkClock`; `minigame/SessionManager` keeps only the pattern book and casting |
| Client interaction | `client/work` | `FocusController`, `WorkCamera` (+ `mixin/client/CameraMixin`), `WorkFocusScreen`, `WorkSurfacePicker`, `WorldViewInput`, `WorkKeys`, `WorkHud`, `PatternBookScreen` |
| Presentation | `client/work`, `client/renderer` | `WorkpieceRenderer`, `WorkpieceVisuals` (resource reload), `SceneTool`, station BERs, `StationEventEffects` |

Rules: one C2S action packet (`WorkActionPacket`) for both camera modes; the client resolves the action with the same
`ActionResolver` the server re-runs; server never accepts outputs, scores, heat or NBT from clients.

## 4. Player flow (iron sword: 18 units + 1 stick)

1. Load metal (basin) and fuel (firebox), ignite: unchanged.
2. Pattern book on the forge (compact screen): choose sword; plan is pinned beside the tray, nothing reserved.
3. Commit at the tray with tongs: metal reserved, stick escrowed (supply bundle visible).
4. Billet on the tray, 3 thermal regions. Bellows raise air (bounded, decays); turning rotates exposure; draw out with
   tongs once mean heat is workable. Units leave the reservoir; heat preparation is recorded.
5. Carry in tongs (cools per simulated tick only). Place on the anvil.
6. Aimed hammer blows on profile regions (segments, chalk marks, saturation, unevenness and correction, flip for back
   faces). Too cold: no deformation, "reheat". Reheat on the forge's firebox rest keeps all progress.
7. Quench: lower into the trough (trough owns the piece), hiss for the profile duration, lift. Water is consumed at
   the immersion boundary (10 ticks), never per frame.
8. Fit: cooled piece on the anvil, attach each reserved component at its socket; the real item appears in the anvil's
   output slot; collect. Items without components complete on lifting from the trough.
9. Optional: grindstone pass (block entity holds the piece, paid at accepted completion), engraving at the anvil.

## 5. Persistence

- `WorkpieceData` gains `ProcessState process` (stage, heat + heat tick, region work/quality/blows/marks, flipped,
  scale, heat preparation, quench ticks/dips/control, quench position, unassembled components, attached count,
  frozen profile snapshot, legacy flags, structural revision, workpiece id).
- `WorkpieceCodec` v3 with explicit version dispatch: v1/v2 migrate once (FORGED keeps forge score fixed and needs no
  component payment; SHAPED completes with both scores and Faulty unchanged). Unknown/undecodable payloads stay as raw
  NBT in their holder and block reuse.
- Forge: `ForgeJob` (reservation + escrow + billet), reheat slot, plan; legacy escrow without a job keeps the once-only
  refund at load.
- Anvil: workpiece + finished output + raw payload. Trough: quench slot. Grindstone: new block entity (same block id).

## 6. Dependencies and order

1. Domain rules → 2. data/profiles → 3. persistence/migration → 4. server services, block entities, packets, config →
5. client focus/camera/input/HUD/book → 6. renderers/effects → 7. remove old minigame + packets after tests pass →
8. GameTests (rewrite `SmithingRun`, new workflow/migration/transaction tests) → 9. guide, lang, JEI/Jade, docs →
10. runtime checks (GameTests, compat GameTests, smoke test with screenshots, multiplayer harness, Ultima gate if the
instance is present).

## 7. Risks

- Camera mixin conflicts with camera mods: World view setting is the fallback; mixin is `require = 0`.
- Synchronous GameTests vs tick-based rules: `WorkClock` has a test-only offset; block-entity ticks are driven directly.
- Shared lang keys for removed minigame text in four translations: obsolete keys are removed so no stale text remains.
- First-person body mods (Ultima): the local body is hidden only while the detached work camera is active.

## 8. Verification

`compileJava` during iteration; `runGameTestServer` (+ `-PcompatMods`); `runClient -PsmokeTest` with screenshots;
`tools/generate_textures.py --check`, `tools/model_preview.py --check`, `check_mod.py`; release gate when available.
Anything that cannot run here is reported as not run.

## 9. Status (end of 2026-11-13)

Implemented as planned, with these changes found while verifying in the real client:

- The pack-test agent plays a whole piece through real client inputs (`SmithDriver`): the focused views' own keys, the
  pattern book's search and Enter, hotbar selection and block use. The smoke test, store screenshots and showcase
  recording use it instead of the deleted minigame screens.
- `WorkFocusScreen` handles the work keys before the movement keys (Space is both the default action key and the jump
  key, so it used to leave the view), ignores key repeats, and in toggle mode a press ends a hold; the focused anvil view
  gained targets to lift the piece off and set a carried piece down, so the whole flow works from the keyboard.
- Presentation: the status panel's wrapped lines no longer overflow it; the World-view panel sits above the crosshair
  (station messages stack up from the hotbar); the hammer waits beside the piece between blows and is smaller; the heat
  palette puts the ideal band at orange to yellow-orange, and the billet texture is a pale tint base so hot metal glows;
  the quench's end cue has its own effect; in a work view, station messages are centred beside the status panel.
- Forge: a solid-fuel fire banks for a minute between heats instead of going out (found by the survival showcase take),
  and the reheat message asks whether the forge can heat the piece rather than whether it heated something this tick.
- The coverage GameTest also writes `logs/immersive_smithing_process_report.txt`.

Verification results, screenshots, performance and what was not run: `docs/cinematic-overhaul/REPORT.md`. Migration
notes: `docs/cinematic-overhaul/MIGRATION.md`.
