# Security Policy

## Supported versions

Security fixes are applied to the latest released version of PeruBilling.

## Reporting a vulnerability

Do not publish credentials, certificates, private keys, SOL passwords, production XML/CDR files or exploitable details in a public issue.

Report vulnerabilities privately to the project maintainer. Include the affected version, impact, reproduction steps and a minimal proof of concept when safe to do so.

## Secrets

PeruBilling never requires real secrets to be committed to the repository. Production secrets must be injected through environment variables or a dedicated secret manager.

## Production configuration

The official image starts with `SPRING_PROFILES_ACTIVE=prod` and `PRODUCTION_GUARD_ENABLED=true`. The guard rejects bootstrap, Swagger/OpenAPI, disabled XSD validation, non-database artifact storage, or an HTTP public base URL. The `prod` profile requires `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` without known fallback credentials. BETA/PRODUCTION CPE processing requires XSD validation.

## Untrusted SUNAT responses

CDR ZIP responses are treated as untrusted input. PeruBilling limits compressed size, decompressed XML size, entry count and compression ratio, rejects unsafe entry paths, and parses XML with external entities/DTD disabled.

## Integridad de artefactos, usuarios y backups

- XML firmado se verifica criptográficamente antes de XSD/SUNAT.
- Artefactos del filesystem se escriben atómicamente y llevan SHA-256 sidecar; `prod` exige checksum al leer.
- Health check dedicado para almacenamiento de artefactos.
- Token de portal público: 32 bytes aleatorios, almacenamiento únicamente por hash y estados publicables explícitos.
- Administración de usuarios por tenant con política fuerte de contraseña y protecciones contra auto-disable/auto-role-change.
- Maven de CI/build reproducible mediante URL + SHA-256 fijado.
- Ejemplo de rate limiting en reverse proxy para login/portal/API.
- Backups incluyen manifest SHA-256; deben cifrarse y almacenarse fuera del host.

## Pendientes de hardening externo

- Para múltiples nodos, `DatabaseArtifactStorage` ya evita discos divergentes; para alto volumen, evolucionar a object storage mediante `ArtifactStorage`.
- Aplicar rate limiting/WAF en el edge y TLS administrado por la plataforma.
- Restringir egress de webhooks con política de red/proxy para cerrar DNS rebinding entre resolución y conexión.
- Guardar secretos y certificados mediante secret manager/KMS/HSM cuando el nivel de riesgo/escala lo requiera.
- Cifrar backups en reposo fuera del host y realizar restore drills periódicos.
- Ejecutar SCA/SAST/CodeQL/SBOM en cada release y revisar findings antes de producción.

## Revocación y autorización en tiempo real

- Cada JWT se revalida contra la cuenta y el tenant persistidos antes de construir las autoridades.
- La suspensión de un tenant invalida inmediatamente los JWT ya emitidos de sus usuarios.
- La deshabilitación o bloqueo temporal de un usuario invalida sus JWT aunque todavía no hayan expirado.
- Los cambios de rol se aplican desde base de datos y no dependen del claim de rol antiguo del token.
- `sub` y `tenant_id` deben resolver a la misma cuenta persistida; un token con identidad cruzada se rechaza.
- Los workers programados pueden deshabilitarse globalmente con `SCHEDULING_ENABLED=false` durante mantenimiento o pruebas de integración.

## Actuator, CORS, auditoría y aislamiento

- Actuator health remains public for probes; all other exposed actuator endpoints require `ROLE_ADMIN` or `SCOPE_OPERATIONS_READ`.
- CORS is deny-by-default for browsers and only accepts an explicit `CORS_ALLOWED_ORIGINS` allowlist; wildcard is rejected.
- HTTP audit persistence failures emit an error log plus `perubilling.audit.write_failures`.
- API Key `lastUsedAt` updates are throttled to reduce database write amplification.
- Public document access resolves by both document id and tenant id as defense in depth.
- Production artifact storage defaults to PostgreSQL; backup/restore scripts understand both database and filesystem backends.
- Maven Enforcer requires Java 25+ and Maven 3.9+; JaCoCo report generation is attached to `verify`.


## Secretos y guard de producción

- `API_KEY_PEPPER` debe tener al menos 32 caracteres aleatorios; la aplicación falla al iniciar si es débil.
- `JWT_SECRET` continúa exigiendo Base64 con al menos 32 bytes y `MASTER_KEY` Base64 de exactamente 32 bytes.
- Con `PRODUCTION_GUARD_ENABLED=true`, el arranque exige PostgreSQL explícito, contraseña no-placeholder, checksum de artefactos, URLs públicas/CORS HTTPS y endpoints SUNAT oficiales.
- Los valores de `.env.example` son placeholders y deben reemplazarse mediante un gestor de secretos del entorno.
