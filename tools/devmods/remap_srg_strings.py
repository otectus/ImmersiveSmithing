#!/usr/bin/env python3
"""Makes Architectury-built Forge mods loadable in the ForgeGradle development runtime (official names).

ForgeGradle's fg.deobf renames a mod's references to Minecraft, but a mod built with Architectury Loom has its mixin
targets baked into the annotations as SRG names (m_142687_) with no refmap, and its access transformer is in SRG
too. Neither is remapped, so the mixins fail to apply in a dev run. This rewrites every SRG member token in the
jar's class constant pools and in META-INF/accesstransformer.cfg to the official name, using ForgeGradle's own
srg->official table (build/createSrgToMcp/output.srg). SRG ids are unique per member, so a token replacement is
exact. Used only by the -PsimplySwords run profile (build.gradle task remapDevMods); never part of a release.

Usage: remap_srg_strings.py <srg-to-official.srg> <out-dir> <jar>...
"""
import pathlib
import re
import struct
import sys
import zipfile

TOKEN = re.compile(rb'(?<![A-Za-z0-9_$])([mf]_\d+_)(?![A-Za-z0-9_$])')


def load_mapping(path):
    table = {}
    for line in pathlib.Path(path).read_text().splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'MD:' and len(parts) >= 5:
            srg, official = parts[1].rsplit('/', 1)[1], parts[3].rsplit('/', 1)[1]
        elif parts[0] == 'FD:' and len(parts) >= 3:
            srg, official = parts[1].rsplit('/', 1)[1], parts[2].rsplit('/', 1)[1]
        else:
            continue
        if re.fullmatch(r'[mf]_\d+_', srg):
            table[srg.encode()] = official.encode()
    return table


def rewrite(data, table):
    """Returns (bytes, count) with SRG tokens replaced inside the given bytes."""
    count = 0

    def sub(match):
        nonlocal count
        mapped = table.get(match.group(1))
        if mapped is None:
            return match.group(1)
        count += 1
        return mapped

    return TOKEN.sub(sub, data), count


def rewrite_class(data, table):
    """Rewrites CONSTANT_Utf8 entries; everything after the constant pool is copied as is (it references entries by index)."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return data, 0
    out = bytearray(data[:10])
    pool_count = struct.unpack('>H', data[8:10])[0]
    pos = 10
    index = 1
    changed = 0
    while index < pool_count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            text, n = rewrite(data[pos + 3:pos + 3 + length], table)
            changed += n
            out += bytes([1]) + struct.pack('>H', len(text)) + text
            pos += 3 + length
        else:
            size = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}.get(tag)
            if size is None:
                raise ValueError(f'unknown constant pool tag {tag} at {pos}')
            out += data[pos:pos + 1 + size]
            pos += 1 + size
            if tag in (5, 6):
                index += 1  # long and double take two slots
        index += 1
    out += data[pos:]
    return bytes(out), changed


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    table = load_mapping(sys.argv[1])
    out_dir = pathlib.Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    for jar in sys.argv[3:]:
        source = pathlib.Path(jar)
        target = out_dir / source.name
        total = 0
        with zipfile.ZipFile(source) as zin, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                data = zin.read(info.filename)
                if info.filename.endswith('.class'):
                    data, n = rewrite_class(data, table)
                    total += n
                elif info.filename == 'META-INF/accesstransformer.cfg':
                    data, n = rewrite(data, table)
                    total += n
                zout.writestr(info, data)
        print(f'{source.name}: {total} SRG names -> official ({target})')


if __name__ == '__main__':
    main()
