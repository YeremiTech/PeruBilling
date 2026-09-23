# PeruBilling — ticket térmico 80 mm (v7)

El ticket usa el motor existente Thymeleaf + OpenHTMLtoPDF + PDFBox. El PDF A4 se conserva sin cambios visuales respecto a v6.

## Diseño
- Página PDF de 80 mm de ancho y longitud adaptada al contenido.
- Márgenes laterales de 3 mm; fondo blanco, texto oscuro; sin tarjetas, gradientes ni iconos decorativos.
- Emisor compacto (logo, marca, razón social, RUC, dirección y establecimiento).
- Comprobante centrado; cliente, operación, detalle por producto y totales con separadores finos.
- QR de 29 mm centrado; URL y DigestValue conservados; pie legal discreto.
- Mensaje de agradecimiento opcional: `perubilling.pdf.thermal.thank-you=Gracias por su preferencia.` (por defecto vacío).

## Motor y longitud
Se calcula una altura inicial conservadora, se reintenta si el PDF ocupa más de una página y luego se ajusta a la última línea de texto, dejando aproximadamente 6 mm de margen final. Si el comprobante supera la longitud máxima configurada (2000 mm), la generación falla expresamente en lugar de emitir un ticket cortado. No se alteraron cálculos tributarios, endpoints ni contratos de integración.

## Validaciones realizadas aquí
- `bash scripts/verify-project.sh --static-only` superado.
- Verificación estática de la plantilla: texto negro, tamaño tipográfico y saltos de línea.
- Cuatro PDFs ilustrativos renderizados con motor alternativo (no OpenHTMLtoPDF) para 1, 5, 20 productos y campos largos. Todos tienen ancho físico de 80 mm, una sola página, textos dentro de 3–77 mm de la página y conservan QR y pie legal.

## Validaciones pendientes en la aplicación
- Ejecutar `./mvnw verify` con Java 25 y Maven disponibles: incluye `ThermalTicketPrintRegressionTest` para validar PDF binario generado por OpenHTMLtoPDF con casos 1, 5, 20 y campos largos.
- Repetir descarga real del ticket con documento nuevo; los PDF existentes almacenados no se actualizan al cambiar la plantilla.
- Comprobar en impresora física la zona imprimible de 3 mm; algunos modelos usan márgenes mayores.

Los PDFs ilustrativos de prueba no demuestran la renderización final del backend. No considerar completada la certificación SUNAT a partir de estas pruebas de presentación.
