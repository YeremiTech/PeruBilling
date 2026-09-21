# Reglas oficiales SUNAT

PeruBilling mantiene un gate semántico **CORE** deliberadamente limitado y versionado;
no afirma que el código replique automáticamente toda la hoja oficial de SUNAT.

Para cada release, `scripts/download-sunat-rules.sh` descarga la publicación oficial
identificada por `app.sunat.rules.version=2026-08-26`. El pipeline de release debe fijar
`SUNAT_RULES_SHA256`; si cambia el archivo oficial, la instalación falla hasta que una
revisión humana actualice el hash y los fixtures correspondientes.

`SUNAT_RULES_ALLOW_UNPINNED=true` existe únicamente para obtener el hash durante una
revisión local inicial. No debe utilizarse en BETA/PRODUCTION.

La hoja oficial se usa como fuente normativa para ampliar tests de regresión y el motor
semántico antes de promover una nueva versión. Los casos fuera del CORE soportado deben
fallar antes de generar/enviar XML.
