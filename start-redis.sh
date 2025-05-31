#!/bin/bash
cd /home/son/Documents/hoc_java/Crawler/redis-cluster

echo "Checking port 6379..."
if sudo netstat -tulnp | grep :6379; then
    echo "Port 6379 is in use, stopping existing containers..."
    docker-compose down
    sleep 5
    # Xóa container Redis cũ
    docker stop $(docker ps -a -q --filter "name=redis-cluster_redis") 2>/dev/null
    docker rm $(docker ps -a -q --filter "name=redis-cluster_redis") 2>/dev/null
    # Kiểm tra lại port
    if sudo netstat -tulnp | grep :6379; then
        echo "Error: Port 6379 is still in use, please stop the process manually"
        exit 1
    fi
fi

echo "Checking Redis Cluster containers..."
if ! docker ps | grep redis-cluster_redis-1_1; then
    echo "Starting Redis Cluster containers..."
    docker-compose up -d
    echo "Waiting for containers to be ready..."
    sleep 30
fi

echo "Waiting for Redis nodes to be fully ready..."
for i in {1..6}; do
    node_ip="172.20.0.$((i+1))"
    echo "Checking Redis node at $node_ip:6379..."
    for attempt in {1..15}; do
        if docker exec redis-cluster_redis-${i}_1 redis-cli -h $node_ip -p 6379 ping 2>/dev/null | grep -q PONG; then
            echo "Redis node $i is ready"
            break
        else
            echo "Waiting for Redis node $i... (attempt $attempt)"
            sleep 3
        fi
        if [ $attempt -eq 15 ]; then
            echo "Error: Redis node $i failed to start"
            exit 1
        fi
    done
done

echo "Checking Redis Cluster status..."
CLUSTER_STATE=$(docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER INFO 2>/dev/null | grep cluster_state | cut -d':' -f2 | tr -d '\r')
SLOTS_ASSIGNED=$(docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER INFO 2>/dev/null | grep cluster_slots_assigned | cut -d':' -f2 | tr -d '\r')

echo "Current cluster state: $CLUSTER_STATE"
echo "Slots assigned: $SLOTS_ASSIGNED"

if [ "$CLUSTER_STATE" != "ok" ] || [ "$SLOTS_ASSIGNED" != "16384" ]; then
    echo "Redis Cluster needs initialization or repair..."

    # Reset cluster configuration on all nodes first
    echo "Resetting cluster configuration on all nodes..."
    for i in {1..6}; do
        node_ip="172.20.0.$((i+1))"
        echo "Resetting node $i..."
        docker exec redis-cluster_redis-${i}_1 redis-cli -h $node_ip -p 6379 CLUSTER RESET HARD 2>/dev/null || true
        sleep 2
    done

    echo "Waiting after cluster reset..."
    sleep 10

    echo "Creating Redis Cluster..."
    docker exec redis-cluster_redis-1_1 redis-cli --cluster create \
        172.20.0.2:6379 172.20.0.3:6379 172.20.0.4:6379 \
        172.20.0.5:6379 172.20.0.6:6379 172.20.0.7:6379 \
        --cluster-replicas 1 --cluster-yes

    sleep 15

    # Kiểm tra lại cluster status
    CLUSTER_STATE_AFTER=$(docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER INFO | grep cluster_state | cut -d':' -f2 | tr -d '\r')
    SLOTS_ASSIGNED_AFTER=$(docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER INFO | grep cluster_slots_assigned | cut -d':' -f2 | tr -d '\r')

    if [ "$CLUSTER_STATE_AFTER" = "ok" ] && [ "$SLOTS_ASSIGNED_AFTER" = "16384" ]; then
        echo "Redis Cluster initialized successfully"
    else
        echo "Failed to initialize Redis Cluster properly"
        echo "State: $CLUSTER_STATE_AFTER, Slots: $SLOTS_ASSIGNED_AFTER"
        exit 1
    fi
else
    echo "Redis Cluster is already properly initialized"
fi

echo "Redis Cluster is ready!"
echo "=== Cluster Info ==="
docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER INFO
echo "=== Cluster Nodes ==="
docker exec redis-cluster_redis-1_1 redis-cli -h 172.20.0.2 -p 6379 CLUSTER NODES