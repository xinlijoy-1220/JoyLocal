#!/bin/sh
set -eu

NAMESRV_ADDR="${NAMESRV_ADDR:-rmqnamesrv:9876}"
SECKILL_TOPIC="${SECKILL_TOPIC:-joylocal-seckill-order}"
TIMEOUT_TOPIC="${TIMEOUT_TOPIC:-joylocal-order-timeout}"
CLUSTER_NAME="${CLUSTER_NAME:-DefaultCluster}"
BROKER_NAME="${BROKER_NAME:-broker-a}"

printf '[topic-init] waiting for broker %s in cluster %s on %s ...\n' "${BROKER_NAME}" "${CLUSTER_NAME}" "${NAMESRV_ADDR}"
while true; do
  if sh mqadmin clusterList -n "${NAMESRV_ADDR}" > /tmp/clusterList.log 2>&1; then
    if grep -q "${CLUSTER_NAME}" /tmp/clusterList.log && grep -q "${BROKER_NAME}" /tmp/clusterList.log; then
      break
    fi
  fi
  cat /tmp/clusterList.log 2>/dev/null || true
  sleep 5
done

printf '[topic-init] creating normal topic: %s\n' "${SECKILL_TOPIC}"
sh mqadmin updateTopic \
  -n "${NAMESRV_ADDR}" \
  -c "${CLUSTER_NAME}" \
  -t "${SECKILL_TOPIC}" \
  -r 8 \
  -w 8 \
  -a +message.type=NORMAL

printf '[topic-init] creating delay topic: %s\n' "${TIMEOUT_TOPIC}"
sh mqadmin updateTopic \
  -n "${NAMESRV_ADDR}" \
  -c "${CLUSTER_NAME}" \
  -t "${TIMEOUT_TOPIC}" \
  -r 8 \
  -w 8 \
  -a +message.type=DELAY

printf '[topic-init] topics are ready\n'
