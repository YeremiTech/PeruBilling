#!/usr/bin/env sh
set -eu
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48 | tr -d '\n')"
printf 'MASTER_KEY=%s\n' "$(openssl rand -base64 32 | tr -d '\n')"
printf 'API_KEY_PEPPER=%s\n' "$(openssl rand -hex 32)"
