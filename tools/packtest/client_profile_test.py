#!/usr/bin/env python3
"""Run the existing packaged client acceptance agent in a minimal, isolated installation.

The Ultima harness supplies the launcher, real-window/framebuffer evidence, timeout and result validation.
This adapter supplies an empty source instance plus explicitly selected original mod jars. It never edits a live instance.
Build the current production jar and packTestJar first. --expect epicknights enables its additional screenshots/checks.
"""
import argparse
from pathlib import Path
import re
import sys
import ultima_pack_test as harness


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True)
    parser.add_argument('--expect', default='')
    parser.add_argument('--timeout', type=int, default=600)
    parser.add_argument('--extra-mods', type=Path, nargs='*', default=[])
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', args.label): parser.error('label must be a plain directory name')
    source = harness.ROOT / 'build/packtest-inputs' / args.label
    (source / 'mods').mkdir(parents=True, exist_ok=False)
    (source / 'options.txt').write_text('fullscreen:false\npauseOnLostFocus:false\nonboardAccessibility:false\n'
                                      'tutorialStep:none\nguiScale:2\nrenderDistance:6\nsoundCategory_master:0.0\n')
    harness.PACK = source
    harness.HEAP = '4096M'
    sys.argv = ['ultima_pack_test.py', '--label', args.label, '--expect', args.expect, '--timeout', str(args.timeout),
                '--extra-mods', *(str(path.resolve()) for path in args.extra_mods)]
    harness.main()


if __name__ == '__main__':
    main()
