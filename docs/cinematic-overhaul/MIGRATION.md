# Upgrading to 1.3.0: hands-on smithing

1.3.0 replaces the forge and anvil minigames with hands-on work at the stations. Worlds, configs and datapacks from 1.2
load without changes; this page lists what moves, what is kept for compatibility and what behaves differently.
Back up a world before any update.

## Players

- **Install on both sides.** The network protocol is now `6`; a 1.2 client cannot join a 1.3 server and the other
  way round.
- **Pieces in progress carry over.** A hot workpiece in tongs, on an anvil or dropped as an item is read and upgraded
  the first time it is touched:
  - a piece the old forge had finished (`FORGED`) keeps its forge score and is shaped again on the anvil, with the new
    operations of its kind;
  - a piece the old anvil had finished (`SHAPED`) keeps both scores and only needs its quench;
  - neither is charged for its components again.
- **Old forge reservations.** Components a 1.2 forge was holding for an unfinished minigame are dropped at that forge,
  once, when its chunk next loads. They were never part of a committed billet.
- **Unreadable pieces are kept.** A piece that cannot be read (its item belongs to a removed mod, or it was written by a
  newer version) stays exactly as it was in its tongs, anvil, forge or trough, is reported in the log, and can be read
  again once the mod is back. Nothing is deleted.
