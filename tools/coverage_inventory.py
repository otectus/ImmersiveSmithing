#!/usr/bin/env python3
"""Turns the live coverage catalogs into the checked-in inventories, the fixtures the GameTests compare against.

  ./gradlew runGameTestServer -PepicFight -PsimplySwords -PepicKnights
      # CoverageGameTests writes run-gametest/logs/immersive_smithing_catalog_<mod>.json for every loaded mod below
  python3 tools/coverage_inventory.py --profile full
      # writes docs/compatibility/inventory/full/<mod>.json, then rebuilds docs/compatibility/INVENTORY.md
  python3 tools/coverage_inventory.py
      # only rebuilds INVENTORY.md from the profiles already checked in

The catalogs come from the running game's registry and its rebuilt recipe data, never from
tools/generate_epic_compat.py, so the fixture is an independent record of what each pinned version registers and how
each item is obtained. A profile is one tested set of mods (each catalog records it as loaded_mods); a GameTest run with
exactly that set fails when a mod no longer matches its fixture (an item appears, disappears or changes route), and every
run fails when any item is missing_support. "acquisition" is a human note added here and not compared.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOGS = ROOT / 'run-gametest/logs'
OUT = ROOT / 'docs/compatibility/inventory'
TABLE = ROOT / 'docs/compatibility/INVENTORY.md'
MODS = {
    'epicfight': 'Epic Fight', 'simplyswords': 'Simply Swords',
    'magistuarmory': 'Epic Knights', 'magistuarmoryaddon': 'Epic Knights: Addon', 'slavicarmory': 'Epic Knights: Slavic Armory',
    'antiquelegacy': 'Epic Knights: Antique Legacy', 'epic_knights_ice_and_fire': 'Epic Knights: Ice and Fire',
    'epic_knights_ores_and_alloys': 'Epic Knights: Ores and Alloys', 'darkagesarmory': 'Epic Knights: Dark Ages',
    'epicknights_open_helms': 'Epic Knights: Open Helms', 'artillery_addon': 'Epic Knights: Artilleries and Firearms',
    'medieval_boomsticks': 'Medieval Boomsticks', 'ekotsk': 'Epic Knights of the Seven Kingdoms',
}


def name(item_id):
    return item_id.split(':', 1)[1]


def simply_swords_note(item):
    """Notes kept from the 1.1 audit: Simply Swords is still handled by automatic detection."""
    n = name(item['id'])
    forged = bool(item['forge'])
    components = ' '.join(c for f in item['forge'] for c in f['components'])
    if n == 'sword_on_a_stick':
        return ('A longsword set on a stick. Simply Swords\' recipe stays (the longsword\'s quality and mark carry over); '
                'the forge also makes it directly from the longsword\'s metal, the sticks and the string.')
    if n.startswith('runic_'):
        return ('Runic upgrade of the netherite weapon (runic tablet and a diamond) and its reroll both stay on the '
                'smithing table. Quality and mark carry through both; the reroll still clears the rolled power. Melts '
                'for the netherite base\'s metal only.')
    if n.startswith('diamond_'):
        return 'Diamond weapon: crafted exactly as Simply Swords ships it (any iron nuggets are fittings, not stock).'
    if n.startswith('netherite_'):
        extra = ' Iron-nugget fittings stay components.' if 'iron_nugget' in components else ''
        return ('Netherite upgrade: forged from netherite in the diamond piece\'s shape (no template, no diamond '
                'piece). Simply Swords\' smithing-table upgrade is replaced.' + extra)
    if n.startswith('gold_') and 'iron_nugget' in components:
        return 'Gold weapon with iron-nugget fittings: forged from gold, the nuggets taken as components.'
    if n.startswith(('iron_', 'gold_')) and forged:
        return 'Ordinary metal weapon: forged; the crafting recipe is replaced.'
    if not item['native'] and not forged and item['quality']:
        return ('Unique weapon: found as loot or made by Simply Swords\' own mechanics. Graded when found, refined at the '
                'grindstone; never forged and never melted down.')
    return item['reason']


def acquisition(mod, item):
    if mod == 'simplyswords':
        return simply_swords_note(item)
    reason = item['reason']
    if item['disposition'] == 'forge_default' and item['forge']:
        replaced = [n['recipe'] for n in item['native'] if n['state'].startswith('disabled')]
        kept = [n['recipe'] for n in item['native'] if n['state'].startswith('kept')]
        note = 'Forged; replaces ' + ', '.join(f'`{r}`' for r in replaced) if replaced else 'Forged'
        if kept:
            note += '; keeps ' + ', '.join(f'`{r}`' for r in kept)
        return note + '.'
    return reason[0].upper() + reason[1:] + '.' if reason else ''


def cost(item):
    if not item['forge']:
        return '-'
    parts = []
    for f in item['forge']:
        metal = f"{f['units']} {name(f['material'])}"
        parts.append(metal + (' + ' + ', '.join(f['components']) if f['components'] else ''))
    return '; '.join(parts)


def section_from(mod, title, catalog):
    items = catalog['items']
    counts = catalog.get('dispositions', {})
    lines = [f'## {title} {catalog["version"]}', '',
             f'{len(items)} pieces of equipment: ' + ', '.join(f'{v} {k.replace("_", " ")}' for k, v in sorted(counts.items())) + '.',
             '', '| Item | Route | Forge cost (units + components) | Pattern | Quality | Recycles for | Acquisition |',
             '| --- | --- | --- | --- | --- | --- | --- |']
    for item in items:
        pattern = ', '.join(sorted({name(f['pattern']) for f in item['forge']})) or '-'
        lines.append(f"| `{item['id']}` | {item.get('disposition', '?').replace('_', ' ')} | {cost(item)} | {pattern} | "
                     f"{'yes' if item['quality'] else 'no'} | {item['recycling']} | {item['acquisition']} |")
    return '\n'.join(lines)


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--profile', help='write the last GameTest run as this profile (docs/compatibility/inventory/<profile>/)')
    args = parser.parse_args()
    if args.profile:
        if not re.fullmatch(r'[a-z0-9_-]+', args.profile):
            sys.exit('profile names are lower-case words')
        target = OUT / args.profile
        catalogs = [c for c in (LOGS / f'immersive_smithing_catalog_{mod}.json' for mod in MODS) if c.exists()]
        if not catalogs:
            sys.exit('no catalog: run the GameTests first')
        # Only the last GameTest run counts: its catalogs share the newest one's mod set; older ones belong to other runs.
        newest = json.loads(max(catalogs, key=lambda c: c.stat().st_mtime).read_text()).get('loaded_mods')
        if target.exists():
            for old in target.glob('*.json'):
                old.unlink()
        written = 0
        for mod in MODS:
            source = LOGS / f'immersive_smithing_catalog_{mod}.json'
            if not source.exists():
                continue
            catalog = json.loads(source.read_text())
            if catalog.get('loaded_mods') != newest:
                continue
            for item in catalog['items']:
                item['acquisition'] = acquisition(mod, item)
            target.mkdir(parents=True, exist_ok=True)
            (target / f'{mod}.json').write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + '\n')
            counts = catalog.get('dispositions', {})
            missing = counts.get('missing_support', 0)
            print(f'{args.profile}/{mod}: {len(catalog["items"])} items, {counts}' + (f'  ({missing} MISSING)' if missing else ''))
            written += 1
        if not written:
            sys.exit('no catalog from the last GameTest run')
    sections = []
    for profile in sorted(p for p in OUT.iterdir() if p.is_dir()):
        loaded = None
        for mod, title in MODS.items():
            path = profile / f'{mod}.json'
            if not path.exists():
                continue
            catalog = json.loads(path.read_text())
            loaded = loaded or catalog.get('loaded_mods')
            sections.append(section_from(mod, f'{title} ({profile.name} profile)', catalog))
        if loaded:
            sections.insert(len(sections) - sum(1 for m in MODS if (profile / f'{m}.json').exists()),
                            f'# Profile `{profile.name}`\n\nLoaded mods: ' + ', '.join(f'`{m}`' for m in loaded) + '.')
    header = ['# Equipment inventory', '',
              'Generated by `tools/coverage_inventory.py` from the live catalogs that `CoverageGameTests` writes. Each profile is',
              'one tested set of mods (`docs/compatibility/inventory/<profile>/`); a GameTest run with exactly that set fails when',
              'the game no longer matches. Routes and costs are explained in [epic-integrations.md](epic-integrations.md).', '',
              'Costs are in units (nugget 1, ingot 9, block 81). "Quality" says whether the piece can carry smithing quality.', '']
    TABLE.write_text('\n'.join(header) + '\n' + '\n\n'.join(sections) + '\n')
    print(f'{TABLE.relative_to(ROOT)}: {len(sections)} sections')


if __name__ == '__main__':
    main()
