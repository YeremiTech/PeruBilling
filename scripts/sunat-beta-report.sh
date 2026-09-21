#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$ROOT_DIR/sunat-resources/beta-fixtures/matrix.json" "$ROOT_DIR/sunat-resources/beta-fixtures/cases" <<'PY'
import json, pathlib, sys
matrix = json.load(open(sys.argv[1], encoding='utf-8'))
root = pathlib.Path(sys.argv[2])
required = [c for c in matrix['cases'] if c.get('releaseRequired')]
rows = []
for c in required:
    d = root / c['id']
    ok = all((d/n).is_file() and (d/n).stat().st_size > 0 for n in ('submitted.xml','cdr.zip','metadata.json','manifest.sha256'))
    rows.append((c['id'], 'COMPLETO' if ok else 'PENDIENTE'))
for cid, status in rows:
    print(f'{status:10} {cid}')
complete = sum(status == 'COMPLETO' for _, status in rows)
percent = (100 * complete / len(rows)) if rows else 0
print(f'\nProgreso evidencia BETA: {complete}/{len(rows)} ({percent:.1f}%)')
PY