- **Finished items** (quality, maker's mark, signature, recycling value) are unchanged.
- **Engraving** no longer opens by itself after the quench: sneak-use the Smith's Anvil with the finished piece. Servers
  can bring the old behaviour back with `signing.openAfterQuench` (it then opens when a piece is collected).
- **The forge fire banks between heats.** With solid fuel, once nothing is heating and the burning fuel has burnt down,
  the fire stays lit for a minute without using fuel, so a piece put back on the rest relights it. Before, it went out.
- **The grindstone** is a pass now: rest the piece at the wheel, choose the aspect, and hold its edge to the wheel. The
  step, price and cap are as before.
- **Controls** (all rebindable): use a station to work it in the framed view (or press `G` while looking at one); the
  arrow keys choose a part, `Space` acts, `R` turns the piece over, hold `Left Alt` for a heavy blow; Escape or any
  movement key leaves. The camera mode (Cinematic, Shoulder or World view) is in the client config, or bind
  "Cycle work view" in the controls.

## Server operators

Forge keeps the values already in `immersive_smithing-server.toml` and adds the new keys with their defaults.

| Key | In 1.3.0 |
| --- | --- |
| `tools.<tier>ForgeTimeSeconds`, `tools.<tier>AnvilTimeSeconds` | Unused; kept so existing files stay valid. There is no timer. |
| `tools.<tier>BlowRecoveryTicks` | New. Ticks between accepted hammer blows: stone 12, iron 11, diamond 10, netherite 9 (a heavy blow takes half again as long). |
| `minigames.readyDelayMs`, `minigames.timingToleranceMs`, `minigames.maxLatencyCompensationMs` | Unused; kept. The section name stays `minigames` so files stay valid. |
| `minigames.maxStationDistance` | Still used: how far from the real eyes a smith may act on a station (default 6). |
| `minigames.maxAssistTimeMultiplier` | Now caps how much `ISmithingAssistSource` assistance can shorten blow recovery. |
| `work.heatPreparationWeight` | New. Share of the forge score from heat preparation; the rest is quench control (0.70). |
| `work.coolingMultiplier`, `work.heatingMultiplier` | New. Scale how fast pieces cool in air and heat in the forge (1.0). Frozen into each job when it is committed. |
| `work.leaseIdleSeconds` | New. A smith's hold on a station lapses after this long without an action (30). The work stays. |
| `work.jobPrioritySeconds` | New. How long a committed billet stays its smith's after they last worked it (300); then anyone may work, draw or cancel it. |
| `work.overheatBurns` | New, off by default. When on, metal held above 0.95 heat for three seconds burns and the piece is Faulty. When off, overheating only builds scale, a bounded and recoverable quality loss. |
| `trough.quenchTimeMultiplier` | New. Scales every profile's quench duration (1.0). |
| `grindstone.passTicks` | New. How long an edge must be held to the wheel for one refinement (40). |
| `signing.openAfterQuench` | Default changed to `false`. An existing file that says `true` keeps `true`. |

Faulty pieces now come only from destructive overheating (when enabled) and from pieces that were already Faulty; there
is no deadline any more. `/immersivesmithing session` lists open pattern books and station holds;
`/immersivesmithing process` writes the recipe-to-process coverage report.

## Client options

New in `immersive_smithing-client.toml`: `camera.mode`, `camera.transitionSpeed`, `camera.side`, `camera.fov`, and under
`accessibility`: `reducedMotion`, `showWorkGuides`, `expandedStatus`, `holdToAct`, `effectSoundVolume`; under
`patternBook`: `favorites`, `lastPattern`. Keys kept from the minigames now mean:

| Key | Now |
| --- | --- |
| `highContrastMinigames` | High-contrast work guides: outlined marks and solid heat bars. |
| `largeMinigameTargets` | Thicker guide marks and a larger cursor; what counts as a part is unchanged. |
| `colorblindSafeTargets` | Blue/orange palette for heat bands and guides (heat is always also named in words). |
| `timingCueSounds`, `soundCueVolume` | Readiness cues: a billet ready to draw, a piece too cold to work, a quench done. |
| `feedbackLabelMs` | How long a work result ("Too cold", "Overworked") stays readable beside the piece. |

## Datapack and resource pack authors

- **Process profiles** (`data/<ns>/immersive_smithing/process_profiles/`) are new: the operations, quench and component
  sockets of each kind of piece. The 13 built-in profiles serve all 44 built-in anvil patterns. A recipe without its own
  `process_profile` uses the profile that lists its anvil pattern, so existing recipes need no change.
- **Recipes** may name a `process_profile`. `forge_pattern` is still accepted and ignored.
- **Material families** may define `thermal` heat bands; without them a process profile's `heat` block applies, then the
  defaults (no built-in material or profile sets either, so every metal uses the defaults).
- **Anvil patterns** still choose the process profile and classify items through `categories`; their strike fields
  (`targets`, `strikes`, timings) are read and ignored. **Forge patterns** are read and ignored.
- **Workpiece profiles** (`assets/<ns>/immersive_smithing/workpiece_profiles/`, resource packs) describe how pieces deform.
  A profile without art falls back to a plain bar.
- The new sound events (`block.smiths_forge.bellows`, `block.smiths_anvil.strike_heavy`, `block.smiths_trough.quench_loop`,
  ...) point at vanilla sounds and can be replaced by resource packs.

Formats: [docs/API.md](../API.md).

## Mod developers

- `ItemSmithedEvent` fires once per finished piece when it is **completed**: at the trough for pieces without
  components (and pieces from before 1.3.0), at the anvil when the last component is fitted for the others. It used to
  fire at the quench for every piece. `getTroughPos()` is unchanged; `getCompletedAt()` and `getCompletionPos()` are new.
- `ISmithingAssistSource`: the `ANVIL` answer now divides the hammer's recovery between blows (bounded by
  `maxAssistTimeMultiplier`, never below four ticks); `FORGE` is no longer asked. Nothing is sent to clients.
- `ISmithingCostModifier`, `ISmithingRecipeGate` and the other providers are unchanged; gates and cost modifiers are
  checked when a plan is pinned in the book and again when it is committed.
- `WorkpieceData` (not part of the API package) keeps its old constructors; old two-state pieces built with them are
  migrated as above. The workpiece NBT is schema 3 (see [Item data](../API.md#item-data)).
- The minigame screens and packets are gone (`ForgeStartPacket`, `ForgeActionPacket`, `OpenAnvilScreenPacket`,
  `AnvilStrikePacket`, `SessionResultPacket`, ...). Station work goes through `WorkActionPacket`, which only carries
  intents; nothing a client sends carries a score or an item.
