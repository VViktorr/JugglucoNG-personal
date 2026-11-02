#!/usr/bin/env python3
"""Verify the requested 1.2.8 NFC, shared GATT and other sensor code freeze.

Reads recorded SHA-256 values; does not require git history or an Android SDK.
"""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'docs/main5-preserved-files.json').read_text())
expected = dict(manifest['files'])
for prefix, files in manifest['directories'].items():
    actual = {p.relative_to(root).as_posix() for p in (root / prefix).rglob('*') if p.is_file()}
    assert actual == set(files), f'Protected sensor tree changed: {prefix}'
    expected.update(files)
for name, digest in expected.items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest, name
# Shared LibreView class delegates Libre3 to the preserved upstream method bodies.
import re
source = (root / 'Common/src/mobile/java/tk/glucodata/Libreview.java').read_text()
for name, digest in manifest['libreview3_methods'].items():
    match = re.search(r'(?m)^[ \t]*(?:(?:public|private|protected|static|final)\s+)*\w+\s+' + name + r'\s*\(', source)
    assert match, name
    start = match.start()
    brace = source.index('{', match.end())
    end, depth = brace + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    body = source[start:end]
    for identifier in manifest['libreview3_methods']:
        body = body.replace(identifier, identifier.removesuffix('Upstream128'))
    normalized = re.sub(r'\s+', ' ', body).strip()
    assert hashlib.sha256(normalized.encode()).hexdigest() == digest, name
# Explicitly authorized Libre 2 NFC isolation: separately pinned, not claimed as upstream.
for name, digest in manifest.get('nfc_isolation_files', {}).items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest, name
print(f'PASS: {len(expected)} preserved sensor/NFC/native files match upstream 1.2.8')
