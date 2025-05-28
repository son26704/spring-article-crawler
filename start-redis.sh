#!/bin/bash
cd /home/son/Documents/hoc_java/Crawler/redis-cluster
echo "Checking port 6379..."
if netstat -tulnp | grep :6379; then
    echo "Port 6379 is in use, please stop Redis Standalone or other processes"
    exit 1
fi
echo "Checking Redis Cluster containers..."
if ! docker ps | grep redis-cluster; then
    echo "Starting Redis Cluster..."
    docker-compose up -d
    sleep 10
fi
echo "Checking Redis Cluster status..."
STATUS=$(docker exec redis-cluster_redis-1_1 redis-cli -c -p 6379 CLUSTER INFO | grep cluster_state | cut -d':' -f2)
if [ "$STATUS" != "ok" ]; then
    echo "Cleaning existing data..."
    docker-compose down -v
    docker-compose up -d
    sleep 10
    echo "Recreating Redis Cluster..."
    docker exec redis-cluster_redis-1_1 redis-cli --cluster create \
      172.20.0.2:6379 172.20.0.3:6379 172.20.0.4:6379 \
      172.20.0.5:6379 172.20.0.6:6379 172.20.0.7:6379 \
      --cluster-replicas 1 --cluster-yes
fi
echo "Redis Cluster is ready!"