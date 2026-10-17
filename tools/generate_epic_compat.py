#!/usr/bin/env python3
"""Generates Immersive Smithing's built-in Epic Fight and Epic Knights data from the pinned jars.

  python3 tools/generate_epic_compat.py            # --check (default): compare with the committed files, write nothing
  python3 tools/generate_epic_compat.py --write    # rewrite the generated files
  python3 tools/generate_epic_compat.py --refresh-registry run-gametest/logs
        # after ./gradlew runGameTestServer -PepicKnights=all,iceandfire -PepicFight -PregistryDump=all

Inputs, all local (no network): the jars pinned in gradle.properties, read from the Gradle cache (run any -PepicKnights
or -PepicFight Gradle task once to download them); Minecraft's and Forge's own data jars; the reviewed policy
tools/epic_compat_manifest.py; and tools/epic_compat_registry.json, a snapshot of what the pinned jars register in a
running game (class, slot, repair material), refreshed from RegistryDumpGameTests' output. tools/epic_compat.lock.json
records the hash of every input; --check fails when an input changed.

Outputs (deterministic, sorted, committed):
  src/main/resources/data/immersive_smithing/recipes/compat/<mod>/<item>.json        forge recipes (and the bokken)
  src/main/resources/data/immersive_smithing/immersive_smithing/coverage/<mod>.json  decisions for items not forged
  src/main/resources/data/immersive_smithing/immersive_smithing/materials/compat/... plate sources, new families
  src/main/resources/data/immersive_smithing/tags/items/compat/<integration>/...     quality, recycling, candidates

Rules (docs/compatibility/epic-integrations.md explains them for players):
- A recipe's metal is counted exactly once, as fractions: an ingot 9 units, a nugget 1, a plate 9 (two ingots make two),
  a ring 1/2 (four nuggets make eight), a chain link 3 rings, chainmail 9 rings, a small plate 3/4, lamellar rows six
  small plates and three leather strips. Intermediates and finished pieces of the same metal are flattened (an armet is
  a barbute and three plates: 72 steel); the total is rounded down, never up.
- The item's repair material decides its metal. Fittings of another metal (a steel boss on an iron shield, gold nuggets
  on a steel hilt) and every non-metal part (hilts, poles, leather, fabric, planks) stay components, never refunded.
- Netherite upgrades are forged from netherite in the diamond piece's whole shape, like vanilla netherite gear.
- Gilding, darkening, fluting and other template transformations, visor states and unique loot keep their own route.
- Every forge recipe lists the native recipes it replaces (disabled while it is active) or follows; it is active only
  while one of them is loaded, so a pack that removes the native recipe removes the forge one too.
- An item the rules cannot decide is an error: add a decision to the manifest.
"""
import argparse
import hashlib
import importlib.util
import json
import math
import os
import pathlib
import re
import sys
import zipfile
from collections import defaultdict
from fractions import Fraction

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/immersive_smithing'
OUT_RECIPES = DATA / 'recipes/compat'
OUT_COVERAGE = DATA / 'immersive_smithing/coverage'
OUT_MATERIALS = DATA / 'immersive_smithing/materials/compat'
OUT_TAGS = DATA / 'tags/items/compat'
REGISTRY = ROOT / 'tools/epic_compat_registry.json'
LOCK = ROOT / 'tools/epic_compat.lock.json'
GRADLE = pathlib.Path(os.path.expanduser('~/.gradle/caches'))
VANILLA_JAR = GRADLE / 'forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar'
FORGE_JAR = GRADLE / 'forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-universal.jar'


class GenerationError(Exception):
    pass


