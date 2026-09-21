# PeruBilling

API modular de facturación electrónica para Perú orientada a integración con POS, ERP, ecommerce y sistemas de ventas. Versión de trabajo: `0.8.0-SNAPSHOT`.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9.16-C71A36?style=flat&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat&logo=flyway&logoColor=white)](https://documentation.red-gate.com/flyway)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat&logo=docker&logoColor=white)](https://www.docker.com/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger-85EA2D?style=flat&logo=swagger&logoColor=black)](https://www.openapis.org/)
[![SUNAT](https://img.shields.io/badge/SUNAT-UBL%202.1-005DAA?style=flat)](https://cpe.sunat.gob.pe/)
[![CI](https://github.com/YeremiTech/PeruBilling/actions/workflows/ci.yml/badge.svg)](https://github.com/YeremiTech/PeruBilling/actions/workflows/ci.yml)
[![CodeQL](https://github.com/YeremiTech/PeruBilling/actions/workflows/codeql.yml/badge.svg)](https://github.com/YeremiTech/PeruBilling/actions/workflows/codeql.yml)
![Status](https://img.shields.io/badge/status-preproduction-orange?style=flat)

El proyecto está planteado como un **monolito modular**: mantiene un despliegue simple, pero separa seguridad, emisores, facturación, CPE, SUNAT, Resumen Diario, webhooks y auditoría para poder evolucionar sin convertir el dominio tributario en un CRUD acoplado.

## Alcance implementado

- Java 25 + Spring Boot 4.1.1 + Maven 3.9.16 fijado mediante `./mvnw`.
- PostgreSQL + Flyway.
- Multi-tenant y multiemisor.
- Login administrativo con JWT HS256.
- API Keys para integraciones máquina-a-máquina con scopes.
- Contraseñas SOL cifradas mediante AES-256-GCM.
- Certificados PKCS#12 cifrados en reposo y validación de vigencia.
- Factura `01`, boleta `03`, nota de crédito `07` y nota de débito `08`.
- Cálculo CORE con `BigDecimal`: IGV, IVAP, exonerado, inafecto, operaciones gratuitas e ICBPER calibrado por fecha de emisión, además de factura de exportación `0102` con afectación `40`. Detracciones/SPOT, ISC, anticipos, percepciones, retenciones, OSE y otros perfiles no declarados se rechazan explícitamente hasta contar con implementación end-to-end.
- Series y correlativos con bloqueo pesimista.
- Idempotencia mediante `Idempotency-Key` con serialización transaccional en PostgreSQL para evitar carreras concurrentes.
- Generación UBL 2.1 para factura/boleta/notas.
- Firma XMLDSig RSA-SHA256 y verificación criptográfica local antes de cualquier envío externo.
- Validación XML segura y XSD configurable.
- Integración SOAP `sendBill` para documentos de envío directo.
- Procesamiento de CDR y reconciliación de envíos ambiguos en Producción mediante consulta de estado/CDR antes de reenviar.
- Resumen Diario UBL 2.0 para boletas y notas vinculadas a boletas, con `sendSummary` + ticket + `getStatus` y creación automática multi-tenant protegida por advisory lock.
- Procesamiento asíncrono basado en PostgreSQL y `FOR UPDATE SKIP LOCKED`.
- Recuperación de trabajos bloqueados y reintentos con backoff.
- PDF multipágina de representación básica, sin truncado silencioso de ítems.
- Descarga de XML, PDF y CDR; producción usa almacenamiento de artefactos en PostgreSQL con longitud + SHA-256 y el backend filesystem conserva escritura atómica con checksum para desarrollo/volúmenes compartidos.
- Webhooks HTTPS firmados con HMAC-SHA256, reintentos y recuperación de entregas `SENDING` abandonadas.
- Auditoría de operaciones de escritura con tabla append-only protegida por trigger.
- Portal confidencial del adquirente con token de alta entropía almacenado solo como hash y URL impresa en el PDF.
- Administración mínima de usuarios del tenant: alta, roles, habilitación y cambio de contraseña.
- Scripts de backup/restore compatibles con la `DATABASE_URL` JDBC de Spring, verificación de integridad de artefactos PostgreSQL/filesystem, health checks y métricas/alertas operativas.
- Actuator, métricas, request IDs y logging estructurable.
- Swagger/OpenAPI en desarrollo/BETA; deshabilitado por defecto en el perfil `prod`.
- Dockerfile y Docker Compose local.

## Arquitectura

```text
POS / ERP / Ecommerce
        |
        | HTTPS + API Key
        v
+-----------------------------+
|        PeruBilling          |
|-----------------------------|
| identity / access-control   |
| tenant / issuer             |
| billing / taxation          |
| cpe / ubl / signature       |
| summary                     |
| sunat                       |
| webhook / audit             |
+-------------+---------------+
              |
       +------+-------+
       |              |
   PostgreSQL     Artifactos
                      |
                XML / PDF / CDR
              |
              v
       SUNAT Beta / Producción
```

La capa de ventas externa no necesita conocer SOAP, XMLDSig ni CDR. Solo consume REST/JSON.

## Estructura de paquetes

```text
pe.com.perubilling
├── access
├── audit
├── billing
│   ├── api
│   ├── application
│   ├── domain
│   ├── infrastructure
│   └── tax
├── catalog
├── cpe
│   ├── domain
│   ├── pdf
│   ├── signature
│   ├── ubl
│   └── validation
├── delivery
├── identity
├── issuer
├── outbox
├── processing
├── security
├── shared
├── summary
├── sunat
├── tenant
├── voiding
└── webhook
```

## Requisitos

- JDK 25.
- Maven 3.9+.
- PostgreSQL 16+ recomendado.
- Docker opcional.
- Para BETA/PRODUCCIÓN: RUC emisor, credenciales SOL y certificado digital PKCS#12 vigente según corresponda.

## Inicio rápido con Docker

```bash
docker compose -f compose.local.yaml up --build
```

API:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Usuario local inicial:

```text
admin@local.test
ChangeMe123!
```

Estas credenciales existen únicamente para `local`. No deben usarse en producción.

El perfil `local` incluye secretos de desarrollo fijos y válidos para arrancar en una PC local sin depender de variables heredadas de Windows/STS. `local` es además el perfil predeterminado cuando no se especifica ninguno (`spring.profiles.default=local`), por lo que en Spring Tool Suite puedes ejecutar `PeruBillingApplication` directamente. El perfil `prod` no utiliza estos valores y debe activarse explícitamente; exige `JWT_SECRET`, `MASTER_KEY` y `API_KEY_PEPPER` reales mediante variables externas.

## Inicio local sin Docker

Crear la base:

```sql
CREATE USER perubilling WITH PASSWORD 'perubilling';
CREATE DATABASE perubilling
    WITH OWNER = perubilling
    ENCODING = 'UTF8';
```

Ejecutar:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway crea el esquema automáticamente.

## Autenticación administrativa

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "admin@local.test",
  "password": "ChangeMe123!"
}
```

Usar el token devuelto:

```http
Authorization: Bearer eyJ...
```

## API Keys

Un administrador puede crear una clave en:

```http
POST /api/v1/api-keys
```

La clave completa se muestra una sola vez. PeruBilling almacena únicamente su hash.

En sistemas externos:

```http
X-API-Key: pb_live_xxxxxxxxx
```

Scopes disponibles:

```text
DOCUMENT_READ
DOCUMENT_WRITE
ISSUER_READ
WEBHOOK_WRITE
OPERATIONS_READ
```

## Flujo mínimo de configuración

1. Iniciar sesión como administrador.
2. Crear un emisor.
3. Crear series para factura, boleta y notas.
4. En `LOCAL`, emitir sin certificado ni SUNAT.
5. En `BETA` o `PRODUCTION`, configurar credenciales SOL.
6. Cargar certificado PKCS#12 vigente.
7. Instalar XSD oficiales UBL 2.1 y 2.0 con checksums fijados usando `scripts/download-sunat-xsd.sh`.
8. Ejecutar `mvn clean verify` con JDK 25 + Docker/Testcontainers.
9. Ejecutar casos oficiales en BETA antes de producción.

## Crear emisor

```http
POST /api/v1/issuers
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "ruc": "20123456786",
  "businessName": "EMPRESA DEMO SAC",
  "tradeName": "DEMO",
  "address": "Av. Ejemplo 123",
  "ubigeo": "150101",
  "department": "LIMA",
  "province": "LIMA",
  "district": "LIMA",
  "environment": "LOCAL"
}
```

`20123456786` es un RUC sintético con dígito verificador válido para pruebas locales del código. No representa autorización para emitir documentos reales ni debe enviarse a SUNAT como si fuera un contribuyente propio.

## Crear serie

```http
POST /api/v1/issuers/{issuerId}/series
```

```json
{
  "documentType": "INVOICE",
  "series": "F001",
  "startAt": 1
}
```

Para boleta:

```json
{
  "documentType": "RECEIPT",
  "series": "B001",
  "startAt": 1
}
```

Las notas usan su propio registro de serie por tipo de documento; una nota que modifica factura debe usar `F...` y una que modifica boleta debe usar `B...`.

## Emitir factura

```http
POST /api/v1/invoices
X-API-Key: pb_live_xxx
Idempotency-Key: sale-98275
Content-Type: application/json
```

```json
{
  "issuerId": "11111111-1111-1111-1111-111111111111",
  "externalId": "SALE-98275",
  "series": "F001",
  "currency": "PEN",
  "customer": {
    "documentType": "6",
    "documentNumber": "20111111112",
    "name": "CLIENTE SAC",
    "address": "Lima"
  },
  "payment": {
    "method": "CONTADO"
  },
  "items": [
    {
      "sku": "PROD-001",
      "description": "Producto de prueba",
      "unitCode": "NIU",
      "quantity": 2,
      "unitValue": 100.00,
      "taxAffectationCode": "10",
      "igvRate": 18.00,
      "icbperPerUnit": 0,
      "adjustments": [
        {
          "charge": false,
          "reasonCode": "00",
          "factor": 0.05
        }
      ]
    }
  ]
}
```

Respuesta inicial:

```text
QUEUED
```

El ajuste anterior representa un descuento de 5% sobre el valor bruto de la línea. En el alcance CORE, `00` habilita otros descuentos y `50` otros cargos; códigos de detracción, retención y percepción permanecen bloqueados hasta contar con su implementación tributaria completa.

Los catálogos y la cobertura habilitada por esta versión se pueden consultar con:

```http
GET /api/v1/catalogs/sunat
```

El worker realiza generación, firma y envío. Consultar:

```http
GET /api/v1/documents/{id}
GET /api/v1/documents/{id}/history
GET /api/v1/documents/{id}/xml
GET /api/v1/documents/{id}/pdf
GET /api/v1/documents/{id}/cdr
```

## Facturas a crédito y cuotas

Para una factura a crédito se informa el saldo pendiente y sus cuotas:

```json
"payment": {
  "method": "CREDITO",
  "pendingAmount": 118.00,
  "installments": [
    {
      "dueDate": "2026-10-12",
      "amount": 118.00
    }
  ]
}
```

PeruBilling valida que el total de cuotas coincida con el saldo pendiente y genera los `PaymentTerms` SUNAT (`Credito`, `Cuota001`, etc.).

## Boletas y Resumen Diario

Una boleta creada en `/api/v1/receipts` entra primero a preparación asíncrona:

```text
SUMMARY_PREPARATION_QUEUED
        ↓
XML UBL + firma + PDF
        ↓
PENDING_SUMMARY
```

Solo cuando ya existen sus artefactos individuales queda disponible para ser agrupada en el Resumen Diario.

Para agrupar las boletas de una fecha:

```http
POST /api/v1/daily-summaries?issuerId={issuerId}&referenceDate=2026-09-11
```

El proceso genera un identificador:

```text
RC-20260911-1
```

Luego procesa `sendSummary`, conserva el ticket y consulta `getStatus`. Cuando el resumen es aceptado, los documentos incluidos pasan a `ACCEPTED`.

## Anulación / baja

Solicitud:

```http
POST /api/v1/documents/{id}/void
Content-Type: application/json
```

```json
{
  "reason": "ANULACION DE LA OPERACION"
}
```

El flujo depende del comprobante:

- factura y notas vinculadas a factura: genera Comunicación de Baja `RA` y procesa `sendSummary`/`getStatus`;
- boleta y notas vinculadas a boleta: utiliza Resumen Diario con condición de anulado;
- la solicitud no marca el documento como anulado hasta obtener un resultado aceptado del flujo SUNAT.

Estados adicionales:

```text
VOID_REQUESTED
VOIDED
```

## Ambientes SUNAT

```text
LOCAL
BETA
PRODUCTION
```

### LOCAL

No realiza tráfico hacia SUNAT. Genera un CDR simulado para probar de extremo a extremo aplicaciones consumidoras.

### BETA

Debe usarse para validar la integración técnica antes de producción. La documentación oficial de SUNAT indica el patrón de usuario de pruebas `[RUC]MODDATOS` y contraseña `MODDATOS`; el RUC corresponde al emisor. El ambiente BETA no debe tratarse como un servicio de carga/estrés.

En PeruBilling se configura el usuario SOL como `MODDATOS`; el gateway concatena automáticamente el RUC del emisor.

### PRODUCTION

Requiere configuración tributaria real del emisor, credenciales válidas, certificado vigente y validación contra las especificaciones oficiales vigentes.

## XSD SUNAT

SUNAT publica paquetes XSD separados para UBL 2.1 y UBL 2.0.

Para UBL 2.1:

```bash
./scripts/download-sunat-xsd.sh
```

El script descarga el paquete oficial UBL 2.1. Para Resumen Diario debe instalarse además el XSD UBL 2.0 desde la página oficial de Guías y Manuales de SUNAT dentro de `sunat-resources/xsd`. El validador busca recursivamente:

```text
UBL-Invoice-2.1.xsd
UBL-CreditNote-2.1.xsd
UBL-DebitNote-2.1.xsd
SummaryDocuments-1.xsd
```

En producción:

```text
SUNAT_XSD_ENABLED=true
SUNAT_XSD_PATH=/opt/perubilling/sunat-xsd
```

## Certificados

Carga:

```http
POST /api/v1/issuers/{issuerId}/certificates
Content-Type: multipart/form-data
```

Campos:

```text
file=<certificado.pfx>
password=<password>
alias=<opcional>
```

PeruBilling:

- abre y valida el PKCS#12;
- comprueba que exista una clave privada;
- comprueba vigencia X.509;
- en `PRODUCTION`, exige que la identidad X.509 exponga el RUC configurado del emisor;
- calcula fingerprint SHA-256;
- cifra bytes y contraseña mediante AES-256-GCM;
- desactiva el certificado anterior.

## Seguridad

Producción exige como mínimo:

```text
JWT_SECRET      Base64 de 32 bytes o más
MASTER_KEY      Base64 de exactamente 32 bytes
API_KEY_PEPPER  secreto aleatorio largo
```

Generar valores:

```bash
./scripts/generate-secrets.sh
```

No versionar:

```text
.pfx / .p12 reales
credenciales SOL
.env de producción
MASTER_KEY
JWT_SECRET
API Keys
backups con información tributaria
```

Controles implementados:

- BCrypt factor 12 para contraseñas humanas.
- JWT corto con validación explícita de `issuer`.
- API Keys hasheadas con pepper.
- cifrado autenticado AES-GCM.
- XML parser con DTD y entidades externas deshabilitadas.
- tenant derivado de la autenticación, no del request.
- correlativos transaccionales.
- idempotencia serializada para solicitudes concurrentes.
- bloqueo temporal de login tras intentos fallidos.
- auditoría.
- webhooks HTTPS con HMAC y bloqueo de redes privadas/locales.
- headers de seguridad.
- límites de multipart.
- workers con leases de procesamiento y recuperación de trabajos abandonados.
- claves foráneas compuestas para reforzar consistencia `tenant_id` en base de datos.

Para despliegue público se recomienda además un reverse proxy/API Gateway con TLS, rate limiting distribuido y límites de tamaño de request.

## Webhooks

Eventos:

```text
document.accepted
document.observed
document.rejected
document.failed
document.voided
```

Headers enviados:

```text
X-PeruBilling-Event
X-PeruBilling-Event-Id
X-PeruBilling-Timestamp
X-PeruBilling-Signature: v1=<hmac-sha256>
```

Firma:

```text
HMAC_SHA256(secret, timestamp + "." + rawBody)
```

El secreto del webhook solo se devuelve cuando se crea el endpoint.

## Estados de documento

```text
QUEUED
SUMMARY_PREPARATION_QUEUED
PENDING_SUMMARY
SUMMARY_PROCESSING
PROCESSING
RETRY_PENDING
ACCEPTED
OBSERVED
REJECTED
SEND_FAILED
VOID_REQUESTED
VOIDED
CANCELLED
```

## Persistencia y concurrencia

- El correlativo se obtiene con `PESSIMISTIC_WRITE` sobre la serie.
- El worker reclama filas mediante `FOR UPDATE SKIP LOCKED`.
- Puede ejecutarse más de una instancia del worker sobre la misma base.
- Los registros `PROCESSING` abandonados se recuperan automáticamente mediante lease temporal.
- `Idempotency-Key` evita duplicados producidos por reintentos del POS/ERP, incluso bajo solicitudes concurrentes dentro de PostgreSQL.

## Migraciones

```text
src/main/resources/db/migration/V1__initial_schema.sql
src/main/resources/db/migration/V2__audit_hardening_phase1.sql
src/main/resources/db/migration/V3__communication_of_voiding.sql
src/main/resources/db/migration/V4__tenant_consistency_constraints.sql
src/main/resources/db/migration/V5__audit_immutability.sql
src/main/resources/db/migration/V6__phase2_reliability_and_delivery.sql
src/main/resources/db/migration/V7__issuer_establishment_code.sql
src/main/resources/db/migration/V8__document_issue_time.sql
src/main/resources/db/migration/V9__api_integration_hardening.sql
src/main/resources/db/migration/V10__line_allowances_charges_and_sunat_catalogs.sql
src/main/resources/db/migration/V11__global_adjustments_and_catalog53_correction.sql
src/main/resources/db/migration/V12__database_artifact_storage.sql
src/main/resources/db/migration/V13__product_classification_codes.sql
src/main/resources/db/migration/V14__export_customer_country.sql
```

Hibernate usa:

```text
spring.jpa.hibernate.ddl-auto=validate
```

La base es responsabilidad de Flyway.

## Pruebas

```bash
./mvnw test
```

La fase de endurecimiento incluye pruebas unitarias/estructurales para:

- dígito verificador de RUC;
- IGV, IVAP y operaciones gratuitas;
- esquemas tributarios por código de afectación;
- `PaymentTerms` y cuotas;
- Comunicación de Baja;
- política de endpoints SUNAT;
- configuración segura por defecto;
- conversión de monto a letras;
- ausencia de ciclos entre módulos mediante una prueba de arquitectura.

La suite ya incluye integración con PostgreSQL/Testcontainers para migraciones y lockout, pero esto no sustituye los casos oficiales de validación contra SUNAT BETA. Antes de producción deben ejecutarse las reglas SUNAT vigentes, XSD/XSL oficiales y casos BETA del emisor.

## Configuración de producción

Copiar `.env.example` y establecer secretos reales. El perfil `prod` deshabilita el bootstrap y exige XSD.

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/perubilling-0.8.0-SNAPSHOT.jar
```

No se recomienda exponer directamente el puerto de Spring Boot a Internet. Use TLS en un reverse proxy o balanceador.

## Brechas todavía pendientes antes de producción tributaria

La versión actual cierra varios requisitos operativos del CORE, pero **no habilita automáticamente producción**. Permanecen como gates:

- ejecutar `./mvnw clean verify` con JDK 25, Docker y Testcontainers;
- instalar XSD oficiales UBL 2.1/2.0 con hashes revisados y fijados por release;
- validar XMLDSig/XSD/fixtures y realizar envíos reales a SUNAT BETA, conservando CDR sanitizados como regresión;
- realizar un ejercicio real de backup/restore y prueba de carga/concurrencia antes del piloto;
- aplicar controles de egress/red para webhooks como defensa adicional frente a DNS rebinding;
- probar restauración, carga y operación multi-réplica sobre el backend de artefactos `database`;
- ampliar regímenes especiales solo cuando entren en alcance: detracciones, ISC, anticipos, percepciones/retenciones, OSE y otros CPE no declarados por `/api/v1/capabilities`.

## Referencias oficiales

- SUNAT CPE - Guías, reglas y archivos XSD: https://cpe.sunat.gob.pe/guias-y-manuales
- SUNAT - Sistemas de emisión electrónica: https://cpe.sunat.gob.pe/
- SUNAT - SEE del contribuyente: https://cpe.sunat.gob.pe/sistema_emision/see_contribuyente

## Nota de conformidad

El código constituye una base de ingeniería orientada a producción, pero **no equivale a una certificación u homologación tributaria**. Antes de usarlo para documentos fiscales reales deben ejecutarse las reglas de validación SUNAT vigentes, XSD oficiales, casos BETA del emisor y una revisión de los supuestos tributarios aplicables al negocio concreto.

El núcleo actual cubre el flujo principal de factura, boleta y notas. Regímenes/operaciones especiales que no estén modelados explícitamente —por ejemplo, determinados escenarios de detracción, percepción, retención, anticipos u otros CPE— deben incorporarse como módulos/reglas específicas antes de utilizarlos en esos casos.

## Estado de la versión actual

La versión `0.8.0-SNAPSHOT` incorpora el hardening técnico vigente para preproducción:

- guard de configuración `prod` fail-closed;
- PostgreSQL explícito y almacenamiento de artefactos con checksum;
- HTTPS obligatorio para URL pública y CORS productivo;
- `API_KEY_PEPPER` de al menos 32 caracteres;
- JaCoCo con umbral mínimo durante `mvn verify`;
- grupos separados de liveness/readiness;
- backup/restore compatible con `DATABASE_URL` JDBC;
- verificación de integridad para artefactos en PostgreSQL/filesystem;
- pruebas de outbox, workers, procesamiento y validación SSRF de webhooks;
- gate regulatorio separado que exige XSD/reglas oficiales y evidencia SUNAT BETA real.

Para verificación técnica use `VERIFICATION.md`. Para operación y despliegue use `OPERATIONS.md`. Para el procedimiento de evidencia BETA use `docs/SUNAT_BETA_CERTIFICATION.md`.
