#!/bin/bash
cd /home/son/Documents/hoc_java/Crawler/redis-cluster
echo "Checking port 6379..."
if netstat -tulnp 2>/dev/null | grep :6379; then
    echo "Port 6379 is in use, stopping existing containers..."
    docker-compose down
fi
echo "Checking for existing containers..."
if docker ps -a | grep redis-cluster; then
    echo "Stopping and removing existing containers..."
    docker-compose down
fi
echo "Starting..."
docker-compose up -d
sleep 60
echo "Checking Redis Cluster status..."
if docker ps | grep redis-cluster_redis-1_1 >/dev/null; then
    STATUS=$(docker exec redis-cluster_redis-1_1 redis-cli -c -p 6379 CLUSTER INFO 2>/dev/null | grep cluster_state | cut -d':' -f2)
    if [ "$STATUS" != "ok" ]; then
        echo "Recreating Cluster..."
        docker exec redis-cluster_redis-1_1 redis-cli --cluster create \
          172.20.0.2:6379 172.20.0.3:6379 172.20.0.4:6379 \
          172.20.0.5:6379 172.20.0.6:6379 172.20.0.7:6379 \
          --cluster-replicas 1 --cluster-yes
    fi
else
    echo "Error: redis-cluster_redis-1_1 is not running"
    exit 1
fi
echo "Ready!"