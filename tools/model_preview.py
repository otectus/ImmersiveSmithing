#!/usr/bin/env python3
"""Renders block models to isometric PNG previews and counts their geometry, offline.

  python3 tools/model_preview.py smiths_forge smiths_anvil --out build/model-preview [--scale 8] [--views ne,sw]
  python3 tools/model_preview.py --stats                      # cuboids, faces and textures of the station models
  python3 tools/model_preview.py --check                      # budgets and resolution; exits 1 on any violation

Resolves parent models and texture variables from src/main/resources/assets/immersive_smithing (and minecraft:block/
block, which has no geometry). Faces are drawn with Minecraft-like directional shading (top 1.0, north/south 0.8,
east/west 0.6, bottom 0.5) in back-to-front order. This is a preview for reviewing silhouettes and texture placement,
not a replacement for the in-game screenshots the pack-test agent takes.

--check enforces the station geometry budgets (cuboids and faces per model and for the four stations together) and
that every blockstate variant, model parent and face texture resolves: model files exist, the variants cover every
combination of their properties, element bounds, rotations and UVs are legal, and textures exist (vanilla ones are
looked up in the Forge Gradle client jar when it is in the Gradle cache).
"""
import argparse
import itertools
import json
import pathlib
import sys
import zipfile
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/immersive_smithing'
STATIONS = ['smiths_forge', 'smiths_forge_lit', 'smiths_anvil', 'smiths_trough', 'smiths_grindstone']
# (cuboids, faces) per model; the lit forge replaces the forge model, so the total counts the four station models.
BUDGETS = {'smiths_forge': (14, 84), 'smiths_forge_lit': (14, 84), 'smiths_anvil': (10, 60),
           'smiths_trough': (7, 42), 'smiths_grindstone': (7, 42)}
TOTAL_BUDGET = (38, 228)
BLOCKS = ['smiths_forge', 'smiths_anvil', 'smiths_trough', 'smiths_grindstone']
VANILLA_JAR = pathlib.Path.home() / '.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar'
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def load_model(name):
    """Elements and resolved textures of a block model, following parents."""
    textures, elements = {}, None
    chain = []
    while name:
        ns, path = name.split(':', 1) if ':' in name else ('immersive_smithing', name)
        if ns != 'immersive_smithing':
            break
        path = path if path.startswith('block/') else 'block/' + path
        data = json.loads((ASSETS / 'models' / (path + '.json')).read_text())
        chain.append(data)
        name = data.get('parent')
    for data in reversed(chain):
        textures.update(data.get('textures', {}))
        if 'elements' in data:
            elements = data['elements']

    def resolve(ref, depth=0):
        while ref.startswith('#') and depth < 10:
            ref = textures.get(ref[1:], ref)
            depth += 1
        return ref
    return elements or [], {k: resolve(v) for k, v in textures.items()}


def texture_image(ref, cache={}):
    if ref not in cache:
        ns, path = ref.split(':', 1)
        img = Image.open(ASSETS / 'textures' / (path + '.png')).convert('RGBA')
        if img.height > img.width:  # animated strip: first frame
            img = img.crop((0, 0, img.width, img.width))
        cache[ref] = img
    return cache[ref]


def face_corners(element, face):
    (x0, y0, z0), (x1, y1, z1) = element['from'], element['to']
    # Corners in texture order: top-left, top-right, bottom-right, bottom-left as seen looking at the face.
    return {
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]


