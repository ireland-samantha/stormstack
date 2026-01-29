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
#   all              - Full pipeline: secrets, frontend, build, test, integration-test

DOCKER_IMAGE="samanthacireland/lightning-engine"
DOCKER_TAG="latest"
FRONTEND_DIR="lightning-engine/webservice/quarkus-web-api/src/main/frontend"
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
    rm -f lightning-engine/webservice/quarkus-web-api/src/main/resources/privateKey.pem
    rm -f lightning-engine/webservice/quarkus-web-api/src/main/resources/publicKey.pem
    rm -f lightning-engine/webservice/quarkus-web-api/src/test/resources/privateKey.pem
    rm -f lightning-engine/webservice/quarkus-web-api/src/test/resources/publicKey.pem
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
    (cd "${FRONTEND_DIR}" && npm ci)
    (cd "${FRONTEND_DIR}" && npm run build)
}

do_frontend_test() {
    banner "FRONTEND TEST"
    (cd "${FRONTEND_DIR}" && npm ci)
    (cd "${FRONTEND_DIR}" && npm run test:coverage)
}

do_docker() {
    banner "DOCKER BUILD"
    docker build -f lightning-engine/webservice/quarkus-web-api/Dockerfile.prebuilt \
        -t "${DOCKER_IMAGE}:${DOCKER_TAG}" \
        -t "${DOCKER_IMAGE}:0.0.2" \
        .
    echo "Docker image built: ${DOCKER_IMAGE}:${DOCKER_TAG}"
}

do_integration_test() {
    do_docker
    banner "INTEGRATION TEST"
    mvn verify -Pacceptance-tests -pl lightning-engine/api-acceptance-test
}

do_registry_start() {
    banner "START LOCAL REGISTRY"
    docker compose -f docker-compose.registry.yml up -d
    echo ""
    echo "Registry started at ${LOCAL_REGISTRY}"
    echo "From AWS (via Tailscale): docker pull ${LOCAL_REGISTRY}/lightning-engine:0.0.2-SNAPSHOT"
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
    # Build and push via Maven profile
    mvn install -PlocalDockerRegistry -pl lightning-engine/webservice/quarkus-web-api -DskipTests
    echo ""
    echo "Image pushed to: ${LOCAL_REGISTRY}/lightning-engine:0.0.2-SNAPSHOT"
    echo "Pull from AWS: docker pull ${LOCAL_REGISTRY}/lightning-engine:0.0.2-SNAPSHOT"
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
