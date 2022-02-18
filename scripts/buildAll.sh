#!/bin/bash
scriptDir=$(dirname $0)

IMAGE_NAME=quay.io/ibmcase/store-aggregator
TAG=latest
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t ${IMAGE_NAME}:${TAG} .
docker push ${IMAGE_NAME}:${TAG}
