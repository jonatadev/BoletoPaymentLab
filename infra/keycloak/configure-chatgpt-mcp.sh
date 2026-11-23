#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077

: "${KC_BOOTSTRAP_ADMIN_USERNAME:?Missing local Keycloak administrator username}"
: "${KC_BOOTSTRAP_ADMIN_PASSWORD:?Missing local Keycloak administrator password}"
: "${CHATGPT_MCP_REDIRECT_URI:?Set the exact HTTPS callback supplied by the MCP client}"

redirect=$CHATGPT_MCP_REDIRECT_URI
if [[ ! $redirect =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?/[^[:space:]]+$ ||
      $redirect == *'*'* || $redirect == *'#'* || $redirect == *'"'* || $redirect == *\\* ]]; then
    echo 'Expected a single exact HTTPS redirect URI without wildcards or fragments.' >&2
    exit 1
fi

dir=$(mktemp -d)
trap 'rm -f -- "$dir/session"; rmdir -- "$dir"' EXIT
admin() {
    /opt/keycloak/bin/kcadm.sh "$@" --config "$dir/session"
}
KC_CLI_PASSWORD=$KC_BOOTSTRAP_ADMIN_PASSWORD admin config credentials \
    --server http://127.0.0.1:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME"
unset KC_BOOTSTRAP_ADMIN_PASSWORD KC_CLI_PASSWORD

realm=boleto-lab
client=boleto-chatgpt
scope=analytics:read
audience=boleto-analytics-mcp
mapper='boleto-analytics-mcp audience'

# kcadm CSV output has no header. Match exact names even if the server search is partial.
lookup_id() {
    local expected=$1 id name found=''
    while IFS=, read -r id name; do
        name=${name//\"/}
        id=${id//\"/}
        if [[ $name == "$expected" ]]; then
            [[ -z $found && $id =~ ^[[:xdigit:]-]+$ ]] || return 1
            found=$id
        fi
    done
    printf '%s' "$found"
}

rows=$(admin get clients -r "$realm" -q "clientId=$client" --fields id,clientId --format csv)
client_id=$(lookup_id "$client" <<< "$rows")
if [[ -z $client_id ]]; then
    client_id=$(admin create clients -r "$realm" -i -s "clientId=$client" \
        -s protocol=openid-connect -s enabled=false -s publicClient=true -s fullScopeAllowed=false)
fi

rows=$(admin get client-scopes -r "$realm" --fields id,name --format csv)
scope_id=$(lookup_id "$scope" <<< "$rows")
if [[ -z $scope_id ]]; then
    scope_id=$(admin create client-scopes -r "$realm" -i -s "name=$scope" \
        -s protocol=openid-connect -s 'attributes={"include.in.token.scope":"true"}')
else
    admin update "client-scopes/$scope_id" -r "$realm" -s 'attributes."include.in.token.scope"=true'
fi

admin update "clients/$client_id/default-client-scopes/$scope_id" -r "$realm" -n
mapper_path="clients/$client_id/protocol-mappers/models"
rows=$(admin get "$mapper_path" -r "$realm" --fields id,name --format csv)
mapper_id=$(lookup_id "$mapper" <<< "$rows")
mapper_settings=(-s "name=$mapper" -s protocol=openid-connect -s protocolMapper=oidc-audience-mapper
    -s "config={\"included.custom.audience\":\"$audience\",\"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}")
if [[ -z $mapper_id ]]; then
    mapper_id=$(admin create "$mapper_path" -r "$realm" -i "${mapper_settings[@]}")
else
    admin update "$mapper_path/$mapper_id" -r "$realm" "${mapper_settings[@]}"
fi

admin update "clients/$client_id" -r "$realm" -s enabled=true -s publicClient=true \
    -s standardFlowEnabled=true -s implicitFlowEnabled=false \
    -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=false -s fullScopeAllowed=false \
    -s 'attributes."pkce.code.challenge.method"=S256' -s "redirectUris=[\"$redirect\"]"

client_config=$(admin get "clients/$client_id" -r "$realm")
grep -Eq '"pkce.code.challenge.method"[[:space:]]*:[[:space:]]*"S256"' <<< "$client_config"
grep -Eq '"publicClient"[[:space:]]*:[[:space:]]*true' <<< "$client_config"
grep -Eq '"standardFlowEnabled"[[:space:]]*:[[:space:]]*true' <<< "$client_config"
grep -Fq "\"$redirect\"" <<< "$client_config"
rows=$(admin get "clients/$client_id/default-client-scopes" -r "$realm" --fields id,name --format csv)
[[ $(lookup_id "$scope" <<< "$rows") == "$scope_id" ]]
mapper_config=$(admin get "$mapper_path/$mapper_id" -r "$realm")
grep -Eq '"included.custom.audience"[[:space:]]*:[[:space:]]*"boleto-analytics-mcp"' <<< "$mapper_config"
grep -Eq '"access.token.claim"[[:space:]]*:[[:space:]]*"true"' <<< "$mapper_config"
echo 'Validated public authorization-code client, PKCE S256, default analytics scope and access-token audience mapper.'
