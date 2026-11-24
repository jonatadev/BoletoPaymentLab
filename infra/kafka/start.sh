#!/usr/bin/env bash
set -euo pipefail
umask 077

data=/var/lib/kafka/data
config=$(mktemp /dev/shm/bpl-kafka.XXXXXX)
cat /opt/bpl-kafka/server.properties /run/kafka/broker.properties > "$config"
printf '\nadvertised.listeners=INTERNAL://kafka:29092,HOST://127.0.0.1:%s,REPLICATION://kafka:29094,ADMIN://kafka:29095\n' "${KAFKA_PORT:-9092}" >> "$config"

if [[ ! -f "$data/meta.properties" ]]; then
    if [[ -n "$(find "$data" -mindepth 1 -print -quit)" ]]; then
        echo "Kafka storage contains data without meta.properties. Restore storage metadata before startup." >&2
        exit 1
    fi
    /opt/kafka/bin/kafka-storage.sh format --cluster-id "$(/opt/kafka/bin/kafka-storage.sh random-uuid)" --config "$config"
fi
exec /opt/kafka/bin/kafka-server-start.sh "$config"
