#!/bin/bash
/home/son/Documents/hoc_java/Crawler/start-redis.sh
cd /home/son/Documents/hoc_java/Crawler
echo "Fixing log directory permissions..."
mkdir -p logs
sudo chown -R son:son logs
sudo chmod -R u+rw logs
echo "Building and running Spring Boot application..."
mvn clean package -DskipTests
mvn spring-boot:run