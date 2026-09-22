# Contrato de errores de la API

PeruBilling devuelve errores REST con una estructura uniforme:

```json
{
  "timestamp": "2026-09-21T19:37:26Z",
  "status": 409,
  "code": "CERTIFICATE_ALREADY_EXISTS",
  "message": "El certificado ya está registrado para este emisor",
  "path": "/api/v1/issuers/{issuerId}/certificates",
  "requestId": "...",
  "validationErrors": {}
}
```

`requestId` permite correlacionar la respuesta con los logs del servidor. Los detalles internos, SQL, nombres de clases y stacktraces no se exponen al consumidor.

## Errores comunes

| HTTP | Código | Uso |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation sobre el cuerpo de la solicitud |
| 400 | `CONSTRAINT_VIOLATION` | Restricción de parámetros o método |
| 400 | `INVALID_JSON` | JSON mal formado o incompatible |
| 400 | `MISSING_PARAMETER` | Query parameter obligatorio ausente |
| 400 | `MISSING_HEADER` | Cabecera obligatoria ausente |
| 400 | `MISSING_MULTIPART_PART` | Parte multipart obligatoria ausente |
| 400 | `INVALID_PARAMETER` | UUID, enum, número u otro parámetro con formato inválido |
| 401 | `UNAUTHORIZED` | Falta autenticación o la credencial no es válida |
| 403 | `FORBIDDEN` | Usuario/API Key autenticado sin permisos suficientes |
| 404 | código de dominio | Recurso solicitado inexistente |
| 409 | código de dominio / `DATA_CONFLICT` | Duplicidad, estado incompatible o conflicto de integridad |
| 409 | `RESOURCE_BUSY` | Contención de bloqueo concurrente |
| 413 | `PAYLOAD_TOO_LARGE` | Archivo o request superior al límite configurado |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` incorrecto |
| 429 | código de dominio | Límite o bloqueo temporal por demasiados intentos |
| 500 | `TRANSACTION_ERROR` | Operación transaccional que no pudo completarse |
| 500 | `INTERNAL_ERROR` | Error no clasificado; debe investigarse por `requestId` |
| 503 | `DATABASE_UNAVAILABLE` | PostgreSQL no disponible temporalmente |

## Emisores, series y certificados

| HTTP | Código | Situación |
|---:|---|---|
| 400 | `INVALID_RUC` | RUC inválido |
| 404 | `ISSUER_NOT_FOUND` | Emisor inexistente para el tenant autenticado |
| 409 | `RUC_EXISTS` | RUC ya registrado en el tenant |
| 400 | `INVALID_SERIES` | Serie incompatible con el tipo de comprobante |
| 404 | `SERIES_NOT_FOUND` | Serie inexistente para el emisor |
| 409 | `SERIES_ALREADY_EXISTS` | Serie duplicada |
| 400 | `EMPTY_CERTIFICATE` | No se adjuntó certificado |
| 400 | `CERTIFICATE_PASSWORD_REQUIRED` | Falta contraseña PKCS#12 |
| 400 | `CERTIFICATE_PASSWORD_INVALID` | Contraseña PKCS#12 incorrecta |
| 400 | `CERTIFICATE_ALIAS_NOT_FOUND` | Alias solicitado inexistente |
| 400 | `PRIVATE_KEY_NOT_FOUND` | PKCS#12 sin clave privada utilizable |
| 400 | `CERTIFICATE_EXPIRED` | Certificado vencido |
| 400 | `CERTIFICATE_NOT_YET_VALID` | Certificado todavía no vigente |
| 400 | `CERTIFICATE_RUC_MISMATCH` | En producción, el certificado no corresponde al RUC del emisor |
| 409 | `CERTIFICATE_ALREADY_EXISTS` | Mismo certificado ya registrado para el mismo emisor |

El fingerprint de certificado es único por `(tenant_id, issuer_id, fingerprint)`. El mismo certificado de laboratorio puede utilizarse con distintos emisores de prueba, pero no se duplica dentro de un mismo emisor.

## Criterio de implementación

Los controladores no contienen `try/catch` repetitivos. Las reglas de negocio se expresan como `BusinessException`, Spring Security produce el mismo `ApiError` para `401/403`, y `GlobalExceptionHandler` clasifica errores de validación, HTTP, multipart, persistencia, locking y transacciones. `INTERNAL_ERROR` queda reservado como último fallback.
