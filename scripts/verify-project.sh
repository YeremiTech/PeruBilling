#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

STATIC_ONLY=false
if [[ "${1:-}" == "--static-only" ]]; then
  STATIC_ONLY=true
elif [[ $# -gt 0 ]]; then
  echo "Uso: $0 [--static-only]" >&2
  exit 2
fi

python3 - <<'PY'
from pathlib import Path
import re
import sys

root = Path('.')
errors = []

# Flyway debe permanecer contiguo para que una fase nunca salte una migración esperada.
migrations = list((root / 'src/main/resources/db/migration').glob('V*__*.sql'))
versions = []
for path in migrations:
    match = re.match(r'V(\d+)__', path.name)
    if not match:
        errors.append(f'Migración con nombre inválido: {path}')
        continue
    versions.append(int(match.group(1)))
versions.sort()
if versions and versions != list(range(1, max(versions) + 1)):
    errors.append(f'Versiones Flyway no contiguas: {versions}')

# Evita merges accidentales o tipos públicos duplicados antes de compilar.
public_types = {}
pattern = re.compile(r'public\s+(?:final\s+|abstract\s+|sealed\s+)?(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)')
for path in (root / 'src').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    if any(marker in text for marker in ('<<<<<<<', '=======', '>>>>>>>')):
        errors.append(f'Marcador de conflicto Git: {path}')
    package = re.search(r'^package\s+([\w.]+);', text, re.MULTILINE)
    for name in pattern.findall(text):
        fqcn = f'{package.group(1) if package else ""}.{name}'
        previous = public_types.setdefault(fqcn, path)
        if previous != path:
            errors.append(f'Tipo público duplicado {fqcn}: {previous} y {path}')

# Balance léxico básico de llaves Java, ignorando comentarios, strings, chars y text blocks.
def java_braces_balanced(text):
    i = 0
    depth = 0
    state = 'normal'
    while i < len(text):
        if state == 'normal':
            if text.startswith('//', i):
                state = 'line_comment'; i += 2; continue
            if text.startswith('/*', i):
                state = 'block_comment'; i += 2; continue
            if text.startswith('"""', i):
                state = 'text_block'; i += 3; continue
            ch = text[i]
            if ch == '"':
                state = 'string'; i += 1; continue
            if ch == "'":
                state = 'char'; i += 1; continue
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth < 0:
                    return False
            i += 1
            continue
        if state == 'line_comment':
            if text[i] == '\n':
                state = 'normal'
            i += 1; continue
        if state == 'block_comment':
            if text.startswith('*/', i):
                state = 'normal'; i += 2
            else:
                i += 1
            continue
        if state == 'text_block':
            if text.startswith('"""', i):
                state = 'normal'; i += 3
            else:
                i += 1
            continue
        if state in ('string', 'char'):
            quote = '"' if state == 'string' else "'"
            if text[i] == '\\':
                i += 2; continue
            if text[i] == quote:
                state = 'normal'
            i += 1; continue
    return depth == 0 and state not in ('block_comment', 'string', 'char', 'text_block')

for path in (root / 'src').rglob('*.java'):
    if not java_braces_balanced(path.read_text(encoding='utf-8')):
        errors.append(f'Estructura Java desbalanceada: {path}')

# Evita dependencias cíclicas entre módulos de primer nivel de pe.com.perubilling.
module_root = root / 'src/main/java/pe/com/perubilling'
if module_root.is_dir():
    module_files = list(module_root.rglob('*.java'))
    modules = {path.relative_to(module_root).parts[0] for path in module_files}
    graph = {module: set() for module in modules}
    import_pattern = re.compile(r'import\s+pe\.com\.perubilling\.([A-Za-z0-9_]+)\.')
    for path in module_files:
        source_module = path.relative_to(module_root).parts[0]
        for target_module in import_pattern.findall(path.read_text(encoding='utf-8')):
            if target_module in modules and target_module != source_module:
                graph[source_module].add(target_module)

    state = {}
    stack = []
    cycle_messages = set()
    def visit(module):
        state[module] = 1
        stack.append(module)
        for target in graph[module]:
            target_state = state.get(target, 0)
            if target_state == 0:
                visit(target)
            elif target_state == 1:
                start = stack.index(target)
                cycle = stack[start:] + [target]
                # Canonicaliza para no reportar rotaciones repetidas.
                body = cycle[:-1]
                rotations = [tuple(body[i:] + body[:i]) for i in range(len(body))]
                canonical = min(rotations)
                cycle_messages.add(' -> '.join(canonical + (canonical[0],)))
        stack.pop()
        state[module] = 2

    for module in sorted(modules):
        if state.get(module, 0) == 0:
            visit(module)
    for cycle in sorted(cycle_messages):
        errors.append(f'Ciclo entre módulos: {cycle}')

# Toolchain y baseline regulatorio deben evolucionar como una sola unidad.
pom = (root / 'pom.xml').read_text(encoding='utf-8')
java_match = re.search(r'<java\.version>(\d+)</java\.version>', pom)
if not java_match or int(java_match.group(1)) != 25:
    errors.append('pom.xml debe fijar java.version=25')

dockerfile = (root / 'Dockerfile').read_text(encoding='utf-8')
if 'temurin-25' not in dockerfile:
    errors.append('Dockerfile debe compilar/ejecutar con Java 25')

if 'ENV SPRING_PROFILES_ACTIVE=prod' not in dockerfile or 'PRODUCTION_GUARD_ENABLED=true' not in dockerfile:
    errors.append('La imagen productiva debe activar prod y ProductionConfigurationGuard por defecto')

prod_properties = (root / 'src/main/resources/application-prod.properties').read_text(encoding='utf-8')
if 'app.production.guard.enabled=${PRODUCTION_GUARD_ENABLED:true}' not in prod_properties:
    errors.append('application-prod.properties debe habilitar el guard de producción')
if 'app.artifacts.backend=${ARTIFACTS_BACKEND:database}' not in prod_properties:
    errors.append('Producción debe usar un backend de artefactos compartido por defecto')
if 'app.artifacts.require-checksum=true' not in prod_properties:
    errors.append('Producción debe exigir checksum al leer artefactos')
if 'management.endpoint.health.group.readiness.show-details=never' not in prod_properties:
    errors.append('Producción debe ocultar detalles de readiness')

security_source = (root / 'src/main/java/pe/com/perubilling/security/SecurityConfig.java').read_text(encoding='utf-8')
if 'EndpointRequest.toAnyEndpoint()' not in security_source:
    errors.append('Actuator debe tener una SecurityFilterChain dedicada')
if 'SCOPE_OPERATIONS_READ' not in security_source:
    errors.append('Actuator debe exigir ROLE_ADMIN o SCOPE_OPERATIONS_READ fuera de health')
if security_source.count('setEnabled(false)') < 1 or 'FilterRegistrationBean' not in security_source:
    errors.append('Filtros de seguridad personalizados deben deshabilitar su registro servlet automático')
if 'origins.contains("*")' not in security_source:
    errors.append('CORS debe rechazar wildcard explícitamente')

guard_source = (root / 'src/main/java/pe/com/perubilling/shared/config/ProductionConfigurationGuard.java').read_text(encoding='utf-8')
for required_guard_marker in (
        'app.artifacts.require-checksum',
        'jdbc:postgresql://',
        'secureCorsOrigins',
        'isOfficialSunatHttpsUrl'):
    if required_guard_marker not in guard_source:
        errors.append(f'ProductionConfigurationGuard incompleto: falta {required_guard_marker}')

api_key_service = (root / 'src/main/java/pe/com/perubilling/access/application/ApiKeyService.java').read_text(encoding='utf-8')
api_key_filter = (root / 'src/main/java/pe/com/perubilling/access/security/ApiKeyAuthenticationFilter.java').read_text(encoding='utf-8')
if '32 caracteres aleatorios' not in api_key_service or '32 caracteres aleatorios' not in api_key_filter:
    errors.append('API_KEY_PEPPER debe exigir al menos 32 caracteres en creación y autenticación')

delivery_source = (root / 'src/main/java/pe/com/perubilling/delivery/application/DocumentDeliveryService.java').read_text(encoding='utf-8')
if 'findByIdAndTenantId(token.getDocumentId(), token.getTenantId())' not in delivery_source:
    errors.append('Portal público debe resolver documentos por id + tenant')

api_key_filter = (root / 'src/main/java/pe/com/perubilling/access/security/ApiKeyAuthenticationFilter.java').read_text(encoding='utf-8')
if 'touchLastUsedAt' not in api_key_filter or 'repository.save(entity)' in api_key_filter:
    errors.append('API Key lastUsedAt debe actualizarse mediante touch throttled, no save por request')

audit_filter = (root / 'src/main/java/pe/com/perubilling/audit/application/AuditRequestFilter.java').read_text(encoding='utf-8')
if 'perubilling.audit.write_failures' not in audit_filter or 'log.error' not in audit_filter:
    errors.append('Fallos de auditoría deben producir métrica y log de error')

if '<artifactId>maven-enforcer-plugin</artifactId>' not in pom:
    errors.append('pom.xml debe incluir Maven Enforcer')
if '<artifactId>jacoco-maven-plugin</artifactId>' not in pom:
    errors.append('pom.xml debe generar reporte JaCoCo')
if '<id>check</id>' not in pom or '<goal>check</goal>' not in pom:
    errors.append('JaCoCo debe bloquear regresiones mediante goal check en verify')
if '<jacoco.line.minimum>' not in pom or '<jacoco.branch.minimum>' not in pom:
    errors.append('pom.xml debe declarar pisos de cobertura JaCoCo')

# Higiene fuente mínima independiente de formatter externo.
for path in (root / 'src').rglob('*.java'):
    for line_number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if '\t' in line:
            errors.append(f'Tab no permitido en Java: {path}:{line_number}')
        if line.rstrip() != line:
            errors.append(f'Whitespace final en Java: {path}:{line_number}')

baseline_source = root / 'src/main/java/pe/com/perubilling/shared/sunat/SunatRulesBaseline.java'
baseline_text = baseline_source.read_text(encoding='utf-8') if baseline_source.is_file() else ''
baseline_match = re.search(r'VERSION\s*=\s*\"([^\"]+)\"', baseline_text)
try:
    import json
    beta_matrix = json.loads((root / 'sunat-resources/beta-fixtures/matrix.json').read_text(encoding='utf-8'))
except Exception as exc:
    errors.append(f'Matriz SUNAT BETA inválida: {exc}')
    beta_matrix = {}
if baseline_match and beta_matrix.get('rulesBaseline') != baseline_match.group(1):
    errors.append(
        f"Baseline SUNAT desalineado: código={baseline_match.group(1)} matriz={beta_matrix.get('rulesBaseline')}"
    )
if beta_matrix:
    cases = beta_matrix.get('cases', [])
    ids = [case.get('id') for case in cases]
    if not cases or any(not cid for cid in ids):
        errors.append('matrix.json debe contener casos con id')
    if len(ids) != len(set(ids)):
        errors.append('matrix.json contiene case ids duplicados')
    if not any(case.get('releaseRequired') for case in cases):
        errors.append('matrix.json no contiene casos obligatorios de release')

# Los scripts operativos deben aceptar la misma DATABASE_URL JDBC que usa Spring Boot.
for script_name in ('backup.sh', 'restore.sh', 'verify-artifacts.sh'):
    script_text = (root / 'scripts' / script_name).read_text(encoding='utf-8')
    if '${DATABASE_URL#jdbc:}' not in script_text:
        errors.append(f'{script_name} debe normalizar DATABASE_URL JDBC para herramientas PostgreSQL')

if errors:
    print('\n'.join(f'ERROR: {error}' for error in errors), file=sys.stderr)
    sys.exit(1)
print(f'Static gate OK: {len(migrations)} migraciones Flyway; {len(public_types)} tipos públicos únicos; toolchain Java 25; baseline regulatorio sincronizado.')
PY

for script in scripts/*.sh; do
  bash -n "$script"
done

echo "Shell gate OK."
./scripts/test-operational-scripts.sh

if [[ "$STATIC_ONLY" == "true" ]]; then
  echo "Static-only gate complete."
  exit 0
fi

java_major="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | awk -F. '{print ($1 == "1" ? $2 : $1)}')"
if [[ -z "$java_major" || "$java_major" -lt 25 ]]; then
  echo "ERROR: PeruBilling requiere JDK 25 o superior; detectado: ${java_major:-desconocido}" >&2
  exit 1
fi

echo "Ejecutando Maven verify con JDK ${java_major}..."
./mvnw -B -ntp verify