NORMALS = {'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}


def render(name, scale=8, view='ne'):
    """Depth-buffered isometric render: every texel of every face is projected, the nearest one wins."""
    import numpy as np
    elements, textures = load_model(name)
    flip = view == 'sw'

    def rot(p):
        x, y, z = p
        return (16 - x, y, 16 - z) if flip else (x, y, z)

    size = int(30 * scale)
    ox, oy = size / 2, size * 0.62
    color = np.zeros((size, size, 4), dtype=np.float64)
    depth = np.full((size, size), -1e9)
    for e in elements:
        for face, data in e.get('faces', {}).items():
            nx, ny, nz = NORMALS[face]
            if flip:
                nx, nz = -nx, -nz
            if nx + ny * 1.2 + nz <= 0.01:
                continue  # facing away from the camera
            ref = textures.get(data['texture'].lstrip('#'), data['texture'])
            if ref.startswith('#'):
                continue
            tex = np.asarray(texture_image(ref), dtype=np.float64)
            k = tex.shape[1] / 16
            u0, v0, u1, v1 = data.get('uv', [0, 0, 16, 16])
            c = [np.array(rot(q), dtype=np.float64) for q in face_corners(e, face)]
            ea, eb = c[1] - c[0], c[3] - c[0]
            steps = int(max(np.linalg.norm(ea), np.linalg.norm(eb)) * scale * 2) + 2
            s_, t_ = np.meshgrid(np.linspace(0, 1, steps), np.linspace(0, 1, steps))
            pts = c[0][None, None, :] + s_[..., None] * ea + t_[..., None] * eb
            sx = ox + (pts[..., 0] - pts[..., 2]) * 0.866 * scale
            sy = oy + ((pts[..., 0] + pts[..., 2]) * 0.5 - pts[..., 1]) * scale
            d = pts[..., 0] + pts[..., 2] + pts[..., 1] * 1.2
            u = u0 + (u1 - u0) * s_
            v = v0 + (v1 - v0) * t_
            rotation = data.get('rotation', 0)
            for _ in range(rotation // 90):
                u, v = 16 - v, u
            tu = np.clip((u * k).astype(int), 0, tex.shape[1] - 1)
            tv = np.clip((v * k).astype(int), 0, tex.shape[0] - 1)
            texel = tex[tv, tu]
            ix, iy = sx.astype(int), sy.astype(int)
            ok = (ix >= 0) & (ix < size) & (iy >= 0) & (iy < size) & (texel[..., 3] > 0)
            for x_, y_, dd, col in zip(ix[ok], iy[ok], d[ok], texel[ok]):
                if dd > depth[y_, x_]:
                    depth[y_, x_] = dd
                    color[y_, x_, :3] = col[:3] * SHADE[face]
                    color[y_, x_, 3] = 255
    out = Image.fromarray(color.clip(0, 255).astype('uint8'), 'RGBA')
    return out.crop(out.getbbox()) if out.getbbox() else out


def stats(name):
    elements, textures = load_model(name)
    faces = sum(len(e.get('faces', {})) for e in elements)
    culled = sum(1 for e in elements for f in e.get('faces', {}).values() if 'cullface' in f)
    used = sorted({textures.get(f['texture'].lstrip('#'), f['texture']) for e in elements for f in e.get('faces', {}).values()})
    return len(elements), faces, culled, used


def check():
    """Returns the list of violations of the budgets and of model resolution."""
    problems = []
    vanilla = None
    if VANILLA_JAR.exists():
        with zipfile.ZipFile(VANILLA_JAR) as jar:
            vanilla = {n for n in jar.namelist() if n.startswith('assets/minecraft/')}

    def texture_exists(ref):
        ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        if ns == 'immersive_smithing':
            return (ASSETS / 'textures' / (path + '.png')).exists()
        return vanilla is None or f'assets/{ns}/textures/{path}.png' in vanilla

    def model_exists(ref):
        ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        if ns == 'immersive_smithing':
            return (ASSETS / 'models' / (path + '.json')).exists()
        return vanilla is None or f'assets/{ns}/models/{path}.json' in vanilla

    totals = [0, 0]
    for name in STATIONS:
        elements, textures = load_model(name)
        cuboids, faces = len(elements), sum(len(e.get('faces', {})) for e in elements)
        max_cuboids, max_faces = BUDGETS[name]
        if cuboids > max_cuboids or faces > max_faces:
            problems.append(f'{name}: {cuboids} cuboids / {faces} faces exceeds {max_cuboids} / {max_faces}')
        if name in BLOCKS:
            totals[0] += cuboids
            totals[1] += faces
        for i, e in enumerate(elements):
            where = f'{name} element {i}'
            if any(not -16 <= v <= 32 for v in e['from'] + e['to']) or any(a > b for a, b in zip(e['from'], e['to'])):
                problems.append(f'{where}: bounds {e["from"]} to {e["to"]}')
            rotation = e.get('rotation')
            if rotation and rotation.get('angle', 0) not in (-45, -22.5, 0, 22.5, 45):
                problems.append(f'{where}: rotation angle {rotation.get("angle")}')
            for face, data in e.get('faces', {}).items():
                ref = textures.get(data['texture'].lstrip('#'), data['texture'])
                if ref.startswith('#'):
                    problems.append(f'{where} {face}: unresolved texture {data["texture"]}')
                elif not texture_exists(ref):
                    problems.append(f'{where} {face}: missing texture {ref}')
                if any(not 0 <= v <= 16 for v in data.get('uv', [0, 0, 16, 16])):
                    problems.append(f'{where} {face}: uv {data["uv"]}')
                if data.get('rotation', 0) not in (0, 90, 180, 270):
                    problems.append(f'{where} {face}: uv rotation {data["rotation"]}')
        particle = textures.get('particle')
        if not particle or particle.startswith('#') or not texture_exists(particle):
            problems.append(f'{name}: particle texture {particle!r} does not resolve')
    if totals[0] > TOTAL_BUDGET[0] or totals[1] > TOTAL_BUDGET[1]:
        problems.append(f'stations together: {totals[0]} cuboids / {totals[1]} faces exceeds {TOTAL_BUDGET[0]} / {TOTAL_BUDGET[1]}')

    for block in BLOCKS:
        state_file = ASSETS / 'blockstates' / f'{block}.json'
        variants = json.loads(state_file.read_text()).get('variants')
        if not variants:
            problems.append(f'{block}: blockstate has no variants')
            continue
        keys = [dict(kv.split('=') for kv in key.split(',')) if key else {} for key in variants]
        names = sorted({k for key in keys for k in key})
        values = {n: sorted({key[n] for key in keys if n in key}) for n in names}
        expected = {tuple(zip(names, combo)) for combo in itertools.product(*(values[n] for n in names))}
        present = {tuple(sorted(key.items())) for key in keys}
        if expected != present:
            problems.append(f'{block}: variants miss {sorted(expected - present)}')
        for key, variant in variants.items():
            for v in variant if isinstance(variant, list) else [variant]:
                if not model_exists(v['model']):
                    problems.append(f'{block}[{key}]: model {v["model"]} missing')
                if v.get('x', 0) not in (0, 90, 180, 270) or v.get('y', 0) not in (0, 90, 180, 270):
                    problems.append(f'{block}[{key}]: rotation x={v.get("x", 0)} y={v.get("y", 0)}')
        item = ASSETS / 'models' / 'item' / f'{block}.json'
        parent = json.loads(item.read_text()).get('parent') if item.exists() else None
        if not parent or not model_exists(parent if parent.startswith('immersive_smithing:') or ':' in parent else 'minecraft:' + parent):
            problems.append(f'{block}: item model parent {parent!r} does not resolve')
    return problems, totals


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('models', nargs='*', default=STATIONS[:1])
    parser.add_argument('--out', default=str(ROOT / 'build/model-preview'))
    parser.add_argument('--scale', type=int, default=8)
    parser.add_argument('--views', default='ne,sw')
    parser.add_argument('--stats', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.check:
        problems, totals = check()
        for name in STATIONS:
            cuboids, faces, culled, used = stats(name)
            max_cuboids, max_faces = BUDGETS[name]
            print(f'{name:22s} cuboids {cuboids:3d}/{max_cuboids:<3d} faces {faces:4d}/{max_faces:<4d} (cullface {culled:2d})')
        print(f'{"four stations":22s} cuboids {totals[0]:3d}/{TOTAL_BUDGET[0]:<3d} faces {totals[1]:4d}/{TOTAL_BUDGET[1]:<4d}')
        for problem in problems:
            print('FAIL', problem)
        print('Model check:', 'FAILED' if problems else 'passed')
        sys.exit(1 if problems else 0)
    if args.stats:
        for name in STATIONS:
            cuboids, faces, culled, used = stats(name)
            print(f'{name:22s} cuboids {cuboids:3d}  faces {faces:4d}  (cullface {culled:2d})  textures {len(used)}')
        return
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for name in args.models:
        tiles = [render(name, args.scale, v) for v in args.views.split(',')]
        sheet = Image.new('RGBA', (sum(t.width for t in tiles) + 20 * len(tiles), max(t.height for t in tiles) + 20), (122, 140, 160, 255))
        x = 10
        for t in tiles:
            sheet.alpha_composite(t, (x, 10))
            x += t.width + 20
        sheet.save(out / f'{name}.png')
        print(out / f'{name}.png')


if __name__ == '__main__':
    main()
