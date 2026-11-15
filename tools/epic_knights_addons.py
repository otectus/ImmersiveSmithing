"""Generate reviewed release templates and native policies; live source recipes supply all actual costs.

Called by generate_epic_compat.py. The manifest records the entire independently loaded registry for each release.
The SHA check prevents refreshing data against a different jar under an unchanged release label.
"""
import json
from pathlib import Path
import generate_epic_compat as g


def generate_addons():
    manifest = json.loads((g.ROOT / 'tools/epic_knights_addons.json').read_text())
    files, inputs = {}, {}
    candidates, quality = set(), set()
    for profile in manifest['profiles']:
        mod, version = profile['mod'], profile['version']
        jar = g.cached_jar(('curse', profile['property']))
        actual = g.sha256(jar)
        if actual != profile['sha256']:
            raise g.GenerationError(f'{mod} {version}: jar differs from reviewed registry; refresh the isolated inventory first')
        inputs[f'{mod}@{version}'] = {'file': jar.name, 'sha256': actual, 'property': profile['property']}
        conditions = [{'type': 'forge:mod_loaded', 'modid': mod},
                      {'type': 'immersive_smithing:mod_version', 'modid': mod, 'versions': f'[{version}]'}]
        policies = []
        for entry in profile['items']:
            item = entry['item']
            if entry['disposition'] == 'forge_default':
                pattern = {'crossbow': 'heavy_crossbow', 'mace': 'flanged_mace'}.get(entry['pattern'], entry['pattern'])
                pattern_file = g.DATA / 'immersive_smithing/anvil_patterns' / f'{pattern}.json'
                if not pattern_file.exists():
                    raise g.GenerationError(f'{item}: missing reviewed pattern {pattern}')
                recipe = {'conditions': conditions + [{'type': 'forge:item_exists', 'item': item}],
                          'type': 'immersive_smithing:smithing', 'source_policy': 'epic_knights',
                          'material': entry['material'], 'result': {'item': item},
                          'forge_pattern': 'immersive_smithing:standard', 'anvil_pattern': 'immersive_smithing:' + pattern}
                suffix = Path('release') if mod == 'medieval_boomsticks' else Path()
                files[g.OUT_RECIPES / mod / suffix / (item.split(':')[1] + '.json')] = g.dump(recipe)
                candidates.add(item)
            else:
                policies.append({'items': [item], 'disposition': entry['disposition'], 'reason': entry['reason']})
            if entry['disposition'] == 'native_transformation' and mod == 'thirtyyearswar':
                quality.add(item)
        files[g.OUT_COVERAGE / (mod + '_reviewed_release.json')] = g.dump({
            'conditions': conditions, 'mod': mod, 'tested_version': version, 'policies': policies})
    for name, values in [('smithable_equipment', candidates), ('quality_eligible', quality)]:
        files[g.OUT_TAGS / 'epic_knights_releases' / (name + '.json')] = g.dump({
            'replace': False, 'values': [{'id': item, 'required': False} for item in sorted(values)]})
    return files, inputs
