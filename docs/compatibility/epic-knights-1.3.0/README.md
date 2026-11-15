# Epic Knights 1.3.0 evidence

This directory records the current `update/1.3.0` implementation, including the corrected Epic Knights 10.15
knight-chestplate cost: the exact halfarmor chestplate and 45 additional steel units (five plates).

- [Support and native-process policies](../epic-integrations.md)
- [Verification report and complete command ledger](../validation/epic-knights-1.3.0.md)
- [Server profile manifest](profile-manifest.json): actual loaded versions, original jar coordinates, filenames,
  SHA-256 hashes, loaders, dependency declarations and runtime classification counts for every tested profile.
- [Production client manifests](client-manifests.json): exact jar hashes for the minimal clients and isolated Ultima copy.
- [Coverage generation commands](generation-commands.json): original-jar inputs, runtime dump directories, output paths and logs.
- [Expected runtime catalogs](../inventory/): `epic-1-3-0-*` fixtures retain complete forge inputs, native routes,
  output state, costs, recovery and dispositions. Historical fixtures remain separate.

| Export | Registered items | Coverage rows | Unresolved rows |
| --- | ---: | ---: | ---: |
| [Base](base/coverage.csv) | 460 | 470 | 0 |
| [Combined equipment/ranged addons](combined/coverage.csv) | 1835 | 1977 | 0 |
| [Materials with Ice and Fire provider](materials-provider/coverage.csv) | 748 | 776 | 0 |
| [Boomsticks release](boomsticks-release/coverage.csv) | 501 | 512 | 0 |
| [Overgeared coexistence](overgeared/coverage.csv) | 599 | 1018 | 0 |
| [TerraFirma coexistence](terrafirma/coverage.csv) | 1515 | 2498 | 0 |
| [TerraFirmaGreg coexistence](terrafirmagreg/coverage.csv) | 1530 | 2514 | 0 |

Each subdirectory also contains `inventory.json`: original jar resources and metadata, runtime input hashes,
registered item counts, route counts and version checks. No upstream jar, class, texture or model is redistributed.
Coverage rows identify actual base/upgrade operations, complete bound ingredient alternatives, result NBT/count,
native preservation, recycling and quality behavior. Input-dependent native serializers have explicit
`* (dynamic output)` rows; these count as routes, never as registered items.

Counts overlap between installations and must not be added as distinct equipment. A reviewed native, unavailable,
loot-only or externally owned disposition is accounted-for behavior, not a promise that an item is forgeable.
Inventory proves completeness of the declared classification; the linked runtime tests establish the tested behavior.
Some optional tests return early when their mod is absent, and external workflows use their own three-test suite.

The handoff records source reference commits. Those are research references rather than a claim that every released
jar was compiled from that head. Pinned runtime jars remain authoritative, including the older Ice and Fire and
Swappable Helms metadata versions and the released Seven Kingdoms/Medieval Boomsticks builds.

The complete Ultima jar manifest retains strict-TOML metadata warnings for two unrelated pack jars
(`Brute Force Culling Revived` encoding and `realistic_wheat` syntax); their exact bytes are hashed and the actual
pack gate passed. These warnings do not affect the fully matched provenance of the Epic Knights server profiles.
