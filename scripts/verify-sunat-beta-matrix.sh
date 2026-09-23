#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="${1:-$ROOT_DIR/sunat-resources/beta-fixtures}"
MATRIX="$ROOT/matrix.json"
CASES_ROOT="$ROOT/cases"

[[ -s "$MATRIX" ]] || { echo "ERROR: matriz SUNAT BETA ausente: $MATRIX" >&2; exit 30; }

python3 - "$MATRIX" "$CASES_ROOT" <<'PY'
import hashlib, io, json, pathlib, sys, zipfile, xml.etree.ElementTree as ET
matrix_path = pathlib.Path(sys.argv[1])
cases_root = pathlib.Path(sys.argv[2])

data = json.loads(matrix_path.read_text(encoding='utf-8'))
if data.get('schemaVersion') != 1:
    raise SystemExit('ERROR: schemaVersion de matriz BETA no soportada')
if data.get('environment') != 'BETA':
    raise SystemExit('ERROR: la matriz debe declarar environment=BETA')

required = [c for c in data.get('cases', []) if c.get('releaseRequired')]
if not required:
    raise SystemExit('ERROR: la matriz BETA no contiene casos obligatorios')

errors = []
complete = 0
for case in required:
    cid = case['id']
    d = cases_root / cid
    required_files = [d/'submitted.xml', d/'cdr.zip', d/'metadata.json', d/'manifest.sha256']
    if not all(p.is_file() and p.stat().st_size > 0 for p in required_files):
        errors.append(f'{cid}: evidencia incompleta')
        continue
    manifest = {}
    for line in (d/'manifest.sha256').read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            errors.append(f'{cid}: línea inválida en manifest.sha256')
            continue
        manifest[parts[1].lstrip('*')] = parts[0]
    for name in ('submitted.xml','cdr.zip','metadata.json'):
        p = d/name
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        if manifest.get(name) != digest:
            errors.append(f'{cid}: checksum inválido para {name}')

    try:
        meta = json.loads((d/'metadata.json').read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'{cid}: metadata.json inválido: {exc}')
        continue
    if meta.get('caseId') != cid:
        errors.append(f'{cid}: metadata.caseId no coincide')
    if meta.get('environment') != 'BETA':
        errors.append(f'{cid}: metadata.environment debe ser BETA')
    if meta.get('actualOutcome') not in case.get('expectedOutcomes', []):
        errors.append(f"{cid}: outcome {meta.get('actualOutcome')} fuera de {case.get('expectedOutcomes')}")
    if not str(meta.get('responseCode','')).strip():
        errors.append(f'{cid}: responseCode vacío')

    try:
        root = ET.parse(d/'submitted.xml').getroot()
        root_local = root.tag.rsplit('}',1)[-1]
        if root_local not in {'Invoice','CreditNote','DebitNote','SummaryDocuments','VoidedDocuments'}:
            errors.append(f'{cid}: raíz UBL inesperada {root_local}')
        if not any(el.tag.rsplit('}',1)[-1] == 'Signature' for el in root.iter()):
            errors.append(f'{cid}: XML enviado no contiene Signature')
    except Exception as exc:
        errors.append(f'{cid}: submitted.xml inválido: {exc}')

    try:
        with zipfile.ZipFile(d/'cdr.zip') as zf:
            if zf.testzip() is not None:
                errors.append(f'{cid}: CDR ZIP corrupto')
            names = [n for n in zf.namelist() if n.lower().endswith('.xml')]
            if len(names) != 1:
                errors.append(f'{cid}: CDR ZIP debe contener exactamente un XML')
            elif ET.parse(io.BytesIO(zf.read(names[0]))).getroot().tag.rsplit('}',1)[-1] != 'ApplicationResponse':
                errors.append(f'{cid}: XML del CDR no es ApplicationResponse')
    except Exception as exc:
        errors.append(f'{cid}: CDR inválido: {exc}')
    complete += 1

print(f'Matriz SUNAT BETA: {complete}/{len(required)} casos obligatorios con evidencia presente.')
if errors:
    for err in errors:
        print('ERROR:', err, file=sys.stderr)
    raise SystemExit(31)
PY
