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
