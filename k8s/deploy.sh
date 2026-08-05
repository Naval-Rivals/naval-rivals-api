#!/bin/bash
set -e

echo "🚀 Criando cluster Kind..."
kind create cluster --config k8s/kind.yaml

echo "📦 Instalando Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

echo "🔧 Configurando Ingress Controller no control-plane..."
kubectl patch deployment ingress-nginx-controller -n ingress-nginx --type=json -p='[
  {"op": "add", "path": "/spec/template/spec/tolerations", "value": [{"key": "node-role.kubernetes.io/control-plane", "operator": "Exists", "effect": "NoSchedule"}]},
  {"op": "replace", "path": "/spec/template/spec/nodeSelector", "value": {"kubernetes.io/os": "linux", "ingress-ready": "true"}}
]'
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

echo "⚓ Aplicando manifestos Naval Rivals..."
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

echo "⏳ Aguardando pods ficarem prontos..."
kubectl wait --namespace navalrivals --for=condition=ready pod --selector=app=navalrivals-api --timeout=120s

echo ""
echo "✅ Naval Rivals API rodando com 2 réplicas!"
echo "🌐 Acesse: http://localhost"
echo ""
kubectl get pods -n navalrivals
