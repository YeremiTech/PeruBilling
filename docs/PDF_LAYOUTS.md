# PDF layouts

PeruBilling genera dos representaciones del mismo comprobante electrónico.

## A4

- Valor por defecto y compatible con clientes existentes.
- Orientado a ERP, backoffice, correo y archivo documental.
- Wrapper: `templates/pdf/cpe-a4.html`.
- Fragmento visual: `templates/pdf/fragments/cpe-a4-modern.html`.
- Soporta múltiples páginas y repetición de cabecera de ítems.

## THERMAL_80

- Orientado a POS y caja.
- Ancho PDF de 80 mm y altura calculada según contenido para reducir cortes artificiales.
- Wrapper: `templates/pdf/cpe-thermal-80.html`.
- Fragmento visual: `templates/pdf/fragments/cpe-thermal-modern.html`.
- Mantiene QR SUNAT, DigestValue, importes, adquirente, referencias y cuotas.

## API

```http
GET /api/v1/documents/{id}/pdf?layout=A4
GET /api/v1/documents/{id}/pdf?layout=THERMAL_80
```

Omitir `layout` equivale a `A4`. Un valor no reconocido produce `400 INVALID_PARAMETER` mediante el manejo global de errores.

El portal público también soporta el parámetro `layout`.

## Persistencia

- `pdf_path`: A4.
- `thermal_pdf_path`: THERMAL_80.

Flyway V16 agrega la segunda ruta sin modificar migraciones históricas.

## Alcance

No se implementa ESC/POS ni acceso directo a impresoras. Esa responsabilidad pertenece al POS o a un agente de impresión. PeruBilling entrega PDF listo para consumo por integraciones.

## Diseño visual

El lenguaje visual usa azul petróleo y turquesa, cabecera de emisor, bloque destacado del CPE, tarjetas informativas, tabla de ítems, total destacado y verificación QR. El ticket de 80 mm reorganiza los mismos datos en una columna vertical. No se agregan teléfono, vendedor, billeteras digitales ni otros datos que el modelo de PeruBilling no almacena.
