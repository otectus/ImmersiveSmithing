#!/usr/bin/env python3
"""Generates the anvil-pattern shape tags for Epic Fight and Simply Swords weapons, checked against the real jars.

Run from the project root (the jars default to the Gradle cache copies of the versions pinned in gradle.properties):
  python3 tools/generate_weapon_compat.py [--epicfight path.jar] [--simplyswords path.jar]

Output (deterministic, committed):
  src/main/resources/data/immersive_smithing/tags/items/<shape>s.json   one tag per anvil pattern, optional entries
  categories of src/main/resources/data/immersive_smithing/immersive_smithing/anvil_patterns/<shape>.json

Every weapon of both mods gets exactly one shape, so EquipmentClassifier never falls back to "sword" just because a
weapon extends SwordItem. Recipes are not generated here: Simply Swords' come from automatic detection at every reload
(so a recipe a pack removes is never brought back); Epic Fight's are bundled by tools/generate_epic_compat.py. The
coverage GameTests compare the result with docs/compatibility/inventory/<profile>/.

Mapping (the inventory in docs/compatibility/INVENTORY.md shows each weapon's pattern):
  Epic Fight     greatsword -> greatsword, longsword -> longsword, tachi/uchigatana/bokken -> katana, dagger -> dagger,
                 spear -> spear, glove -> cestus
  Simply Swords  longsword -> longsword, twinblade -> twinblade, rapier -> rapier, katana -> katana, sai -> parrying_dagger,
                 spear -> spear, glaive -> glaive, warglaive -> warglaive, cutlass -> saber, claymore -> greatsword,
                 greataxe -> battleaxe, greathammer -> battle_hammer, chakram -> chakram, scythe -> scythe, halberd -> halberd
                 uniques: the shape their name or their Better Combat parent states; every other unique keeps "sword"
"""
import argparse
import glob
import json
import os
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/immersive_smithing'
TAGS = DATA / 'tags/items'
PATTERNS = DATA / 'immersive_smithing/anvil_patterns'

PLURAL = {'greatsword': 'greatswords', 'longsword': 'longswords', 'katana': 'katanas', 'dagger': 'daggers',
          'parrying_dagger': 'parrying_daggers', 'spear': 'spears', 'cestus': 'cestusae', 'rapier': 'rapiers',
          'glaive': 'glaives', 'saber': 'sabers', 'battleaxe': 'battleaxes', 'battle_hammer': 'battle_hammers',
          'scythe': 'scythes', 'halberd': 'halberds', 'twinblade': 'twinblades', 'warglaive': 'warglaives',
          'chakram': 'chakrams'}
# Entries that predate this generator and are kept as they are.
EXTRA = {'spear': ['cataclysm:ancient_spear']}

EF_TIERS = ['wooden', 'stone', 'iron', 'golden', 'diamond', 'netherite']
EF_TYPES = {'greatsword': 'greatsword', 'longsword': 'longsword', 'tachi': 'katana', 'dagger': 'dagger', 'spear': 'spear'}
EF_SINGLES = {'uchigatana': 'katana', 'bokken': 'katana', 'glove': 'cestus'}

SS_TIERS = ['iron', 'gold', 'diamond', 'netherite', 'runic']
SS_TYPES = {'longsword': 'longsword', 'twinblade': 'twinblade', 'rapier': 'rapier', 'katana': 'katana',
            'sai': 'parrying_dagger', 'spear': 'spear', 'glaive': 'glaive', 'warglaive': 'warglaive', 'cutlass': 'saber',
            'claymore': 'greatsword', 'greataxe': 'battleaxe', 'greathammer': 'battle_hammer', 'chakram': 'chakram',
            'scythe': 'scythe', 'halberd': 'halberd'}
# Unique weapons with an evident shape: named after it, or a Better Combat parent that states it
# (claymore, scythe, spear/trident, sickle). Everything else stays on the generic sword pattern.
SS_UNIQUES = {'brimstone_claymore': 'greatsword', 'watcher_claymore': 'greatsword', 'the_devourer': 'greatsword',
              'twisted_blade': 'greatsword', 'toxic_longsword': 'longsword', 'watching_warglaive': 'warglaive',
              'magiscythe': 'scythe', 'decaying_relic': 'scythe', 'magispear': 'spear', 'gloampiercer': 'spear',
              'wickpiercer': 'spear', 'sword_on_a_stick': 'spear', 'mjolnir': 'battle_hammer'}


def gradle_property(name):
    for line in (ROOT / 'gradle.properties').read_text().splitlines():
        if line.startswith(name + '='):
            return line.split('=', 1)[1].strip()
    raise SystemExit(f'{name} missing from gradle.properties')


def cached_jar(slug, version):
    hits = glob.glob(os.path.expanduser(f'~/.gradle/caches/modules-2/files-2.1/maven.modrinth/{slug}/{version}/*/*.jar'))
    if not hits:
        raise SystemExit(f'{slug} {version} not in the Gradle cache; run a -P profile once or pass the jar')
    return hits[0]


def registered_items(jar, namespace):
    """Item ids named by the mod's own language file: the items it registers."""
    with zipfile.ZipFile(jar) as z:
        lang = json.loads(z.read(f'assets/{namespace}/lang/en_us.json'))
    return {key.split('.', 2)[2] for key in lang if key.startswith(f'item.{namespace}.') and key.count('.') == 2}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--epicfight')
    parser.add_argument('--simplyswords')
    args = parser.parse_args()
    ef_jar = args.epicfight or cached_jar('epic-fight', gradle_property('epic_fight_modrinth_id'))
    ss_jar = args.simplyswords or cached_jar('simply-swords', gradle_property('simply_swords_modrinth_id'))

    shapes = {shape: list(EXTRA.get(shape, [])) for shape in PLURAL}
    ef_items = registered_items(ef_jar, 'epicfight')
    ss_items = registered_items(ss_jar, 'simplyswords')
    missing = []

    def add(namespace, name, shape, known):
        if name not in known:
            missing.append(f'{namespace}:{name}')
            return
        shapes[shape].append(f'{namespace}:{name}')

    for tier in EF_TIERS:
        for kind, shape in EF_TYPES.items():
            add('epicfight', f'{tier}_{kind}', shape, ef_items)
    for name, shape in EF_SINGLES.items():
        add('epicfight', name, shape, ef_items)
    for tier in SS_TIERS:
        for kind, shape in SS_TYPES.items():
            add('simplyswords', f'{tier}_{kind}', shape, ss_items)
    for name, shape in SS_UNIQUES.items():
        add('simplyswords', name, shape, ss_items)
    if missing:
        raise SystemExit('Not registered by the pinned jars: ' + ', '.join(missing))

    for shape, plural in PLURAL.items():
        values = [{'id': item, 'required': False} for item in sorted(set(shapes[shape]))]
        (TAGS / f'{plural}.json').write_text(json.dumps({'replace': False, 'values': values}, indent=2) + '\n')
        pattern_file = PATTERNS / f'{shape}.json'
        pattern = json.loads(pattern_file.read_text())
        categories = pattern.setdefault('categories', [])
        tag = f'immersive_smithing:{plural}'
        if tag not in categories:
            categories.append(tag)
            pattern_file.write_text(json.dumps(pattern, indent=2) + '\n')
    total = sum(len(v) for k, v in shapes.items()) - sum(len(v) for v in EXTRA.values())
    print(f'{total} weapons in {len(PLURAL)} shape tags (Epic Fight {os.path.basename(ef_jar)}, Simply Swords {os.path.basename(ss_jar)})')


if __name__ == '__main__':
    main()
