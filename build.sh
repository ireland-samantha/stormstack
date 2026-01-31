#!/bin/bash
set -e

# Build script - proxy to Maven commands
# Usage: ./build.sh [command]
#
# Commands:
#   clean            - Clean build artifacts
#   secrets          - Generate JWT keys (gen_secrets.py)
#   build            - Build all modules (skip tests)
#   test             - Run unit tests
#   frontend         - Install deps and build frontend
#   frontend-test    - Run frontend tests with coverage
#   integration-test - Build Docker image and run integration tests
#   docker           - Build Docker image only
#   compose-up       - Start docker-compose services
#   compose-down     - Stop docker-compose services
#   e2e-test         - Run lightning-cli e2e tests
#   all              - Full pipeline: secrets, frontend, build, test, integration-test

DOCKER_IMAGE_ENGINE="samanthacireland/thunder-engine"
DOCKER_IMAGE_AUTH="samanthacireland/thunder-auth"
DOCKER_IMAGE_CONTROL_PLANE="samanthacireland/thunder-control-plane"
DOCKER_TAG="latest"
VERSION="0.0.3-SNAPSHOT"
FRONTEND_DIR="lightning/webpanel"
# Tailscale IP - set via environment variable or detect automatically
if [ -z "$TAILSCALE_IP" ]; then
    TAILSCALE_IP=$(/Applications/Tailscale.app/Contents/MacOS/Tailscale ip -4 2>/dev/null || echo "")
fi
if [ -z "$TAILSCALE_IP" ]; then
    echo "Warning: TAILSCALE_IP not set and unable to detect. Set with: export TAILSCALE_IP=\$(tailscale ip -4)"
fi

LOCAL_REGISTRY_HOST="${TAILSCALE_IP:-$TAILSCALE_IP}"
LOCAL_REGISTRY_PORT="${LOCAL_REGISTRY_PORT:-5001}"
LOCAL_REGISTRY="${LOCAL_REGISTRY_HOST}:${LOCAL_REGISTRY_PORT}"
START_TIME=$(date +%s)

banner() {
    local msg="$1"
    echo ""
    echo "========================================"
    echo "  $msg"
    echo "========================================"
    echo ""
}

print_duration() {
    local end_time=$(date +%s)
    local duration=$((end_time - START_TIME))
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    banner "BUILD COMPLETE"
    if [ $minutes -gt 0 ]; then
        echo "Total time: ${minutes}m ${seconds}s"
    else
        echo "Total time: ${seconds}s"
    fi
    echo ""
}

print_usage() {
    echo "Usage: ./build.sh [command]"
    echo ""
    echo "Commands:"
    echo "  clean            - Clean build artifacts and secrets"
    echo "  secrets          - Generate JWT keys (gen_secrets.py)"
    echo "  build            - Build all modules (skip tests)"
    echo "  test             - Run unit tests"
    echo "  frontend         - Install deps and build frontend"
    echo "  frontend-test    - Run frontend tests with coverage"
    echo "  integration-test - Build Docker image and run integration tests"
    echo "  docker           - Build Docker image only"
    echo "  all              - Full pipeline: secrets, frontend, build, test, integration-test"
    echo ""
    echo "Docker Compose:"
    echo "  compose-up       - Start all docker-compose services"
    echo "  compose-down     - Stop all docker-compose services"
    echo ""
    echo "E2E Testing:"
    echo "  e2e-test         - Run lightning-cli e2e tests (starts/stops docker-compose)"
    echo ""
    echo "Local Registry (Tailscale):"
    echo "  registry-start   - Start local Docker registry on ${LOCAL_REGISTRY}"
    echo "  registry-stop    - Stop local Docker registry"
    echo "  registry-status  - Check registry status and list images"
    echo "  docker-local     - Build and push to local registry"
    echo ""
}

do_clean() {
    banner "CLEAN"
    mvn clean -q
    # Remove generated JWT keys
    rm -f thunder/engine/provider/src/main/resources/privateKey.pem
    rm -f thunder/engine/provider/src/main/resources/publicKey.pem
    rm -f thunder/engine/provider/src/test/resources/privateKey.pem
    rm -f thunder/engine/provider/src/test/resources/publicKey.pem
}

