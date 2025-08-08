#!/bin/bash

set -e # Exit on any error

echo "🧹 Cleaning up..."
kubectl delete -f k8s/ --ignore-not-found=true

echo "⏳ Waiting a moment for cleanup..."
sleep 5

echo "🔧 Setting up Docker environment..."
eval "$(minikube docker-env --shell bash)"

echo "🏗️ Building application image..."
docker build -t homefinance:latest .

echo "🚀 Deploying to Kubernetes..."
kubectl apply -f k8s/

echo "⏳ Waiting for deployments..."
kubectl wait --for=condition=available deployment/mysql --timeout=300s
kubectl wait --for=condition=available deployment/homefinance --timeout=300s

echo "📊 Current status:"
kubectl get all

echo "🌐 Service URL:"
minikube service homefinance --url

echo "✅ Done!"