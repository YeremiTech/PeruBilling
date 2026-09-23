# PeruBilling — Verification

## Static verification

```bash
./scripts/verify-project.sh --static-only
```

Validates source-tree invariants, Flyway migration ordering, public type uniqueness, Java 25 toolchain configuration, regulatory baseline synchronization and shell syntax.

## Local execution without Docker

Requirements:

- JDK 25
- PostgreSQL 16+
- Maven Wrapper included in the project

Create the local database with `perubilling` as owner and start with the `local` profile. Flyway applies V1→V16 automatically.

## Full engineering gate

Requires JDK 25 and Docker/Testcontainers:

```bash
./scripts/verify-project-auto.sh
```

This executes Maven `verify`, PostgreSQL integration tests, Flyway V1→V16, JaCoCo report/check and packaging/SBOM generation.

## Operational scripts gate

```bash
./scripts/test-operational-scripts.sh
```

Validates JDBC URL normalization, database credentials handling and artifact verification behavior used by backup/restore operations.

## Regulatory release gate

```bash
./scripts/verify-regulatory-release.sh
```

A production regulatory release additionally requires:

- official SUNAT XSD packages installed with reviewed SHA-256 pins;
- validation rules baseline pinned to the release;
- complete real SUNAT BETA evidence for every required case in `sunat-resources/beta-fixtures/matrix.json`;
- backup/restore, TLS/edge, alerting and deployment-environment drills.

Synthetic XML/CDR must not be accepted as regulatory evidence.

## Perfil local predeterminado

`spring.profiles.default=local` garantiza que una ejecución desde Spring Tool Suite sin perfil explícito cargue `application-local.properties`. Producción debe activarse explícitamente con `prod` y conserva secretos externos obligatorios.

## PDF visual and persistence regression scope

The automated suite includes regression contracts for:

- A4 at approximately 210 mm width and multipage content without clipping;
- THERMAL_80 at approximately 80 mm width and one continuous page for short and medium tickets;
- embedded PeruBilling brand logo and section icons using the canonical SVG palette;
- QR SUNAT, DigestValue and public access URL presence;
- API-Key invoice creation with an idempotency record persisted after the document parent row;
- idempotent Communication of Voiding with pessimistic document locking.

For a release candidate, download one real A4 PDF and one THERMAL_80 PDF from the API and render them to PNG before approval. The thermal artifact must not contain an almost-empty trailing page.