do_secrets() {
    banner "GENERATE SECRETS"
    python3 gen_secrets.py
}

do_build() {
    banner "BUILD"
    mvn install -DskipTests -q
}

do_test() {
    banner "TEST"
    mvn test
}

do_frontend() {
    banner "FRONTEND BUILD"
    (cd "${FRONTEND_DIR}" && npm install)
    (cd "${FRONTEND_DIR}" && npm run build)
}

do_frontend_test() {
    banner "FRONTEND TEST"
    (cd "${FRONTEND_DIR}" && npm install)
    (cd "${FRONTEND_DIR}" && npm run test:coverage)
}

do_docker() {
    banner "DOCKER BUILD"

    # Build Thunder Engine (main API)
    echo "Building thunder-engine..."
    docker build -f thunder/engine/provider/Dockerfile.prebuilt \
        -t "${DOCKER_IMAGE_ENGINE}:${DOCKER_TAG}" \
        -t "${DOCKER_IMAGE_ENGINE}:${VERSION}" \
        .
    echo "  Built: ${DOCKER_IMAGE_ENGINE}:${DOCKER_TAG}"

    # Build Thunder Auth
    echo "Building thunder-auth..."
    docker build -f thunder/auth/provider/Dockerfile.prebuilt \
        -t "${DOCKER_IMAGE_AUTH}:${DOCKER_TAG}" \
        -t "${DOCKER_IMAGE_AUTH}:${VERSION}" \
        .
    echo "  Built: ${DOCKER_IMAGE_AUTH}:${DOCKER_TAG}"

    # Build Thunder Control Plane
    echo "Building thunder-control-plane..."
    docker build -f thunder/control-plane/provider/Dockerfile.prebuilt \
        -t "${DOCKER_IMAGE_CONTROL_PLANE}:${DOCKER_TAG}" \
        -t "${DOCKER_IMAGE_CONTROL_PLANE}:${VERSION}" \
        .
    echo "  Built: ${DOCKER_IMAGE_CONTROL_PLANE}:${DOCKER_TAG}"

    echo ""
    echo "All Docker images built successfully:"
    echo "  - ${DOCKER_IMAGE_ENGINE}:${DOCKER_TAG}"
    echo "  - ${DOCKER_IMAGE_AUTH}:${DOCKER_TAG}"
    echo "  - ${DOCKER_IMAGE_CONTROL_PLANE}:${DOCKER_TAG}"
}

do_integration_test() {
    do_docker
    banner "INTEGRATION TEST"
    mvn verify -Pacceptance-tests -pl thunder/engine/tests/api-acceptance
}

do_registry_start() {
    banner "START LOCAL REGISTRY"
    docker compose -f docker-compose.registry.yml up -d
    echo ""
    echo "Registry started at ${LOCAL_REGISTRY}"
    echo ""
    echo "Available images (after ./build.sh docker-local):"
    echo "  - ${LOCAL_REGISTRY}/thunder-engine:${VERSION}"
    echo "  - ${LOCAL_REGISTRY}/thunder-auth:${VERSION}"
    echo "  - ${LOCAL_REGISTRY}/thunder-control-plane:${VERSION}"
    echo ""
    echo "IMPORTANT: On remote machines, add to /etc/docker/daemon.json:"
    echo "  { \"insecure-registries\": [\"${LOCAL_REGISTRY}\"] }"
    echo "Then: sudo systemctl restart docker"
}

do_registry_stop() {
    banner "STOP LOCAL REGISTRY"
    docker compose -f docker-compose.registry.yml down
}

do_registry_status() {
    banner "REGISTRY STATUS"
    if docker ps --format '{{.Names}}' | grep -q local-docker-registry; then
        echo "Registry is RUNNING at ${LOCAL_REGISTRY}"
        echo ""
        echo "Images in registry:"
        curl -s "http://${LOCAL_REGISTRY}/v2/_catalog" 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "  (empty or unable to query)"
    else
        echo "Registry is NOT RUNNING"
        echo "Start with: ./build.sh registry-start"
    fi
}

