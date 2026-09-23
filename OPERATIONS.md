# PeruBilling — Operación CORE

## Propósito

Controles mínimos para operar el núcleo de PeruBilling en un piloto real después de superar el gate BETA. Este documento no reemplaza procedimientos de seguridad, continuidad o tributación propios de la organización.

## Arranque de producción

Variables mínimas:

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `JWT_SECRET`, `MASTER_KEY`, `API_KEY_PEPPER`
- `PUBLIC_BASE_URL`
- `SUNAT_XSD_PATH`
- `ARTIFACTS_BACKEND` (producción: `database` por defecto; `filesystem` solo con volumen compartido)
- `ARTIFACTS_ROOT` únicamente cuando `ARTIFACTS_BACKEND=filesystem`
- `CORS_ALLOWED_ORIGINS` si existe frontend web en otro origen

La imagen oficial activa `SPRING_PROFILES_ACTIVE=prod` y `PRODUCTION_GUARD_ENABLED=true`. El guard rechaza bootstrap, Swagger, XSD deshabilitado, filesystem local, lectura de artefactos sin checksum, credenciales PostgreSQL de ejemplo, `PUBLIC_BASE_URL` sin HTTPS, CORS HTTP y endpoints SUNAT productivos fuera de `sunat.gob.pe`.

## Resumen Diario

`DailySummaryScheduler` agrupa documentos `PENDING_SUMMARY` por tenant, emisor y fecha. El lock transaccional dentro de `DailySummaryService` evita que dos nodos creen el mismo resumen a partir del mismo conjunto pendiente.

Variables:

```text
SUMMARY_AUTO_ENABLED=true
SUMMARY_AUTO_POLL_MS=900000
SUMMARY_AUTO_INITIAL_DELAY_MS=60000
SUMMARY_AUTO_MIN_AGE_MINUTES=15
SUMMARY_AUTO_MAX_GROUPS=100
```

El endpoint manual de Resumen Diario se conserva para operación/recuperación.

## Consulta del cliente

Durante procesamiento se genera un token de alta entropía. En PostgreSQL se almacena únicamente SHA-256. La URL se imprime en el PDF y permite consultar/descargar PDF/XML durante la vigencia configurada. Estados no publicables devuelven no disponible.

`PUBLIC_BASE_URL` debe ser HTTPS y apuntar al dominio público real.

## Integridad de artefactos

Producción usa `DatabaseArtifactStorage` por defecto: XML/PDF/CDR se almacenan en PostgreSQL con longitud y SHA-256, por lo que forman parte de `pg_dump`. `FileSystemArtifactStorage` continúa disponible para desarrollo o un volumen realmente compartido.

Para filesystem, verificación offline:

```bash
ARTIFACTS_BACKEND=filesystem ARTIFACTS_ROOT=/data/artifacts ./scripts/verify-artifacts.sh
```

Para alta escala, la siguiente evolución es una implementación `ArtifactStorage` sobre S3/MinIO sin modificar el dominio CPE.

## Backup

Backup consistente de base + artefactos:

```bash
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL='jdbc:postgresql://db:5432/perubilling' \
DATABASE_USERNAME='perubilling_app' \
DATABASE_PASSWORD='<secret>' \
ARTIFACTS_BACKEND=database \
BACKUP_DIR=/backups \
./scripts/backup.sh
```

El backup siempre genera `database.dump`, `SHA256SUMS` y metadata. `artifacts.tar.gz` solo se genera cuando el backend de artefactos es `filesystem`; con `database`, los artefactos quedan incluidos en PostgreSQL. Los backups deben guardarse cifrados fuera del host y probarse periódicamente mediante restore.

Restore destructivo:

```bash
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL='jdbc:postgresql://db:5432/perubilling' \
DATABASE_USERNAME='perubilling_app' \
DATABASE_PASSWORD='<secret>' \
ARTIFACTS_BACKEND=database \
BACKUP_PATH=/backups/perubilling-... \
RESTORE_CONFIRM=YES \
./scripts/restore.sh
```

Después del restore: ejecutar `verify-artifacts.sh`, health checks y una prueba funcional antes de habilitar tráfico. El verificador soporta tanto `filesystem` como `database`; para PostgreSQL recalcula SHA-256 directamente sobre `artifact_blob`.

## Métricas y alertas

Métricas adicionales:

- `perubilling.documents.pending_summary`
- `perubilling.documents.submission_unknown`
- `perubilling.documents.rejected_24h`
- `perubilling.certificates.expiring_30d`
- `perubilling.summary.auto.created`
- `perubilling.summary.auto.failed`
- `perubilling.summary.auto.deadline_risk`

`ops-prometheus-alerts.yml` contiene reglas de ejemplo.

## Actuator y observabilidad

`/actuator/health/**` es público para probes. Readiness incluye estado de aplicación, PostgreSQL, almacenamiento de artefactos y XSD SUNAT; liveness queda separado para evitar reinicios por dependencias externas. El resto de Actuator exige `ROLE_ADMIN` o una API Key con `OPERATIONS_READ`. No exponga Actuator por el reverse proxy salvo las rutas necesarias.

Los fallos de persistencia de auditoría HTTP incrementan `perubilling.audit.write_failures` y generan un log de error con `requestId`; configure una alerta cuando sea mayor que cero.

## CORS

Por defecto no se autoriza ningún origen browser cross-site. Si existe frontend separado, defina una allowlist exacta:

```bash
CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

El wildcard `*` está rechazado por configuración.

## Rate limiting / reverse proxy

El backend conserva controles de autenticación y lockout. El límite de tráfico debe aplicarse en el edge/reverse proxy para funcionar correctamente con múltiples instancias. `ops/nginx-perubilling.conf.example` incluye límites separados para login, portal público y API.

## Gate obligatorio antes de un piloto real

1. `./mvnw clean verify` con JDK 25 y Docker disponible para Testcontainers.
2. Flyway V1→V16 desde base vacía y prueba de upgrade de una copia representativa.
3. XSD oficiales UBL 2.1/2.0 con hashes fijados por la release.
4. Firma XMLDSig generada y verificada criptográficamente.
5. Fixtures positivos/negativos del alcance CORE.
6. Envíos reales a SUNAT BETA y conservación de CDR sanitizados como regresión.
7. Prueba de concurrencia/idempotencia/Resumen Diario.
8. Simulación de timeout y reconciliación SUNAT.
9. Restore real desde backup.
10. Revisión de secretos, TLS, rate limits, alertas y almacenamiento persistente.
11. Confirmar que JaCoCo supera el piso configurado y revisar manualmente cualquier caída relevante de cobertura.
12. Confirmar `17/17` evidencias SUNAT BETA antes de habilitar `PRODUCTION` para un RUC real.

No habilitar `PRODUCTION` únicamente porque la aplicación arranque.

## Control global de scheduling

PeruBilling habilita sus workers programados por defecto. Para ventanas de mantenimiento, migraciones controladas o suites de integración se pueden deshabilitar sin modificar código:

```bash
SCHEDULING_ENABLED=false
```

Esto desactiva el procesamiento automático de `@Scheduled` mientras mantiene los servicios disponibles para invocación explícita. En producción normal debe permanecer en `true`.

Antes de cada release ejecutar con JDK 25 y Docker disponible:

```bash
./scripts/verify-project.sh
```