def load_manifest():
    spec = importlib.util.spec_from_file_location('epic_compat_manifest', ROOT / 'tools/epic_compat_manifest.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def gradle_property(name):
    for line in (ROOT / 'gradle.properties').read_text().splitlines():
        if line.startswith(name + '='):
            return line.split('=', 1)[1].strip()
    raise GenerationError(f'{name} missing from gradle.properties')


def cached_jar(source):
    if source[0] == 'modrinth':
        _, slug, prop = source
        pattern = GRADLE / 'modules-2/files-2.1/maven.modrinth' / slug / gradle_property(prop)
    else:
        _, prop = source
        slug, file_id = gradle_property(prop).split(':')
        pattern = GRADLE / 'modules-2/files-2.1/curse.maven' / slug / file_id
    hits = sorted(p for p in pattern.glob('*/*.jar') if not p.name.endswith('-sources.jar'))
    if not hits:
        raise GenerationError(f'{source} is not in the Gradle cache ({pattern}); run a -PepicKnights/-PepicFight task once')
    return hits[0]


def sha256(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()


# ------------------------------------------------------------------------------------------------ reading jars

class Jar:
    """Recipes, item tags and the mod version of one jar (Forge data only)."""

    def __init__(self, path):
        self.path = pathlib.Path(path)
        self.recipes = {}
        self.tags = defaultdict(list)
        self.strings = []
        with zipfile.ZipFile(self.path) as z:
            for name in z.namelist():
                m = re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json', name)
                if m:
                    self.recipes[f'{m.group(1)}:{m.group(2)}'] = load_json(z.read(name), name)
                    continue
                m = re.fullmatch(r'data/([^/]+)/tags/items/(.+)\.json', name)
                if m:
                    tag = load_json(z.read(name), name)
                    if isinstance(tag, dict):
                        self.tags[f'{m.group(1)}:{m.group(2)}'].append(tag)
            self.toml = z.read('META-INF/mods.toml').decode('utf-8', 'replace') if 'META-INF/mods.toml' in z.namelist() else ''

    def class_strings(self, class_name):
        """CONSTANT_Utf8 strings of one class (for defaults that live in code, like Swappable Helms' visor pairs)."""
        with zipfile.ZipFile(self.path) as z:
            data = z.read(class_name)
        out, pos, index = [], 10, 1
        count = int.from_bytes(data[8:10], 'big')
        sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        while index < count:
            tag = data[pos]
            if tag == 1:
                length = int.from_bytes(data[pos + 1:pos + 3], 'big')
                out.append(data[pos + 3:pos + 3 + length].decode('utf-8', 'replace'))
                pos += 3 + length
            else:
                pos += 1 + sizes[tag]
                if tag in (5, 6):
                    index += 1
            index += 1
        return out


def load_json(raw, name):
    text = raw.decode('utf-8-sig')
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise GenerationError(f'{name}: {e}')


# ------------------------------------------------------------------------------------------------ the world model

class World:
    def __init__(self, manifest, registry, jars):
        self.m = manifest
        self.registry = registry          # item id -> info
        self.mods = set(registry['mods']) | {'minecraft', 'forge'}
        self.items = registry['items']
        self.tag_entries = defaultdict(list)
        self.raw_recipes = {}
        for jar in jars:
            for tag, entries in jar.tags.items():
                self.tag_entries[tag].extend(entries)
            for rid, recipe in jar.recipes.items():
                self.raw_recipes.setdefault(rid, recipe)
        self._tags = {}
        self.recipes = {}
        for rid, raw in sorted(self.raw_recipes.items()):
            r = Recipe.parse(rid, raw, self)
            if r is not None:
                self.recipes[rid] = r
        self.by_result = defaultdict(list)
        for r in self.recipes.values():
            if r.result:
                self.by_result[r.result].append(r)
        self.metal_of = self._metal_sources()

    # tags ------------------------------------------------------------------------------------------
    def tag(self, name, seen=None):
        if name in self._tags:
            return self._tags[name]
        seen = (seen or set()) | {name}
        out = set()
        replaced = False
        for entry in self.tag_entries.get(name, []):
            if entry.get('replace'):
                out = set()
                replaced = True
            for value in entry.get('values', []):
                ref = value['id'] if isinstance(value, dict) else value
                if ref.startswith('#'):
                    if ref[1:] not in seen:
                        out |= self.tag(ref[1:], seen)
                elif ref in self.items or ref.startswith('minecraft:'):
                    out.add(ref)
        del replaced
        self._tags[name] = out
        return out

    def ingredient_items(self, ing):
        if isinstance(ing, list):
            out = set()
            for alt in ing:
                out |= self.ingredient_items(alt)
            return out
        if 'item' in ing:
            return {ing['item']}
        if 'tag' in ing:
            return set(self.tag(ing['tag']))
        return set()

    def _metal_sources(self):
        out = {}
        for name, family in self.m.METALS.items():
            for form, units in self.m.FORMS.items():
                for item in self.tag(f'forge:{form}/{name}'):
                    if item in out and out[item] != (family, units):
                        raise GenerationError(f'{item} is claimed by {out[item]} and {(family, units)}')
                    out[item] = (family, units)
        return out

    # conditions ------------------------------------------------------------------------------------
    def condition(self, c):
        kind = c.get('type', '')
        if kind == 'forge:mod_loaded':
            return c['modid'] in self.mods
        if kind == 'forge:item_exists':
            return self.exists(c['item'])
        if kind == 'forge:not':
            return not self.condition(c['value'])
        if kind == 'forge:and':
            return all(self.condition(v) for v in c['values'])
        if kind == 'forge:or':
            return any(self.condition(v) for v in c['values'])
        if kind == 'forge:tag_empty':
            return not self.tag(c['tag'])
        if kind == 'forge:true':
            return True
        if kind == 'forge:false':
            return False
        raise GenerationError(f'unknown condition {kind}')

    def exists(self, item):
        return item in self.items or item.startswith('minecraft:')

    # item facts -------------------------------------------------------------------------------------
    def info(self, item):
        return self.items.get(item, {})

    def is_equipment(self, item):
        i = self.info(item)
        return bool(i) and i.get('stack') == 1 and (i.get('damageable') or i.get('kind') not in (None, 'item'))


class Recipe:
    """A loaded recipe: shaped/shapeless ingredients (with counts), or a smithing transform."""

    def __init__(self, rid, kind, result, count, ingredients, base=None, addition=None, template=None):
        self.id, self.kind, self.result, self.count = rid, kind, result, count
        self.ingredients = ingredients  # [(ingredient json, count)]
        self.base, self.addition, self.template = base, addition, template

    @staticmethod
    def parse(rid, raw, world):
        if not isinstance(raw, dict):
            return None
        for c in raw.get('conditions', []):
            if not world.condition(c):
                return None
        kind = raw.get('type', '')
        result = raw.get('result')
        if isinstance(result, dict):
            result_id, count = result.get('item'), result.get('count', 1)
        elif isinstance(result, str):
            result_id, count = result, 1
        else:
            result_id, count = None, 1
        if kind in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
            if kind.endswith('shaped'):
                counts = defaultdict(int)
                for row in raw['pattern']:
                    for ch in row:
                        if ch != ' ':
                            counts[ch] += 1
                ingredients = [(raw['key'][k], n) for k, n in sorted(counts.items())]
            else:
                ingredients = [(ing, 1) for ing in raw['ingredients']]
            refs = [i for ing, _ in ingredients for i in referenced_items(ing)]
            if result_id is None or not world.exists(result_id) or any(not world.exists(i) for i in refs):
                return None  # the game fails to parse it too (an unregistered item)
            return Recipe(rid, 'crafting', result_id, count, ingredients)
        if kind == 'minecraft:smithing_transform':
            refs = [i for part in ('base', 'addition', 'template') for i in referenced_items(raw.get(part, {}))]
            if result_id is None or not world.exists(result_id) or any(not world.exists(i) for i in refs):
                return None
            return Recipe(rid, 'smithing', result_id, count, [], raw.get('base'), raw.get('addition'), raw.get('template'))
        return Recipe(rid, 'other:' + kind, result_id, count, [])


def referenced_items(ing):
    if isinstance(ing, list):
        return [i for alt in ing for i in referenced_items(alt)]
    return [ing['item']] if isinstance(ing, dict) and 'item' in ing else []


def ing_key(ing):
    return json.dumps(ing, sort_keys=True)


# ------------------------------------------------------------------------------------------------ valuation

class Valuer:
    """Prices recipes in fractional metal units, flattening same-metal intermediates and pieces."""

    def __init__(self, world):
        self.w = world
        self.m = world.m
        self._metal = {}
        self._busy = set()

    def manufacturing_recipe(self, item):
        """The crafting recipe that makes an item, preferring the item's own mod; None if only other routes exist."""
        recipes = [r for r in self.w.by_result.get(item, []) if r.kind == 'crafting' and r.id not in self.m.IGNORE_RECIPES]
        if not recipes:
            return None
        own = [r for r in recipes if r.id.split(':')[0] == item.split(':')[0]]
        return sorted(own or recipes, key=lambda r: r.id)[0]

    def classify(self, ing, gem_family=None):
        """One ingredient slot: ('metal', family, units), ('gem',), ('piece', item), ('part', item) or ('aux',). With a
        gem family (pricing a netherite shape) a diamond part such as a vanilla diamond sword counts as that metal."""
        items = self.w.ingredient_items(ing)
        if not items:
            return ('aux',)
        metal = {self.w.metal_of.get(i) for i in items}
        if None not in metal:
            if len(metal) != 1:
                raise GenerationError(f'ingredient {ing} mixes metals {sorted(metal)}')
            family, units = metal.pop()
            return ('metal', family, Fraction(units))
        if any(i in self.w.metal_of for i in items):
            raise GenerationError(f'ingredient {ing} mixes metal and non-metal items')
        if (isinstance(ing, dict) and ing.get('tag') in self.m.GEMS) or items <= {g for g in self.m.GEMS if not g.startswith('forge:')}:
            return ('gem',)
        if len(items) == 1:
            item = next(iter(items))
            if self.w.is_equipment(item):
                return ('piece', item)
            if self.manufacturing_recipe(item) is not None and self.metal_of(item, gem_family):
                return ('part', item)
        return ('aux',)

    def metal_of(self, item, gem_family=None):
        """Metal in one unit of an item made by crafting, by family (memoised; a cycle counts as no metal)."""
        key = (item, gem_family)
        if key in self._metal:
            return self._metal[key]
        recipe = self.manufacturing_recipe(item)
        total = defaultdict(Fraction)
        if recipe is None or key in self._busy:
            return total
        self._busy.add(key)
        try:
            for ing, n in recipe.ingredients:
                kind = self.classify(ing, gem_family)
                share = Fraction(n, recipe.count)
                if kind[0] == 'metal':
                    total[kind[1]] += kind[2] * share
                elif kind[0] == 'gem' and gem_family:
                    total[gem_family] += 9 * share
                elif kind[0] in ('piece', 'part'):
                    for family, units in self.metal_of(kind[1], gem_family).items():
                        total[family] += units * share
        finally:
            self._busy.discard(key)
        self._metal[key] = dict(total)
        return self._metal[key]

    def primary_of_piece(self, item):
        """The metal a finished piece is made of: its repair metal, else the metal most of it is."""
        repair = self.w.info(item).get('repair_families', [])
        metal = self.metal_of(item)
        if len(repair) == 1 and (repair[0] in metal or not metal):
            return repair[0]
        if self.w.info(item).get('repair_other') and not repair:
            return None
        return max(sorted(metal), key=metal.get) if metal else None

    def emit(self, recipe, primary, gem_family=None, scale=Fraction(1), out=None, depth=0):
        """Flattens a recipe into the forge's terms: units of the primary metal and components. A part or a finished
        piece of the primary metal is expanded into its own recipe; anything else (a hilt, a wooden shield base, a
        fitting of another metal) is taken whole as a component, exactly as the native recipe takes it."""
        if depth > 8:
            raise GenerationError(f'recipe chain too deep at {recipe.id}')
        out = out if out is not None else {'units': Fraction(0), 'aux': {}, 'chain': []}
        out['chain'].append(recipe.id)
        for ing, n in recipe.ingredients:
            k = Fraction(n, recipe.count) * scale
            kind = self.classify(ing, gem_family)
            expand = None
            if kind[0] == 'metal' and kind[1] == primary:
                out['units'] += kind[2] * k
                continue
            if kind[0] == 'gem' and gem_family:
                out['units'] += 9 * k
                continue
            if kind[0] == 'piece':
                same = self.primary_of_piece(kind[1]) == primary or (gem_family and self.metal_of(kind[1], gem_family).get(gem_family))
                expand = kind[1] if same else None
            elif kind[0] == 'part' and primary in self.metal_of(kind[1], gem_family):
                expand = kind[1]
            sub = self.manufacturing_recipe(expand) if expand else None
            if sub is not None:
                self.emit(sub, primary, gem_family, k, out, depth + 1)
            else:
                add_aux(out, ing, k)
        return out


def add_aux(out, ing, count):
    key = ing_key(ing)
    prev = out['aux'].get(key)
    out['aux'][key] = (ing, (prev[1] if prev else Fraction(0)) + count)


# ------------------------------------------------------------------------------------------------ decisions

class Decision:
    def __init__(self, item, disposition, reason, forge=None, crafting=None, replaces=(), derived=(), state_of=None):
        self.item, self.disposition, self.reason = item, disposition, reason
        self.forge, self.crafting = forge, crafting
        self.replaces, self.derived, self.state_of = sorted(replaces), sorted(derived), state_of


def pattern_for(item, info, m):
    if info.get('slot'):
        return {'head': 'helmet', 'chest': 'chestplate', 'legs': 'leggings', 'feet': 'boots'}[info['slot']]
    if info.get('kind') == 'shield':
        return 'shield'
    path = item.split(':', 1)[1]
    best = None
    for key, pattern in m.PATTERNS.items():
        if path == key or path.endswith('_' + key):
            if best is None or len(key) > len(best[0]):
                best = (key, pattern)
    if best is None:
        raise GenerationError(f'no anvil pattern for {item}: add its type to PATTERNS')
    return best[1]


def ing_items(world, ing):
    return world.ingredient_items(ing) if ing else set()


def single(world, ing):
    items = sorted(world.ingredient_items(ing)) if ing else []
    return items[0] if items else None


TEMPLATE_NAMES = {'magistuarmory:gilding_template': 'gilding', 'magistuarmory:darkening_template': 'darkening',
                  'magistuarmoryaddon:fully_gilding_template': 'full gilding', 'ekotsk:whitening_template': 'whitening',
                  'minecraft:netherite_upgrade_smithing_template': 'template upgrade'}


def describe_transform(world, r):
    template = single(world, r.template)
    addition = single(world, r.addition)
    return f'{TEMPLATE_NAMES.get(template, "smithing transform")} with {addition}'


def decide(world, valuer, item, pairs):
    m = world.m
    info = world.info(item)
    if item in m.SPECIAL_FORGE:
        spec = m.SPECIAL_FORGE[item]
        forge = {'family': spec['material'], 'units': spec['units'], 'pattern': spec['pattern'],
                 'aux': [(ing, n) for ing, n in spec['auxiliary']], 'exact': str(spec['units']), 'chain': []}
        return Decision(item, 'forge_default', f'new recipe (reviewed balance, the mod ships none)', forge=forge)
    if item in m.SPECIAL_CRAFTING:
        return Decision(item, 'native_nonmetal_recipe', 'crafting recipe shipped by Immersive Smithing (the mod ships none)',
                        crafting=m.SPECIAL_CRAFTING[item])
    if item in m.PROTECTED:
        disposition, reason = m.PROTECTED[item]
        return Decision(item, disposition, reason)
    if item in m.NON_EQUIPMENT:
        return Decision(item, 'non_equipment', m.NON_EQUIPMENT[item])
    if item in pairs:
        return Decision(item, 'native_transformation', f'the open visor of {pairs[item]}, switched with Swappable Helms',
                        state_of=pairs[item])
    recipes = [r for r in world.by_result.get(item, []) if r.id not in m.IGNORE_RECIPES]
    crafting = sorted((r for r in recipes if r.kind == 'crafting'), key=lambda r: r.id)
    smithing = sorted((r for r in recipes if r.kind == 'smithing'), key=lambda r: r.id)
    if not crafting and not smithing:
        raise GenerationError(f'{item} has no recipe and no decision: classify it in the manifest')

    for r in crafting:
        used = {i for ing, _ in r.ingredients for i in world.ingredient_items(ing)}
        if used & m.ASSEMBLY_PARTS:
            return Decision(item, 'native_assembly', m.ASSEMBLY_REASON)
        if used & m.OWN_SMITHING_PARTS:
            return Decision(item, 'native_assembly', m.OWN_SMITHING_REASON)

    upgrades = [r for r in smithing if ing_items(world, r.template) == {m.NETHERITE_TEMPLATE}
                and world.metal_of.get(single(world, r.addition), (None,))[0] == m.NETHERITE_FAMILY]
    transforms = [r for r in smithing if r not in upgrades]
    if upgrades and not crafting:
        base = single(world, upgrades[0].base)
        base_recipe = valuer.manufacturing_recipe(base)
        if base_recipe is None:
            raise GenerationError(f'{item}: netherite upgrade of {base}, which has no crafting recipe')
        out = valuer.emit(base_recipe, m.NETHERITE_FAMILY, gem_family=m.NETHERITE_FAMILY)
        return Decision(item, 'forge_default', f'netherite upgrade: the shape of {base} in netherite',
                        forge=finish(item, info, m, m.NETHERITE_FAMILY, out), replaces=[r.id for r in upgrades])

    plans = []
    for r in crafting:
        metal = {}
        for ing, n in r.ingredients:
            kind = valuer.classify(ing)
            share = Fraction(n, r.count)
            if kind[0] == 'metal':
                metal[kind[1]] = metal.get(kind[1], 0) + kind[2] * share
            elif kind[0] in ('piece', 'part'):
                for family, units in valuer.metal_of(kind[1]).items():
                    metal[family] = metal.get(family, 0) + units * share
        repair = info.get('repair_families', [])
        primary = m.PRIMARY.get(item)
        if primary is None and len(repair) == 1 and repair[0] in metal:
            primary = repair[0]
        if primary is None and repair and not (set(repair) & set(metal)) and not metal:
            plans.append((r, None, f'no metal ingredient (although repaired with {", ".join(repair)})'))
            continue
        if primary is None and info.get('repair_other') and not repair and item not in m.FORCE_FORGE:
            plans.append((r, None, 'not metal work: repaired with ' + ', '.join(info['repair_other'])))
            continue
        if primary is None and metal:
            ranked = sorted(metal.items(), key=lambda kv: -kv[1])
            if len(ranked) > 1 and ranked[0][1] == ranked[1][1]:
                raise GenerationError(f'{item}: metals {metal} tie; set PRIMARY in the manifest')
            primary = ranked[0][0]
        if primary is None:
            plans.append((r, None, 'no metal ingredient'))
            continue
        if metal.get(primary, 0) < 9 and item not in m.FORCE_FORGE:
            # Under one ingot: metal work only if the metal outweighs every other part once flattened (a dart's head).
            flat = valuer.emit(r, primary)
            others = sum(n for _, n in flat['aux'].values())
            if flat['units'] < others:
                plans.append((r, None, f'not predominantly metal: {flat["units"]} units of {primary} on {others} other parts'))
                continue
        plans.append((r, primary, 'keep' if (item in m.KEEP_NATIVE or r.id in m.KEEP_NATIVE) else 'replace'))
    forged = [(r, p, how) for r, p, how in plans if p is not None]
    if not forged:
        if transforms:
            return Decision(item, 'native_transformation', 'made from another piece: '
                            + '; '.join(describe_transform(world, r) for r in transforms))
        return Decision(item, 'native_nonmetal_recipe', plans[0][2] if plans else 'no manufacturing recipe')
    variants = {}
    for r, primary, how in forged:
        forge = finish(item, info, m, primary, valuer.emit(r, primary))
        key = json.dumps([forge['family'], forge['units'], [(ing_key(i), n) for i, n in forge['aux']]])
        v = variants.setdefault(key, {'forge': forge, 'replaces': [], 'derived': []})
        (v['derived'] if how == 'keep' else v['replaces']).append(r.id)
    if len(variants) > 1:
        costs = {k: v['replaces'] + v['derived'] for k, v in variants.items()}
        raise GenerationError(f'{item}: its recipes cost differently {costs}; ignore one in the manifest')
    v = next(iter(variants.values()))
    reason = 'forged from the metal of its own recipe' if v['replaces'] else 'forged next to its native recipe, which is kept'
    if transforms:
        reason += '; also made by ' + '; '.join(describe_transform(world, r) for r in transforms)
    return Decision(item, 'forge_default', reason, forge=v['forge'], replaces=v['replaces'], derived=v['derived'])


def finish(item, info, m, family, out):
    units = math.floor(out['units'])
    if units < 1:
        raise GenerationError(f'{item}: less than one unit of {family} ({out["units"]})')
    aux = []
    for key in sorted(out['aux']):
        ing, n = out['aux'][key]
        aux.append((ing, math.ceil(n)))
    return {'family': family, 'units': units, 'pattern': pattern_for(item, info, m), 'aux': aux,
            'exact': str(out['units']), 'chain': out['chain']}


# ------------------------------------------------------------------------------------------------ output

def conditions_for(world, item, extra_items, version_range):
    mod = item.split(':')[0]
    out = [{'type': 'forge:mod_loaded', 'modid': mod}]
    if version_range:
        out.append({'type': 'immersive_smithing:mod_version', 'modid': mod, 'versions': version_range})
    for other in sorted(set(i.split(':')[0] for i in extra_items) - {'minecraft', mod}):
        out.append({'type': 'forge:mod_loaded', 'modid': other})
    for i in [item] + sorted(set(extra_items) - {item}):
        if not i.startswith('minecraft:'):
            out.append({'type': 'forge:item_exists', 'item': i})
    return out


def forge_json(world, d):
    f = d.forge
    referenced = [i for ing, _ in f['aux'] for i in referenced_items(ing)]
    mod = d.item.split(':')[0]
    obj = {'conditions': conditions_for(world, d.item, referenced, world.m.VERSION_RANGES.get(mod)),
           'type': 'immersive_smithing:smithing', 'material': f['family'], 'metal_units': f['units']}
    if f['aux']:
        obj['auxiliary'] = [{'ingredient': ing, 'count': n} for ing, n in f['aux']]
    obj['result'] = {'item': d.item}
    obj['forge_pattern'] = 'immersive_smithing:standard'
    obj['anvil_pattern'] = 'immersive_smithing:' + f['pattern']
    if d.replaces:
        obj['replaces'] = d.replaces
    if d.derived:
        obj['derived_from'] = d.derived
    return obj


def crafting_json(world, d):
    spec = d.crafting
    referenced = [i for ing in spec['key'].values() for i in referenced_items(ing)]
    return {'conditions': conditions_for(world, d.item, referenced, None), 'type': 'minecraft:crafting_shaped',
            'category': 'combat', 'pattern': spec['pattern'], 'key': spec['key'], 'result': {'item': d.item}}


def dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + '\n'


def visor_pairs(world, swappable):
    """Swappable Helms' default pairs (they live in its code): open state -> closed piece, both registered."""
    pairs = {}
    if swappable is None:
        return pairs
    for s in swappable.class_strings('com/ice_the_noob/quick_swap/Config.class'):
        m = re.fullmatch(r'([a-z0-9_.]+:[a-z0-9_/.]+)-([a-z0-9_.]+:[a-z0-9_/.]+)', s)
        if not m:
            continue
        a, b = m.groups()
        if not (world.exists(a) and world.exists(b)):
            continue
        open_, closed = (a, b) if 'open' in a.split(':')[1] or a.startswith('epicknights_open_helms:') else (b, a)
        if closed.startswith('epicknights_open_helms:') and not open_.startswith('epicknights_open_helms:'):
            open_, closed = closed, open_
        pairs[open_] = closed
    return pairs


def generate(world, valuer, swappable):
    m = world.m
    pairs = visor_pairs(world, swappable)
    files = {}
    decisions = {}
    errors = []
    for item in sorted(world.items):
        mod = item.split(':')[0]
        if mod not in m.MODS or not world.is_equipment(item):
            continue
        try:
            decisions[item] = decide(world, valuer, item, pairs)
        except GenerationError as e:
            errors.append(str(e))
    if errors:
        raise GenerationError('\n  '.join(['undecided items:'] + errors))

    coverage = defaultdict(list)
    quality = defaultdict(set)
    candidates = defaultdict(set)
    non_recyclable = defaultdict(set)
    for item, d in sorted(decisions.items()):
        mod = item.split(':')[0]
        integration = m.MODS[mod][0]
        path = item.split(':')[1]
        if d.forge:
            files[OUT_RECIPES / mod / f'{path}.json'] = dump(forge_json(world, d))
            if not world.info(item).get('candidate'):
                candidates[integration].add(item)
        else:
            if d.crafting:
                files[OUT_RECIPES / mod / f'{path}.json'] = dump(crafting_json(world, d))
            coverage[mod].append(d)
        info = world.info(item)
        # Smithing quality belongs on metal work: pieces repaired with metal, visor states, and loot that is not
        # repaired with wood, stone or cloth (relics often have no repair material at all).
        metal_piece = bool(info.get('repair_families')) or d.state_of is not None \
            or (d.disposition == 'protected_loot' and not info.get('repair_other'))
        if d.disposition in ('native_transformation', 'protected_loot', 'disabled_by_upstream') and info.get('damageable') and metal_piece:
            quality[integration].add(item)
        if d.disposition == 'protected_loot' and 'relic' in d.reason:
            non_recyclable[integration].add(item)

    def policies_of(entries):
        groups = defaultdict(list)
        for d in entries:
            groups[(d.disposition, d.reason, d.state_of)].append(d.item)
        out = []
        for (disposition, reason, state_of), items in sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1], kv[0][2] or '')):
            p = {'items': sorted(items), 'disposition': disposition, 'reason': reason}
            if state_of:
                p['state_of'] = state_of
            out.append(p)
        return out

    for mod, entries in sorted(coverage.items()):
        plain = [d for d in entries if not d.state_of]
        states = [d for d in entries if d.state_of]
        files[OUT_COVERAGE / f'{mod}.json'] = dump({'conditions': [{'type': 'forge:mod_loaded', 'modid': mod}], 'mod': mod,
                                                     'tested_version': world.registry['mods'][mod], 'policies': policies_of(plain)})
        if states:
            # Visor states are reached with Swappable Helms only. Without it they have no route, and the second file
            # says so (files are read in id order: <mod>_visor_states before <mod>_without_swappable_helms).
            files[OUT_COVERAGE / f'{mod}_visor_states.json'] = dump({
                'conditions': [{'type': 'forge:mod_loaded', 'modid': mod}, {'type': 'forge:mod_loaded', 'modid': 'quick_swap'}],
                'mod': mod, 'policies': policies_of(states)})
            files[OUT_COVERAGE / f'{mod}_without_swappable_helms.json'] = dump({
                'conditions': [{'type': 'forge:mod_loaded', 'modid': mod}], 'mod': mod,
                'policies': [{'items': sorted(d.item for d in states), 'disposition': 'disabled_by_upstream',
                              'reason': 'an open visor state: reached only by switching a closed helmet with Swappable Helms'}]})

    for integration, items in sorted(quality.items()):
        files[OUT_TAGS / integration / 'quality_eligible.json'] = dump(
            {'replace': False, 'values': [{'id': i, 'required': False} for i in sorted(items)]})
    for integration, items in sorted(candidates.items()):
        files[OUT_TAGS / integration / 'smithable_equipment.json'] = dump(
            {'replace': False, 'values': [{'id': i, 'required': False} for i in sorted(items)]})
    for integration, items in sorted(non_recyclable.items()):
        files[OUT_TAGS / integration / 'non_recyclable.json'] = dump(
            {'replace': False, 'values': [{'id': i, 'required': False} for i in sorted(items)]})

    for plate, family in sorted(m.PLATE_SOURCES.items()):
        recipe = valuer.manufacturing_recipe(plate)
        units = valuer.metal_of(plate).get(family) if recipe else None
        if units is None or units.denominator != 1:
            raise GenerationError(f'{plate} is not a whole number of {family} units ({units})')
        mod = plate.split(':')[0]
        conditions = [{'type': 'forge:mod_loaded', 'modid': mod}, {'type': 'forge:item_exists', 'item': plate}]
        files[OUT_MATERIALS / mod / f'{plate.split(":")[1]}.json'] = dump({
            'conditions': conditions, 'family': family, 'sources_only': True,
            'sources': [{'item': plate, 'units': int(units)}]})
    for family, spec in sorted(m.NEW_FAMILIES.items()):
        name = family.split(':')[1]
        files[OUT_MATERIALS / spec['mod'] / f'{name}.json'] = dump({
            'conditions': [{'type': 'forge:mod_loaded', 'modid': spec['mod']},
                           {'type': 'forge:not', 'value': {'type': 'forge:tag_empty', 'tag': f'forge:ingots/{name}'}}],
            'family': family, 'display_name': spec['display_name'],
            'sources': [{'tag': f'forge:{form}/{name}', 'units': units} for form, units in m.FORMS.items()],
            'melt_time_multiplier': spec['melt_time_multiplier'], 'requires_ignition': True, 'tint': spec['tint'],
            'upgrade_policy': 'shape'})
    return files, decisions


