#!/bin/bash
# ==============================================================================
# Kafka Topic Initialization Script
# Pre-creates all domain event topics with 3 partitions and replication factor 1
# ==============================================================================

echo "Waiting for Kafka broker to be ready..."
cub kafka-ready -b localhost:9092 1 30 || true

TOPICS=(
  "order-created-events"
  "inventory-reserved-events"
  "inventory-rejected-events"
  "payment-succeeded-events"
  "payment-failed-events"
  "inventory-released-events"
)

for topic in "${TOPICS[@]}"; do
  echo "Creating topic: $topic"
  /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --topic "$topic"
done

echo "All Kafka topics created successfully!"
