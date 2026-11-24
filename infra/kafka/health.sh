#!/usr/bin/env bash
set -eu

identity=/run/kafka/identity.p12
# Kafka ApiVersions v0, correlation ID 1 and an empty client ID.
# Verify the echoed correlation ID and zero error code after the frame length.
# The listener requires the node certificate before it processes Kafka requests.
response=$(
    printf '\x00\x00\x00\x0a\x00\x12\x00\x00\x00\x00\x00\x01\x00\x00' |
        timeout -k 2s 8s openssl s_client -quiet -connect 127.0.0.1:29094 \
            -verify_ip 127.0.0.1 -verify_return_error -CAfile /run/kafka/ca.pem \
            -cert "$identity" -key "$identity" -pass fd:3 \
            3< <(sed -n 's/^ssl.keystore.password=//p' /run/kafka/broker.properties) 2>/dev/null |
        od -An -j4 -N6 -tx1 | tr -d '[:space:]'
)
test "$response" = 000000010000
