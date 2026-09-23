# SUNAT XSD runtime manifest

PeruBilling **no empaqueta ni sustituye** los XSD oficiales. En producción el proceso
es fail-fast: `SUNAT_XSD_PATH` debe contener los entrypoints UBL 2.1/2.0 requeridos.

El instalador `scripts/download-sunat-xsd.sh` descarga ambos paquetes desde las URLs
oficiales configuradas, exige SHA-256 fijados por el operador/release y verifica que
queden disponibles recursivamente:

- `UBL-Invoice-2.1.xsd`
- `UBL-CreditNote-2.1.xsd`
- `UBL-DebitNote-2.1.xsd`
- `SummaryDocuments-1.xsd`
- `VoidedDocuments-1.xsd`

Uso recomendado en una release:

```bash
export SUNAT_XSD21_SHA256='<hash revisado para esta release>'
export SUNAT_XSD20_SHA256='<hash revisado para esta release>'
./scripts/download-sunat-xsd.sh /opt/perubilling/sunat-xsd
```

El script genera `INSTALLATION.sha256` con los hashes efectivamente instalados. No
use `SUNAT_XSD_ALLOW_UNPINNED=true` en producción; existe solo para inspección local
antes de fijar los checksums de una release.

Los enlaces y hashes deben revisarse cada vez que SUNAT publique una actualización.
La versión regulatoria CORE de la aplicación se mantiene separada en
`app.sunat.rules.version`.
