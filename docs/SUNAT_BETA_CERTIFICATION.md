# Certificación operativa SUNAT BETA

## Objetivo

Convertir la validación contra SUNAT BETA en evidencia reproducible y no en una comprobación manual aislada.

## Precondiciones

1. JDK 25 o Docker disponible.
2. PostgreSQL/Docker disponibles para los tests de integración.
3. XSD oficiales instalados y fijados por SHA-256.
4. Workbook de reglas SUNAT correspondiente al baseline compilado instalado y fijado por SHA-256.
5. RUC, certificado digital y credenciales SOL habilitados para BETA.

## Verificación de código

Con JDK 25 local:

```bash
./scripts/verify-project.sh
```

Sin JDK 25 local, pero con Docker:

```bash
./scripts/verify-project-docker.sh
```

Selección automática:

```bash
./scripts/verify-project-auto.sh
```

## Ejecución BETA

Cada escenario de `sunat-resources/beta-fixtures/matrix.json` debe ejecutarse mediante el flujo real de PeruBilling. Se debe conservar exactamente el XML firmado enviado y el CDR ZIP recibido.

Registrar cada caso con:

```bash
./scripts/register-sunat-beta-evidence.sh CASE_ID XML_FIRMADO CDR_ZIP OUTCOME RESPONSE_CODE
```

Consultar avance:

```bash
./scripts/sunat-beta-report.sh
```

## Gate de release regulatoria

```bash
./scripts/verify-regulatory-release.sh
```

El gate exige simultáneamente:

- `verify-project.sh` exitoso con JDK 25;
- XSD oficiales y hashes válidos;
- reglas oficiales y hash válido;
- cobertura completa de la matriz SUNAT BETA obligatoria.

Una release no debe marcarse como certificada si falta cualquiera de esos elementos.
