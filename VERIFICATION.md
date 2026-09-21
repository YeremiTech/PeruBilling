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

Create the local database with `perubilling` as owner and start with the `local` profile. Flyway applies V1→V14 automatically.

## Full engineering gate

Requires JDK 25 and Docker/Testcontainers:

```bash
./scripts/verify-project-auto.sh
```

This executes Maven `verify`, PostgreSQL integration tests, Flyway V1→V14, JaCoCo report/check and packaging/SBOM generation.

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
