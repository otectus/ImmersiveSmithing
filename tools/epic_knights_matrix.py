#!/usr/bin/env python3
"""Run isolated, pinned Epic Knights GameTest profiles serially; preserve commands and nonzero test counts.

Example: python3 tools/epic_knights_matrix.py --profiles base,armor,materials --offline
Runtime catalogs are evidence, not expectations: refresh reviewed fixtures separately with coverage_inventory.py.
"""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT.parent / '.mcmod-tools/gradlew-quiet.sh'
PROFILES = {'plain': [], 'compat': ['-PcompatMods'], 'base': ['-PepicKnights=base']}
for key in ['addon', 'antique', 'slavic', 'darkages', 'japanese', 'oresalloys', 'ingots', 'iceandfire',
            'thirtyyearswar', 'fletchery', 'artillery', 'boomsticks', 'boomsticks-release', 'sevenkingdoms',
            'bettercombat', 'rework', 'overgeared', 'terrafirma', 'terrafirmagreg']:
    PROFILES[key] = ['-PepicKnights=' + key]
PROFILES.update({
    'armor': ['-PepicKnights=addon,slavic,antique,darkages,japanese'],
    'materials': ['-PepicKnights=oresalloys,ingots'],
    'materials-provider': ['-PepicKnights=oresalloys,ingots,iceandfire'],
    'ranged': ['-PepicKnights=thirtyyearswar,fletchery,artillery,boomsticks-release'],
    'visors': ['-PepicKnights=openhelms,swappable'],
    'visors-addon': ['-PepicKnights=addon,openhelms,swappable'],
    'epicfight': ['-PepicKnights=base', '-PepicFight'],
    'combined': ['-PepicKnights=all,thirtyyearswar,fletchery,japanese,ingots'],
})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', default=','.join(PROFILES))
    parser.add_argument('--output', type=Path, default=ROOT / 'build/compat-validation/epic-knights-1.3.0')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--init-script', type=Path)
    parser.add_argument('--keep-going', action='store_true')
    args = parser.parse_args()
    selected = args.profiles.split(',')
    unknown = set(selected) - PROFILES.keys()
    if unknown: parser.error('Unknown profiles: ' + ', '.join(sorted(unknown)))
    args.output.mkdir(parents=True, exist_ok=True)
    ledger = args.output / 'commands.json'
    records = json.loads(ledger.read_text()) if ledger.exists() else []
    failed = False
    for profile in selected:
        command = [str(HELPER), str(ROOT), 'runGameTestServer']
        if args.offline: command += ['--offline']
        if args.init_script: command += ['--init-script', str(args.init_script.resolve())]
        command += PROFILES[profile] + ['-PregistryDump=all', '-PgameTestProfile=epic-final-' + profile]
        start = time.time()
        print('RUN ' + profile + ': ' + shlex.join(command), flush=True)
        result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        output = args.output / f'{profile}-{int(start)}.txt'
        output.write_text(result.stdout)
        matches = re.findall(r'^log: (.+)$', result.stdout, re.M)
        log = Path(matches[-1]) if matches else None
        full = log.read_text(errors='replace') if log and log.is_file() else ''
        count = re.search(r'(\d+) GAME TESTS COMPLETE', full)
        tests = int(count[1]) if count else 0
        failures = re.findall(r'\[minecraft/LogTestReporter\]: (.+ failed! .*)', full)
        passed = result.returncode == 0 and tests > 0 and 'All ' + str(tests) + ' required tests passed' in full
        record = {'profile': profile, 'command': command, 'exit_code': result.returncode, 'pass': passed,
                  'tests': tests, 'failures': failures, 'seconds': round(time.time() - start, 1),
                  'log': str(log) if log else None, 'wrapper_output': str(output),
                  'runtime_catalogs': str(ROOT / 'run-gametest/profiles' / ('epic-final-' + profile) / 'logs')}
        records.append(record)
        ledger.write_text(json.dumps(records, indent=2) + '\n')
        print(('PASS' if passed else 'FAIL') + f' {profile}: {tests} tests, log {log}', flush=True)
        if not passed:
            failed = True
            if not args.keep_going: break
    raise SystemExit(1 if failed else 0)


if __name__ == '__main__':
    main()