do_docker_local() {
    banner "BUILD & PUSH TO LOCAL REGISTRY"
    # Check if registry is running
    if ! docker ps --format '{{.Names}}' | grep -q local-docker-registry; then
        echo "Error: Local registry is not running"
        echo "Start with: ./build.sh registry-start"
        exit 1
    fi

    # Build and push all services via Maven profile
    echo "Building and pushing thunder-engine..."
    mvn install -PlocalDockerRegistry -pl thunder/engine/provider -DskipTests

    echo "Building and pushing thunder-auth..."
    mvn install -PlocalDockerRegistry -pl thunder/auth/provider -DskipTests

    echo "Building and pushing thunder-control-plane..."
    mvn install -PlocalDockerRegistry -pl thunder/control-plane/provider -DskipTests

    echo ""
    echo "All images pushed to local registry:"
    echo "  - ${LOCAL_REGISTRY}/thunder-engine:${VERSION}"
    echo "  - ${LOCAL_REGISTRY}/thunder-auth:${VERSION}"
    echo "  - ${LOCAL_REGISTRY}/thunder-control-plane:${VERSION}"
    echo ""
    echo "Pull from remote (via Tailscale):"
    echo "  docker pull ${LOCAL_REGISTRY}/thunder-engine:${VERSION}"
    echo "  docker pull ${LOCAL_REGISTRY}/thunder-auth:${VERSION}"
    echo "  docker pull ${LOCAL_REGISTRY}/thunder-control-plane:${VERSION}"
}

do_compose_up() {
    banner "DOCKER COMPOSE UP"
    docker compose up -d
    echo ""
    echo "Services started. Health status:"
    docker compose ps
    echo ""
    echo "To view logs: docker compose logs -f"
}

do_compose_down() {
    banner "DOCKER COMPOSE DOWN"
    docker compose down -v
    echo "All services stopped and volumes removed."
}

do_e2e_test() {
    banner "E2E TESTS (lightning-cli)"

    # Check if lightning CLI binary exists
    if [ ! -x "lightning/cli/lightning" ]; then
        echo "Building lightning CLI..."
        (cd lightning/cli && go build -o lightning ./cmd/lightning)
    fi

    # Run all e2e test scripts
    E2E_DIR="lightning/cli/e2e"
    if [ -d "$E2E_DIR" ]; then
        for script in "$E2E_DIR"/e2e-*.sh; do
            if [ -x "$script" ]; then
                echo ""
                echo "Running: $(basename "$script")"
                echo "----------------------------------------"
                "$script"
            fi
        done
    else
        echo "Error: E2E test directory not found: $E2E_DIR"
        exit 1
    fi
}

do_all() {
    do_clean
    do_secrets
    do_frontend
    do_frontend_test
    do_build
    do_test
    do_integration_test
}

# Main
case "${1:-}" in
    clean)
        do_clean
        print_duration
        ;;
    secrets)
        do_secrets
        print_duration
        ;;
    build)
        do_build
        print_duration
        ;;
    test)
        do_test
        print_duration
        ;;
    frontend)
        do_frontend
        print_duration
        ;;
    frontend-test)
        do_frontend_test
        print_duration
        ;;
    integration-test)
        do_integration_test
        print_duration
        ;;
    docker)
        do_docker
        print_duration
        ;;
    registry-start)
        do_registry_start
        ;;
    registry-stop)
        do_registry_stop
        ;;
    registry-status)
        do_registry_status
        ;;
    docker-local)
        do_docker_local
        print_duration
        ;;
    compose-up)
        do_compose_up
        ;;
    compose-down)
        do_compose_down
        ;;
    e2e-test)
        do_e2e_test
        print_duration
        ;;
    all)
        do_all
        print_duration
        ;;
    -h|--help|help)
        print_usage
        ;;
    "")
        echo "Error: No command specified"
        echo ""
        print_usage
        exit 1
        ;;
    *)
        echo "Error: Unknown command '${1}'"
        echo ""
        print_usage
        exit 1
        ;;
esac
