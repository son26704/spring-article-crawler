#!/bin/bash
/home/son/Documents/hoc_java/Crawler/start-redis.sh
cd /home/son/Documents/hoc_java/Crawler
echo "Building and running Spring Boot application..."
mvn clean package -DskipTests
java -jar target/Crawler-0.0.1-SNAPSHOT.jar