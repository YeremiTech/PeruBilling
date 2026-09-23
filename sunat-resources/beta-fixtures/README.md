# Evidencia SUNAT BETA

Este directorio conserva evidencia **real** de ejecución contra SUNAT BETA. No se incluyen XML/CDR ficticios porque una release no debe declararse certificada sin respuesta auténtica de SUNAT.

## Matriz obligatoria

`matrix.json` define los escenarios CORE que deben quedar cubiertos antes de una release regulatoria. Cada caso obligatorio debe disponer de:

- `cases/<case-id>/submitted.xml`: XML UBL **firmado** que fue enviado realmente.
- `cases/<case-id>/cdr.zip`: CDR ZIP retornado por SUNAT BETA.
- `cases/<case-id>/metadata.json`: ambiente, resultado real y código de respuesta.
- `cases/<case-id>/manifest.sha256`: hashes SHA-256 de los tres archivos anteriores.

## Registrar un caso real

```bash
./scripts/register-sunat-beta-evidence.sh \
  invoice-0101-taxed-cash \
  /ruta/al/xml-firmado.xml \
  /ruta/al/R-<nombre>.zip \
  ACCEPTED \
  0
```

El script verifica que el caso exista en la matriz, que el XML esté firmado, que el CDR sea un ZIP válido y que contenga un `ApplicationResponse`.

## Ver progreso

```bash
./scripts/sunat-beta-report.sh
```

## Gate de evidencia

```bash
./scripts/verify-sunat-beta-evidence.sh
```

El gate falla mientras falte un solo caso `releaseRequired=true`, si un hash no coincide, si un XML no es válido, si el CDR no contiene `ApplicationResponse`, o si el resultado real no coincide con el resultado permitido por la matriz.

Los archivos reales pueden contener información tributaria. Deben gestionarse de acuerdo con las políticas de seguridad de la organización y no publicarse en repositorios públicos sin evaluación previa.
