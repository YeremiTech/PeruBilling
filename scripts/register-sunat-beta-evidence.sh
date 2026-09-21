#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MATRIX="$ROOT_DIR/sunat-resources/beta-fixtures/matrix.json"
EVIDENCE_ROOT="$ROOT_DIR/sunat-resources/beta-fixtures/cases"

usage() {
  cat >&2 <<'MSG'
Uso:
  register-sunat-beta-evidence.sh CASE_ID SUBMITTED_XML CDR_ZIP OUTCOME RESPONSE_CODE

OUTCOME debe ser ACCEPTED, OBSERVED o REJECTED.
El XML y el CDR deben ser artefactos reales obtenidos de SUNAT BETA.
MSG
  exit 2
}

[[ $# -eq 5 ]] || usage
case_id="$1"
xml_source="$2"
cdr_source="$3"
outcome="$4"
response_code="$5"

[[ -f "$xml_source" ]] || { echo "ERROR: XML no existe: $xml_source" >&2; exit 3; }
[[ -f "$cdr_source" ]] || { echo "ERROR: CDR ZIP no existe: $cdr_source" >&2; exit 4; }
[[ "$outcome" =~ ^(ACCEPTED|OBSERVED|REJECTED)$ ]] || { echo "ERROR: outcome inválido: $outcome" >&2; exit 5; }

python3 - "$MATRIX" "$case_id" "$outcome" <<'PY'
import json, sys
matrix_path, case_id, outcome = sys.argv[1:]
data = json.load(open(matrix_path, encoding='utf-8'))
case = next((c for c in data['cases'] if c['id'] == case_id), None)
if case is None:
    raise SystemExit(f'ERROR: caso no registrado en matrix.json: {case_id}')
if outcome not in case['expectedOutcomes']:
    raise SystemExit(f"ERROR: outcome {outcome} no permitido para {case_id}: {case['expectedOutcomes']}")
PY

unzip -tqq "$cdr_source" >/dev/null || { echo "ERROR: CDR no es un ZIP válido." >&2; exit 6; }

case_dir="$EVIDENCE_ROOT/$case_id"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
cp "$xml_source" "$tmp_dir/submitted.xml"
cp "$cdr_source" "$tmp_dir/cdr.zip"

python3 - "$tmp_dir/submitted.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
path = sys.argv[1]
root = ET.parse(path).getroot()
local = root.tag.rsplit('}', 1)[-1]
allowed = {'Invoice','CreditNote','DebitNote','SummaryDocuments','VoidedDocuments'}
if local not in allowed:
    raise SystemExit(f'ERROR: raíz UBL no esperada: {local}')
if not any(el.tag.rsplit('}',1)[-1] == 'Signature' for el in root.iter()):
    raise SystemExit('ERROR: submitted.xml no contiene ds:Signature; registre el XML firmado realmente enviado.')
PY

python3 - "$tmp_dir/cdr.zip" <<'PY'
import io, sys, zipfile, xml.etree.ElementTree as ET
path = sys.argv[1]
with zipfile.ZipFile(path) as zf:
    xml_names = [n for n in zf.namelist() if n.lower().endswith('.xml')]
    if len(xml_names) != 1:
        raise SystemExit(f'ERROR: CDR ZIP debe contener exactamente un XML; encontrados: {xml_names}')
    data = zf.read(xml_names[0])
root = ET.parse(io.BytesIO(data)).getroot()
local = root.tag.rsplit('}', 1)[-1]
if local != 'ApplicationResponse':
    raise SystemExit(f'ERROR: CDR no contiene ApplicationResponse: {local}')
PY

sent_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
python3 - "$tmp_dir/metadata.json" "$case_id" "$outcome" "$response_code" "$sent_at" <<'PY'
import json, sys
path, case_id, outcome, code, sent_at = sys.argv[1:]
json.dump({
  'caseId': case_id,
  'environment': 'BETA',
  'actualOutcome': outcome,
  'responseCode': code,
  'recordedAt': sent_at
}, open(path, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
open(path, 'a', encoding='utf-8').write('\n')
PY

(
  cd "$tmp_dir"
  sha256sum submitted.xml cdr.zip metadata.json > manifest.sha256
)

rm -rf "$case_dir"
mkdir -p "$(dirname "$case_dir")"
mv "$tmp_dir" "$case_dir"
trap - EXIT

echo "Evidencia BETA registrada: $case_id"
echo "Directorio: $case_dir"
