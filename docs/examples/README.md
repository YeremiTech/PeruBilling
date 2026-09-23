# Ejemplos PDF

Coloca aquí 2 PDF de referencia generados por la API:

```text
docs/examples/factura-a4-ejemplo.pdf        -> Factura en formato A4
docs/examples/boleta-thermal-80-ejemplo.pdf -> Boleta en ticket térmico 80 mm
```

Se generan con:

```http
GET /api/v1/documents/{id}/pdf?layout=A4
GET /api/v1/documents/{id}/pdf?layout=THERMAL_80
```

> Usa documentos de prueba en ambiente `LOCAL` (RUC sintético). No subas comprobantes reales con datos sensibles.