def owned(path):
    """Generated files: everything under the integration folders this tool writes."""
    roots = [OUT_RECIPES, OUT_COVERAGE, OUT_MATERIALS, OUT_TAGS]
    return any(str(path).startswith(str(r)) for r in roots)


def existing_files(m):
    out = set()
    mods = set(m.MODS)
    integrations = {v[0] for v in m.MODS.values()}
    for mod in mods:
        for root in (OUT_RECIPES / mod, OUT_MATERIALS / mod):
            if root.exists():
                out |= {p for p in root.rglob('*.json')}
        for p in OUT_COVERAGE.glob(f'{mod}*.json') if OUT_COVERAGE.exists() else []:
            out.add(p)
    for integration in integrations:
        root = OUT_TAGS / integration
        if root.exists():
            out |= {p for p in root.rglob('*.json')}
    return out


def refresh_registry(log_dir, m):
    mods = list(m.MODS) + list(m.SUPPORT_JARS)
    snapshot = {'mods': {}, 'items': {}}
    for mod in sorted(mods):
        path = pathlib.Path(log_dir) / f'immersive_smithing_registry_{mod}.json'
        if not path.exists():
            raise GenerationError(f'{path} missing: run the GameTests with -PregistryDump=all and every pinned mod')
        d = json.loads(path.read_text())
        snapshot['mods'][mod] = d['version']
        for i in d['items']:
            classes = [c.rsplit('.', 1)[-1] for c in i['classes']]
            if i.get('armorSlot'):
                kind = 'armor'
            elif i['shield'] and i['attackDamage'] <= 0:
                kind = 'shield'
            elif any(c in ('ProjectileWeaponItem', 'BowItem', 'CrossbowItem', 'TridentItem') for c in classes):
                kind = 'ranged'
            elif i['attackDamage'] > 0 or 'TieredItem' in classes:
                kind = 'weapon'
            elif i['maxStack'] == 1 and i['damageable']:
                kind = 'equipment'
            else:
                kind = 'item'
            entry = {'kind': kind, 'stack': i['maxStack'], 'damageable': i['damageable'], 'candidate': i['candidate'],
                     'class': classes[0], 'repair_families': i['repairFamilies'], 'repair_other': i.get('repairOther', [])}
            if i.get('armorSlot'):
                entry['slot'] = i['armorSlot']
            snapshot['items'][i['id']] = entry
    REGISTRY.write_text(json.dumps(snapshot, indent=1, sort_keys=True) + '\n')
    print(f'{REGISTRY.relative_to(ROOT)}: {len(snapshot["items"])} items of {len(snapshot["mods"])} mods')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--write', action='store_true', help='rewrite the generated files')
    mode.add_argument('--check', action='store_true', help='compare with the committed files (default)')
    mode.add_argument('--refresh-registry', metavar='LOG_DIR', help='rebuild tools/epic_compat_registry.json from a dump')
    parser.add_argument('--report', action='store_true', help='print every decision')
    args = parser.parse_args()
    m = load_manifest()
    try:
        if args.refresh_registry:
            refresh_registry(args.refresh_registry, m)
            return
        if not REGISTRY.exists():
            raise GenerationError('tools/epic_compat_registry.json missing: run --refresh-registry first')
        registry = json.loads(REGISTRY.read_text())
        inputs = {'minecraft': VANILLA_JAR, 'forge': FORGE_JAR}
        for mod, (_, source) in m.MODS.items():
            inputs[mod] = cached_jar(source)
        for mod, source in m.SUPPORT_JARS.items():
            inputs[mod] = cached_jar(source)
        jars = {mod: Jar(path) for mod, path in inputs.items()}
        world = World(m, registry, [jars['minecraft'], jars['forge']] + [jars[k] for k in sorted(jars) if k not in ('minecraft', 'forge')])
        valuer = Valuer(world)
        files, decisions = generate(world, valuer, jars.get('quick_swap'))
        lock = {'inputs': {mod: {'file': pathlib.Path(p).name, 'sha256': sha256(p), 'version': registry['mods'].get(mod, '-')}
                           for mod, p in sorted(inputs.items())},
                'manifest_sha256': sha256(ROOT / 'tools/epic_compat_manifest.py'),
                'registry_sha256': sha256(REGISTRY)}
        files[LOCK] = json.dumps(lock, indent=2) + '\n'
    except GenerationError as e:
        print(f'error: {e}', file=sys.stderr)
        sys.exit(2)

    stale = existing_files(m) - set(files)
    changed = [p for p, text in files.items() if not p.exists() or p.read_text() != text]
    if args.write:
        for p in stale:
            p.unlink()
        for p in changed:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(files[p])
    counts = defaultdict(int)
    for d in decisions.values():
        counts[d.disposition] += 1
    if args.report:
        for item, d in sorted(decisions.items()):
            cost = f" {d.forge['units']} {d.forge['family']} (exact {d.forge['exact']}) + {[(ing_key(i), n) for i, n in d.forge['aux']]}" if d.forge else ''
            print(f'{item}: {d.disposition}: {d.reason}{cost}')
    print(f'{len(decisions)} items: ' + ', '.join(f'{k} {v}' for k, v in sorted(counts.items())))
    if args.write:
        print(f'wrote {len(changed)} files, removed {len(stale)}')
    elif changed or stale:
        print(f'{len(changed)} generated files differ and {len(stale)} are stale; run with --write', file=sys.stderr)
        for p in sorted(changed)[:10] + sorted(stale)[:10]:
            print('  ' + str(p.relative_to(ROOT)), file=sys.stderr)
        sys.exit(1)
    else:
        print('generated files are up to date')


if __name__ == '__main__':
    main()
