# PeruBilling: plantillas de impresión A4 y THERMAL_80 — revisión v6

## Motivo del ajuste
El ZIP v5 no contenía la plantilla A4 corregida en v4: se basó en otra rama de los archivos y volvió a introducir la hoja gris con tarjeta exterior redondeada y pie flotante. Además, en el ticket v5 la suma de las columnas del encabezado superaba el ancho útil de 80 mm, lo que podía recortar el eslogan en el borde derecho. El cálculo estimado de altura dejaba una cola innecesaria al final del ticket.

## Qué contiene v6
- **A4:** se reincorpora íntegra la plantilla de impresión v4: hoja física A4 (210×297 mm), cabecera visual de 210 mm sin tarjeta exterior, iconos azules alineados con la fila y pie fijo a todo el ancho del borde inferior. La zona inferior se reserva con `@page` y la paginación procede de un margin box. No se cambian importes ni códigos SUNAT.
- **THERMAL_80:** tabla del encabezado cuya suma de columnas cabe en el ancho útil, eslogan en una fila propia a la derecha, RUC/domicilio/ubigeo con iconos alineados verticalmente, tarjetas y barras azul petróleo, bloque turquesa de total, QR con espacio blanco y pie sin superposición.
- **Descuentos y crédito:** se conservan descuentos por línea y se muestra el bloque de modalidad, saldo y cuotas cuando la operación es a crédito. El bloque de documento afectado se conserva para notas.
- **Tamaño del papel:** el generador empieza con una altura estimada, amplía el papel si hay desborde y recorta la página usando la última posición del texto impreso y 12 mm de margen de seguridad. Si un comprobante excede 2 m, devuelve un error explícito en lugar de un PDF truncado.
- **QR:** se amplió el margen blanco del QR a 4 módulos, manteniendo el contenido tributario sin cambios.
- **Versión detectable:** la información interna de cada PDF nuevo contiene `PeruBilling-Template-Version=PRINT-V6-20260922` y `PeruBilling-Pdf-Layout=A4` o `THERMAL_80`, para distinguir artefactos antiguos de los nuevos.

## Importante: los artefactos antiguos NO se actualizan solos
Los PDF A4 y térmicos se generan durante el procesamiento y se almacenan como artefactos separados. Si vuelves a descargar una factura procesada antes de desplegar esta versión, obtendrás el PDF antiguo. En el entorno LOCAL emite **un comprobante NUEVO con una serie/número diferente** para comprobar visualmente v6. No reescribas XML firmado, CDR ni PDF fiscal ya distribuido en producción solo por motivos cosméticos.

## Pruebas en el equipo del usuario (Windows, JDK 25)
Desde la carpeta `PeruBilling`:

```powershell
.\mvnw.cmd "-Dtest=DocumentPdfTemplateContractTest,PdfBrandAssetsTest,SunatQrCodeGeneratorTest,DocumentPdfGeneratorPublicLinkTest,DocumentPdfLayoutTest" test
```

A continuación, con PostgreSQL/Docker en funcionamiento si requieren las pruebas de integración:

```powershell
.\mvnw.cmd clean verify
```

El proyecto sigue ofreciendo `GET /api/v1/documents/{id}/pdf?layout=A4` y `?layout=THERMAL_80`, así como sus equivalentes en el portal público. En un PDF nuevo inspecciona la anchura: A4 ≈210 mm, térmico ≈80 mm; el ticket de una factura sencilla debe tener **una sola página continua**.

## Límites de verificación del paquete
Se ha comprobado la sintaxis XHTML de ambas plantillas, la carga y los colores de los recursos gráficos, los controles estáticos del proyecto y un PDF ilustrativo del ticket generado por WeasyPrint. **No se ha ejecutado Maven ni OpenHTMLtoPDF en este entorno** (dispone de Java 21, sin Maven, y no puede acceder a los repositorios externos). El PDF ilustrativo no constituye una salida real del backend ni una validación SUNAT.
