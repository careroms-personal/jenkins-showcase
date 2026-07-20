#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

#1. Run kind command with config at kind/jenkins-showcase.yaml
mkdir -p ~/Projects/jenkins-showcase-volume/argocd-repo-server
mkdir -p ~/Projects/jenkins-showcase-volume/jenkins-home
kind create cluster --config "${SCRIPT_DIR}/kind/jenkins-showcase.yaml"

#2. Run kubectl config current-context jenkins-showcase
kubectl config use-context kind-jenkins-showcase

#3. Run helm install argocd in argocd folder
helm install argocd "${SCRIPT_DIR}/argocd" --namespace argocd --create-namespace --values "${SCRIPT_DIR}/argocd/kind-values.yaml"
