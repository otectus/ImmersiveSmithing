#!/usr/bin/env python3
"""Deterministic Forge jar and runtime inventory for the Epic Knights compatibility audit.

Use original jars, not remapped development jars. Jar resources describe declarations;
only registry/catalog dumps from the selected running profile establish active content.
Unmatched content remains UNRESOLVED and --require-complete fails instead of hiding it.
No jars, classes, textures or other upstream assets are copied to the output.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import tomllib
import zipfile


FIELDS = ["mod/version", "item ID", "role", "variant/state", "material family", "source recipe IDs",
          "acquisition mode", "replacement policy", "forge cost", "auxiliaries/base/template", "output count",
          "recycling policy", "quality behavior", "pattern", "verification status", "reason"]


def quality_behavior(entry):
    if entry.get('disposition') == 'external_workflow':
        return 'native owner; no added quality effects; existing metadata retained'
    if not entry.get('quality', False):
        return 'no smithing quality; native appearance/protection/behavior retained'
    return {'shield': 'durability quality; native blocking/cooldowns; efficacy neutral',
            'armor': 'durability and armor efficacy; native equipment and appearance',
            'ranged': 'durability; pinned launch/hit adapter applies weapon efficacy once; native ammo/reload',
            'weapon': 'durability and melee efficacy; native attributes/special behavior retained'}.get(
                entry.get('kind'), 'quality eligible; role-specific effects documented in epic-integrations.md')


def jar_inventory(path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if "META-INF/mods.toml" not in names:
            raise ValueError(f"{path}: no Forge mods.toml; select the Forge build")
        metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8-sig"))
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in names else ""
        version = re.search(r"^Implementation-Version: (.+)$", manifest, re.M)
        mods = []
        for mod in metadata["mods"]:
            declared = mod.get("version", "unknown")
            mods.append({"id": mod["modId"], "version": version.group(1).strip() if declared == "${file.jarVersion}" and version else declared})
        resources = {}
        invalid = {}
        for name in sorted(names):
            # Forge jar roots merge common and Forge resources. Never read a Fabric source tree here.
            if name.startswith("data/") and name.endswith(".json"):
                try:
                    resources[name] = json.loads(archive.read(name).decode("utf-8-sig"))
                except (UnicodeError, json.JSONDecodeError) as error:
                    invalid[name] = str(error)
        recipes = {}
        tags = {}
        for name, data in resources.items():
            recipe = re.fullmatch(r"data/([^/]+)/recipes/(.+)\.json", name)
            tag = re.fullmatch(r"data/([^/]+)/tags/items/(.+)\.json", name)
            if recipe:
                recipes[f"{recipe[1]}:{recipe[2]}"] = data
            elif tag:
                tags[f"{tag[1]}:{tag[2]}"] = data
        return {"filename": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "loader": metadata.get("modLoader"), "loader_range": metadata.get("loaderVersion"), "mods": mods,
                "dependencies": metadata.get("dependencies", {}), "recipes": recipes, "item_tags": tags,
                "invalid_resources": invalid,
                "loot_tables": sorted(n for n in resources if "/loot_tables/" in n),
                "config_classes": sorted(n for n in names if n.endswith(".class") and "config" in n.lower())}


def runtime_files(directory, prefix):
    found = {}
    if directory:
        for path in sorted(directory.glob(prefix + "*.json")):
            data = json.loads(path.read_text())
            namespace = data.get("namespace", data.get("mod"))
            if namespace in found:
                raise ValueError(f"Duplicate runtime namespace {namespace}: use one profile directory")
            found[namespace] = data
    return found


def coverage(jars, registries, catalogs):
    rows = []
    for jar in jars:
        for mod in jar["mods"]:
            namespace = mod["id"]
            registry = registries.get(namespace)
            catalog = catalogs.get(namespace, {})
            indexed = {i["id"]: i for i in catalog.get("items", [])}
            if registry is None:
                rows.append(dict(zip(FIELDS, [f"{namespace}/{mod['version']}", "*", "unknown", "unknown", "", "",
                    "UNRESOLVED", "preserve", "", "", "", "unverified", "unverified", "", "NOT_RUN",
                    "No runtime registry dump for this installation profile; jar declarations do not prove registered content"])))
                continue
            if registry["version"] != mod["version"]:
                raise ValueError(f"{namespace}: runtime {registry['version']} differs from jar {mod['version']}")
            for item in sorted(registry["items"], key=lambda i: i["id"]):
                entry = indexed.get(item["id"], {})
                native = {r["id"]: r for r in item.get("recipes", [])}
                forge = entry.get("forge", [])
                routes = entry.get("native", []) or [
                    {"recipe": rid, "state": "active native route; replacement not audited"} for rid in sorted(native)
                ] or [{"recipe": "", "state": "no active recipe result"}]
                for route in routes:
                    rid = route["recipe"]
                    raw = jar["recipes"].get(rid, {})
                    description = raw if isinstance(raw, dict) else {}
                    plan = [f for f in forge if f.get("from") == rid or rid in f.get("replaces", [])]
                    disposition = entry.get("disposition", "UNRESOLVED")
                    reason = entry.get("reason", "Registered item absent from runtime coverage report")
                    if disposition == "missing_support":
                        disposition = "UNRESOLVED"
                    if plan:
                        disposition = ";".join(sorted({f.get("operation", disposition) for f in plan}))
                    row = [f"{namespace}/{mod['version']}", item["id"], entry.get("kind", "unknown"),
                           route.get("result_nbt", native.get(rid, {}).get("result_nbt", "native acquisition/state; see behavioral evidence")),
                           ";".join(sorted({f["material"] for f in plan})), rid, disposition,
                           route["state"], json.dumps([f["units"] for f in plan]),
                           json.dumps({"forge": [f.get("components", []) for f in plan],
                                       "native_inputs": native.get(rid, {}).get("complete_ingredients", route.get("ingredients", [])),
                                       "base": description.get("base"), "template": description.get("template")}, sort_keys=True),
                           str(native.get(rid, {}).get("count", route.get("result_count", ""))), entry.get("recycling", "unverified"),
                           quality_behavior(entry), entry.get("shape", ""),
                           "RUNTIME_INVENTORIED" if disposition != "UNRESOLVED" else "UNRESOLVED", reason]
                    rows.append(dict(zip(FIELDS, row)))
            # These native serializers assemble from real input NBT and cannot truthfully
            # advertise a fixed output item. Keep them visible as routes, not fake items.
            for route in registry.get("dynamic_recipes", []):
                preserved = route['active'] and not route['suppressed']
                rows.append(dict(zip(FIELDS, [f"{namespace}/{mod['version']}", "* (dynamic output)",
                    "native dynamic process", "input-dependent output/state", "", route['id'],
                    "NATIVE_DYNAMIC_PROCESS" if preserved else "UNRESOLVED", "native; active" if preserved else "inactive or suppressed",
                    "[]", "native serializer " + route['serializer'], "native assembly", "input-dependent; no new salvage",
                    "native output; see per-item quality policy and transformation behavioral gates", "native",
                    "RUNTIME_INVENTORIED" if preserved else "UNRESOLVED",
                    "Native input-dependent serializer " + route['class'] + "; see transformation behavioral gates"])))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jars", type=Path, nargs="+", required=True)
    parser.add_argument("--runtime", type=Path, help="One profile's registry and catalog dump directory")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    jars = [jar_inventory(p) for p in sorted(args.jars)]
    mods = [m["id"] for j in jars for m in j["mods"]]
    if len(mods) != len(set(mods)):
        parser.error("Multiple versions of one mod: inventory each installation profile separately")
    registries = runtime_files(args.runtime, "immersive_smithing_registry_")
    catalogs = runtime_files(args.runtime, "immersive_smithing_catalog_")
    rows = coverage(jars, registries, catalogs)
    unresolved = sum(r["acquisition mode"] == "UNRESOLVED" for r in rows)
    args.output.mkdir(parents=True, exist_ok=True)
    summary = {"schema": 2, "jars": jars, "runtime_namespaces": sorted(registries),
               "runtime_versions": {mod: registry['version'] for mod, registry in sorted(registries.items())},
               "loaded_versions": next(iter(catalogs.values()), {}).get('loaded_versions', {}),
               "runtime_snapshot_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                   for p in sorted(args.runtime.glob('immersive_smithing_*.json'))} if args.runtime else {},
               "registered_item_count": sum(len(registries[m]["items"]) for m in mods if m in registries),
               "coverage_row_count": len(rows), "unresolved_rows": unresolved,
               "dynamic_route_count": sum(row['item ID'] == '* (dynamic output)' for row in rows),
               "behavioral_verification": "Not established by inventory; see profile test evidence"}
    (args.output / "inventory.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    with (args.output / "coverage.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(jars)} jars; {summary['registered_item_count']} registered items; {len(rows)} coverage rows; {unresolved} unresolved")
    print(args.output / "inventory.json")
    print(args.output / "coverage.csv")
    return 1 if args.require_complete and unresolved else 0


if __name__ == "__main__":
    raise SystemExit(main())
