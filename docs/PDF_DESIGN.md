# Diseño PDF de PeruBilling

PeruBilling utiliza Thymeleaf + OpenHTMLToPDF para generar dos representaciones visuales del mismo CPE:

- `A4`: representación empresarial para ERP, correo, archivo y descarga.
- `THERMAL_80`: representación compacta para POS e impresoras térmicas de 80 mm.

## Referencia visual

El diseño se inspira en la arquitectura de plantillas del proyecto `YeremiTech/pymex-erp-backend`:

- wrapper por formato;
- fragmento de contenido reutilizable;
- composición por tablas compatible con motores HTML-to-PDF;
- paleta azul petróleo + verde turquesa;
- bloques diferenciados para emisor, cliente, comprobante, detalle, totales y QR.

No se copian datos comerciales del proyecto de referencia. PeruBilling usa exclusivamente la información disponible en su dominio tributario.

## Estructura

```text
src/main/resources/templates/pdf/
├── cpe-a4.html
├── cpe-thermal-80.html
└── fragments/
    ├── cpe-a4-modern.html
    └── cpe-thermal-modern.html
```

Los wrappers contienen reglas de tamaño de página y CSS. Los fragments contienen la estructura Thymeleaf y los datos del comprobante.

## Diseño A4

La representación A4 incluye:

1. Identidad del emisor con monograma generado desde el nombre comercial o razón social.
2. RUC, domicilio fiscal, ubigeo y establecimiento.
3. Tarjeta superior del tipo de CPE y numeración.
4. Datos del cliente.
5. Datos del comprobante.
6. Bloque de documento afectado para notas de crédito/débito.
7. Tabla de ítems con cantidad, unidad, valor unitario, descuento e importe.
8. Observaciones y condición de pago.
9. Totales tributarios y total a pagar.
10. Importe en letras.
11. QR SUNAT, DigestValue y enlace público cuando exista.
12. Pie legal y número de página.

## Diseño Thermal 80

La representación térmica usa exactamente el mismo modelo tributario y QR, pero reorganiza la información verticalmente:

1. Emisor y RUC.
2. Tipo y número de CPE.
3. Cliente.
4. Datos de operación y moneda.
5. Documento afectado cuando aplique.
6. Detalle de ítems.
7. Totales.
8. Condición de pago/cuotas cuando aplique.
9. Observaciones.
10. QR SUNAT y acceso público.

## Decisiones de diseño

- No se inventan teléfono, vendedor, Yape/Plin, tarjeta u otros datos que PeruBilling no almacena.
- El QR sigue siendo el QR tributario SUNAT; no se reemplaza por el enlace público.
- El enlace público se presenta como elemento adicional.
- El logotipo se representa por un monograma derivado del emisor hasta que exista un módulo de branding/logo explícito.
- Los colores son deliberadamente sobrios para mantener legibilidad en PDF e impresión.
- No se usa Flexbox/Grid como dependencia estructural principal; se priorizan tablas y bloques por compatibilidad con OpenHTMLToPDF.


## Validaciones visuales reforzadas

- El contenedor A4 no usa `overflow:hidden`, para no recortar comprobantes con muchos ítems.
- El pie A4 usa posicionamiento fijo y el margen inferior de página reserva espacio para evitar superposición en documentos multipágina.
- El cálculo de altura de `THERMAL_80` considera cantidad de ítems y longitud aproximada de textos para evitar tanto cortes como colas blancas excesivas.
- Existe una prueba de paginación A4 con 55 ítems y una prueba de altura máxima razonable para un ticket térmico corto.
- El QR tributario y el `DigestValue` permanecen independientes del enlace público de consulta.
