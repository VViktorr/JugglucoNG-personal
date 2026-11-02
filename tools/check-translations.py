#!/usr/bin/env python3
"""Audit merged Android resource locales; fail on structural/format errors.

Missing translations are reported and use Android's default-language fallback.
Run: python tools/check-translations.py [--root path/to/repo]
"""
import argparse
from collections import defaultdict
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

# Consume %% as a whole before looking for arguments. Percentages in prose
# such as '50% of' are not treated as format placeholders.
FORMAT = re.compile(r'%%|%(?:([1-9][0-9]*)\$)?[-#+0,(<]*[0-9]*(?:\.[0-9]+)?([tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaAn])')
TEXT_TAGS = {'string', 'plurals', 'string-array'}
LOCALE = re.compile(r'values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+.+)$')


def signature(node):
    result = {}
    sequence = previous = 0
    for match in FORMAT.finditer(''.join(node.itertext())):
        conversion = match.group(2)
        if conversion is None or conversion == 'n':
            continue
        if match.group(1):
            index = int(match.group(1))
        elif '<' in match.group():
            index = previous
        else:
            sequence += 1
            index = sequence
        previous = index
        result[index] = conversion.lower()
    return result


def variants(default, localized):
    if default.tag == 'string':
        return [(default, localized)]
    if default.tag == 'plurals':
        choices = {x.get('quantity'): x for x in default}
        return [(choices.get(x.get('quantity'), choices.get('other')), x) for x in localized]
    return list(zip(default, localized))


def audit(root, required_locales=()):
    groups = defaultdict(dict)
    errors = []
    file_count = 0
    for flavor in ('main', 'mobile', 'wear'):
        for path in sorted((root / 'Common/src' / flavor / 'res').glob('values*/*.xml')):
            if path.name.startswith('.'):
                continue
            file_count += 1
            try:
                resource = ET.parse(path).getroot()
            except ET.ParseError as exc:
                errors.append(f'{path}: malformed XML: {exc}')
                continue
            group = groups[flavor, path.parent.name]
            for node in resource:
                if node.tag not in TEXT_TAGS:
                    continue
                key = node.get('name')
                if key in group:
                    errors.append(f'{path}: duplicate resource {key}')
                group[key] = node
    languages = sorted({locale for _, locale in groups if LOCALE.fullmatch(locale)})
    missing_counts = {}
    for flavor in ('mobile', 'wear'):
        defaults = {**groups['main', 'values'], **groups[flavor, 'values']}
        for locale in languages:
            localized = {**groups['main', locale], **groups[flavor, locale]}
            missing = [key for key, node in defaults.items()
                       if node.get('translatable') != 'false' and key not in localized
                       and ''.join(node.itertext()).strip()]
            missing_counts[flavor, locale] = len(missing)
            print(f'{flavor}/{locale}: {len(missing)} missing translations')
            if locale in required_locales and missing:
                errors.append(f'{flavor}/{locale}: required translations missing: {missing}')
            for key, node in localized.items():
                default = defaults.get(key)
                if default is None:
                    errors.append(f'{flavor}/{locale}: orphan translation {key}')
                    continue
                if default.tag != node.tag:
                    errors.append(f'{flavor}/{locale}: resource type mismatch {key}')
                    continue
                if default.get('formatted') == 'false':
                    continue
                for source, translated in variants(default, node):
                    if source is None:
                        errors.append(f'{flavor}/{locale}: missing default plural other {key}')
                        continue
                    if signature(source) != signature(translated):
                        errors.append(f'{flavor}/{locale}: format mismatch {key}: '
                                      f'{signature(source)} vs {signature(translated)}')
    for error in errors:
        print('ERROR: ' + error, file=sys.stderr)
    print(f'Checked {file_count} XML files; {len(errors)} structural/format errors.')
    return 1 if errors else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--require-locale', action='append', default=[],
                        help='Fail for missing translations, e.g. pl (repeatable)')
    args = parser.parse_args()
    sys.exit(audit(args.root, tuple('values-' + x for x in args.require_locale)))
