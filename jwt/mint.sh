#!/usr/bin/env sh
# Prints a JWT for a customer, signed with the POC private key (RS256), valid until 2030-01-01.
# Usage: ./jwt/mint.sh C-1001
# The services validate it with security/src/main/resources/jwt/poc-public.pem.
set -eu
dir=$(cd "$(dirname "$0")" && pwd)
customer="$1"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"iss":"poc-idp","sub":"auth0|%s","customer_id":"%s","iat":%s,"exp":1893456000}' \
  "$customer" "$customer" "$(date +%s)" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -sign "$dir/poc-private.pem" -binary | b64url)
printf '%s.%s.%s\n' "$header" "$payload" "$signature"
