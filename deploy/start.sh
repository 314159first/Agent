#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-local}"

cd "$(dirname "$0")"

case "$MODE" in
  local)
    ENV_TEMPLATE=".env.local.example"
    PROFILES=(--profile localdb)
    ;;
  production)
    ENV_TEMPLATE=".env.production.example"
    PROFILES=(--profile localdb)
    ;;
  external)
    ENV_TEMPLATE=".env.external.example"
    PROFILES=()
    ;;
  tools)
    ENV_TEMPLATE=".env.local.example"
    PROFILES=(--profile localdb --profile tools)
    ;;
  *)
    echo "Usage: ./start.sh [local|production|external|tools]"
    exit 1
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed or not in PATH."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is not available."
  exit 1
fi

if [ ! -f .env ]; then
  cp "$ENV_TEMPLATE" .env
  echo "Created deploy/.env from $ENV_TEMPLATE. Review it before production use."
fi

echo "Starting AgentX in $MODE mode..."
docker compose "${PROFILES[@]}" up -d --build

echo
echo "AgentX is starting."
echo "Frontend: http://localhost:${FRONTEND_PORT:-3000}"
echo "Backend:  http://localhost:${BACKEND_PORT:-8088}/api/health"
echo "Logs:     docker compose logs -f"
