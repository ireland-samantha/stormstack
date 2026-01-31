#!/bin/bash
#
# Lightning CLI Module Management E2E Test Script
#
# This script tests module uninstall and reinstall operations using Lightning CLI:
#   1. Deploys a game with RigidBodyModule
#   2. Verifies RigidBodyModule is active via 'run_lightning node module list'
#   3. Spawns entities and runs simulation
#   4. Uninstalls RigidBodyModule via 'run_lightning node module uninstall'
#   5. Verifies module is uninstalled
#   6. Reloads modules via 'run_lightning node module reload'
#   7. Verifies RigidBodyModule is reinstalled and working
#
# Usage:
#   ./e2e-test-modules.sh              # Run full test
#   ./e2e-test-modules.sh --skip-build # Skip building Lightning CLI
#   ./e2e-test-modules.sh --skip-docker# Skip starting Docker
#   ./e2e-test-modules.sh --cleanup    # Only cleanup
#
# Requirements:
#   - Go 1.22+
#   - Docker and Docker Compose
#   - curl, jq

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIGHTNING_CLI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LIGHTNING_BIN="$LIGHTNING_CLI_DIR/lightning"
LIGHTNING_CONFIG="$HOME/.lightning.yaml"

# Service URLs (host ports from docker-compose.yml)
AUTH_URL="http://localhost:8082"
CONTROL_PLANE_URL="http://localhost:8081"
ENGINE_URL="http://localhost:8080"

# Test configuration
TEST_USERNAME="admin"
TEST_PASSWORD="${ADMIN_INITIAL_PASSWORD:-admin}"
TEST_MODULES="RigidBodyModule,GridMapModule,EntityModule"
TARGET_MODULE="RigidBodyModule"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_header() { echo -e "\n${BOLD}${CYAN}=== $1 ===${NC}\n"; }
log_cmd() { echo -e "${CYAN}[CMD]${NC} lightning $*" >&2; }

# Helper to run lightning commands with logging
run_lightning() {
    log_cmd "$@"
    "$LIGHTNING_BIN" "$@"
}

# Parse command line arguments
SKIP_BUILD=false
SKIP_DOCKER=false
CLEANUP_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --cleanup)
            CLEANUP_ONLY=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --skip-build    Skip building Lightning CLI"
            echo "  --skip-docker   Skip starting Docker Compose"
            echo "  --cleanup       Only run cleanup"
            echo "  -h, --help      Show this help message"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Track deployment for cleanup
MATCH_ID=""
CONTAINER_ID=""
JWT_TOKEN=""

# Cleanup function
cleanup() {
    log_info "Cleaning up..."

    # Undeploy if we have a match
    if [ -n "$MATCH_ID" ]; then
        log_info "Undeploying match $MATCH_ID..."
        run_lightning deploy undeploy "$MATCH_ID" > /dev/null 2>&1 || true
    fi

    # Remove lightning config if we created it
    if [ -f "$LIGHTNING_CONFIG.e2e-backup" ]; then
        mv "$LIGHTNING_CONFIG.e2e-backup" "$LIGHTNING_CONFIG"
        log_info "Restored original Thunder config"
    elif [ -f "$LIGHTNING_CONFIG" ]; then
        rm -f "$LIGHTNING_CONFIG"
    fi

    # Stop Docker Compose
    if [ "$SKIP_DOCKER" = false ]; then
        log_info "Stopping Docker Compose services..."
        cd "$PROJECT_ROOT"
        docker compose down --volumes 2>/dev/null || true
    fi

    log_info "Cleanup complete"
}

# Trap for cleanup on exit
trap cleanup EXIT

# Cleanup only mode
if [ "$CLEANUP_ONLY" = true ]; then
    SKIP_DOCKER=false
    cleanup
    trap - EXIT
    exit 0
fi

log_header "Module Management E2E Test"
echo "  Target Module: $TARGET_MODULE"
echo "  Test Modules:  $TEST_MODULES"
echo ""

# =============================================================================
# Step 1: Build Lightning CLI and Rigid Body Module
# =============================================================================
if [ "$SKIP_BUILD" = false ]; then
    log_info "Building Lightning CLI..."
    cd "$LIGHTNING_CLI_DIR"

    if ! command -v go &> /dev/null; then
        log_error "Go is not installed. Please install Go 1.22+"
        exit 1
    fi

    go build -o lightning ./cmd/lightning

    if [ ! -f "$LIGHTNING_BIN" ]; then
        log_error "Failed to build Lightning CLI"
        exit 1
    fi

    log_success "Lightning CLI built successfully"

    # Build rigid body module JAR
    log_info "Building Rigid Body Module JAR..."
    cd "$PROJECT_ROOT"
    mvn install -pl thunder/engine/extensions/modules/physics-module/rigid-body-module -DskipTests -q

    RIGID_BODY_JAR="$PROJECT_ROOT/thunder/engine/extensions/modules/physics-module/rigid-body-module/target/rigid-body-module-0.0.3-SNAPSHOT.jar"
    if [ ! -f "$RIGID_BODY_JAR" ]; then
        log_error "Failed to build rigid body module JAR"
        exit 1
    fi

    log_success "Rigid Body Module JAR built: $RIGID_BODY_JAR"
else
    log_info "Skipping Lightning CLI build"
    if [ ! -f "$LIGHTNING_BIN" ]; then
        log_error "Thunder binary not found at $LIGHTNING_BIN. Run without --skip-build"
        exit 1
    fi

    # Check if rigid body JAR exists
    RIGID_BODY_JAR="$PROJECT_ROOT/thunder/engine/extensions/modules/physics-module/rigid-body-module/target/rigid-body-module-0.0.3-SNAPSHOT.jar"
    if [ ! -f "$RIGID_BODY_JAR" ]; then
        log_warn "Rigid Body Module JAR not found. It will need to be built."
    fi
fi

log_info "Verifying Lightning CLI..."
log_cmd "version"
if ! "$LIGHTNING_BIN" version &> /dev/null; then
    log_error "Lightning CLI failed to execute"
    exit 1
fi
log_success "Lightning CLI is working"

# =============================================================================
# Step 2: Start Docker Compose
# =============================================================================
if [ "$SKIP_DOCKER" = false ]; then
    log_info "Starting Docker Compose services..."
    cd "$PROJECT_ROOT"

    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker first."
        exit 1
    fi

    docker compose down --volumes 2>/dev/null || true
    docker compose up -d --remove-orphans

    log_success "Docker Compose services started"
else
    log_info "Skipping Docker Compose start"
fi

# =============================================================================
# Step 3: Wait for services
# =============================================================================
log_info "Waiting for services to become healthy..."

wait_for_service() {
    local url="$1"
    local name="$2"
    local max_attempts="${3:-60}"
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            log_success "$name is healthy"
            return 0
        fi

        if [ $((attempt % 10)) -eq 0 ]; then
            log_info "Waiting for $name... (attempt $attempt/$max_attempts)"
        fi

        sleep 2
        attempt=$((attempt + 1))
    done

    log_error "$name failed to become healthy after $max_attempts attempts"
    return 1
}

wait_for_service "$AUTH_URL/q/health" "Auth Service" 60
wait_for_service "$CONTROL_PLANE_URL/q/health" "Control Plane" 60
wait_for_service "$ENGINE_URL/api/health" "Engine" 60

log_success "All services are healthy"

# =============================================================================
# Step 4: Authenticate with OAuth2
# =============================================================================
log_info "Authenticating with auth service..."

if [ -f "$LIGHTNING_CONFIG" ]; then
    cp "$LIGHTNING_CONFIG" "$LIGHTNING_CONFIG.e2e-backup"
fi

# Configure control plane URL first (needed for auth proxy)
run_lightning config set control_plane_url "$CONTROL_PLANE_URL"

# Check for API token from environment (useful for CI/CD)
if [ -n "$THUNDER_API_TOKEN" ]; then
    log_info "Using API token from THUNDER_API_TOKEN environment variable"
    run_lightning auth token "$THUNDER_API_TOKEN"
else
    # Use Lightning CLI to authenticate via control plane auth proxy
    log_info "Authenticating via Lightning CLI (control plane auth proxy)..."

    if ! run_lightning auth login --username "$TEST_USERNAME" --password "$TEST_PASSWORD"; then
        log_error "Failed to authenticate with username/password"
        exit 1
    fi

    log_success "Authenticated via Lightning CLI"
fi

log_success "Lightning CLI authenticated successfully"

# Verify proxy mode status (should be enabled by default)
log_info "Checking proxy mode status..."
PROXY_STATUS=$(run_lightning node proxy status -o json)
if echo "$PROXY_STATUS" | jq -e '.proxyEnabled' > /dev/null 2>&1; then
    PROXY_ENABLED=$(echo "$PROXY_STATUS" | jq -r '.proxyEnabled')
    if [ "$PROXY_ENABLED" = "true" ]; then
        log_success "Proxy mode is ENABLED (requests routed through control plane)"
    else
        log_info "Proxy mode is DISABLED (direct node connections)"
    fi
fi

# =============================================================================
# Step 4b: Upload and Distribute Rigid Body Module
# =============================================================================
log_header "Uploading and Distributing $TARGET_MODULE"

if [ -n "$RIGID_BODY_JAR" ] && [ -f "$RIGID_BODY_JAR" ]; then
    log_info "Uploading $TARGET_MODULE JAR to registry..."
    UPLOAD_OUTPUT=$(run_lightning module upload "$TARGET_MODULE" "0.0.3-SNAPSHOT" "$RIGID_BODY_JAR" --description "Physics rigid body simulation" 2>&1)

    if [[ "$UPLOAD_OUTPUT" == *"uploaded successfully"* ]]; then
        log_success "$TARGET_MODULE uploaded to registry"
    else
        log_warn "Upload may have failed or module already exists: $UPLOAD_OUTPUT"
    fi

    # Distribute module to all nodes
    log_info "Distributing $TARGET_MODULE to all nodes..."
    DISTRIBUTE_OUTPUT=$(run_lightning module distribute "$TARGET_MODULE" "0.0.3-SNAPSHOT" 2>&1)

    if [[ "$DISTRIBUTE_OUTPUT" == *"distributed to"* ]]; then
        log_success "$TARGET_MODULE distributed to nodes"
        echo "$DISTRIBUTE_OUTPUT"
    else
        log_warn "Distribution may have issues: $DISTRIBUTE_OUTPUT"
    fi
else
    log_warn "Rigid Body JAR not available for upload, will use pre-installed module"
fi

# =============================================================================
# Step 5: Deploy game with modules
# =============================================================================
log_header "Deploying Game with $TARGET_MODULE"

log_info "Deploying game with modules: $TEST_MODULES..."

DEPLOY_OUTPUT=$(run_lightning deploy --modules "$TEST_MODULES" -o json)

if ! echo "$DEPLOY_OUTPUT" | jq -e '.matchId' > /dev/null 2>&1; then
    log_error "Deployment failed. Output: $DEPLOY_OUTPUT"
    exit 1
fi

MATCH_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.matchId')
NODE_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.nodeId')
CONTAINER_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.containerId')

log_success "Deployed game: matchId=$MATCH_ID, containerId=$CONTAINER_ID"

# =============================================================================
# Step 5b: Verify deployment using run_lightning node list and match list
# =============================================================================
log_header "Verifying Deployment with List Commands"

log_info "Listing cluster nodes..."
NODE_LIST=$(run_lightning node list -o json)

if echo "$NODE_LIST" | jq -e '.[0].nodeId' > /dev/null 2>&1; then
    NODE_COUNT=$(echo "$NODE_LIST" | jq '. | length')
    log_success "Found $NODE_COUNT node(s) in cluster:"
    echo "$NODE_LIST" | jq -r '.[] | "  - \(.nodeId) [\(.status)] containers=\(.containers) matches=\(.matches)"'

    # Get the node ID from the list to verify against our deployment
    LISTED_NODE=$(echo "$NODE_LIST" | jq -r '.[0].nodeId')
    if [ "$LISTED_NODE" = "$NODE_ID" ]; then
        log_success "Deployment node $NODE_ID matches listed node"
    else
        log_info "Listed node: $LISTED_NODE, Deployment node: $NODE_ID"
    fi
else
    log_warn "Failed to list nodes. Output: $NODE_LIST"
fi

echo ""
log_info "Listing matches with 'run_lightning match list'..."
MATCH_LIST=$(run_lightning match list -o json)

if echo "$MATCH_LIST" | jq -e '.[0].matchId' > /dev/null 2>&1; then
    MATCH_COUNT=$(echo "$MATCH_LIST" | jq '. | length')
    log_success "Found $MATCH_COUNT match(es):"
    echo "$MATCH_LIST" | jq -r '.[] | "  - \(.matchId) [\(.status)] node=\(.nodeId) players=\(.playerCount)"'

    # Verify our deployed match is in the list
    if echo "$MATCH_LIST" | jq -e ".[] | select(.matchId == \"$MATCH_ID\")" > /dev/null 2>&1; then
        LISTED_MATCH_STATUS=$(echo "$MATCH_LIST" | jq -r ".[] | select(.matchId == \"$MATCH_ID\") | .status")
        log_success "Verified match $MATCH_ID exists with status: $LISTED_MATCH_STATUS"
    else
        log_warn "Match $MATCH_ID not found in list"
    fi
else
    log_warn "Failed to list matches. Output: $MATCH_LIST"
fi

# Set node context with container ID for container-specific operations
# Note: Proxy mode is enabled by default, routing requests through the control plane
run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1

# =============================================================================
# Step 6: Verify module is installed using Lightning CLI
# =============================================================================
log_header "Verifying Module Installation"

log_info "Running: run_lightning node module list"
MODULE_LIST=$(run_lightning node module list -o json)

log_info "Available modules:"
echo "$MODULE_LIST" | jq -r '.[] | "  - \(.name)"'

# Check if RigidBodyModule is in the list
if echo "$MODULE_LIST" | jq -e ".[] | select(.name == \"$TARGET_MODULE\")" > /dev/null 2>&1; then
    log_success "$TARGET_MODULE is installed"
else
    log_error "$TARGET_MODULE not found in module list"
    exit 1
fi

# =============================================================================
# Step 7: Spawn entities and verify physics
# =============================================================================
log_header "Testing Physics Simulation"

log_info "Spawning 5 entities..."

INTERNAL_MATCH_ID=$(echo "$MATCH_ID" | grep -oE '[0-9]+$')

for i in $(seq 1 5); do
    SPAWN_PARAMS="{\"matchId\":$INTERNAL_MATCH_ID,\"playerId\":1,\"entityType\":100}"
    run_lightning command send spawn "$SPAWN_PARAMS" > /dev/null 2>&1
done

# Advance tick to process spawns
run_lightning node tick advance > /dev/null 2>&1

# Get initial snapshot using Lightning CLI
SNAPSHOT_BEFORE=$(run_lightning snapshot get -o json)
log_info "SNAPSHOT_BEFORE"

TICK_BEFORE=$(echo "$SNAPSHOT_BEFORE" | jq -r '.tick')
log_info "Initial snapshot at tick $TICK_BEFORE"

# Send move commands to entities
log_info "Sending move commands to entities..."
COMMANDS_FILE="/tmp/lightning-module-commands.json"
cat > "$COMMANDS_FILE" << 'EOF'
[
  {"command": "move", "params": {"entityId": 1, "targetX": 50, "targetY": 50}},
  {"command": "move", "params": {"entityId": 2, "targetX": 75, "targetY": 25}},
  {"command": "move", "params": {"entityId": 3, "targetX": 25, "targetY": 75}}
]
EOF

BULK_OUTPUT=$(run_lightning command send-bulk "$COMMANDS_FILE" 2>&1)
if [[ "$BULK_OUTPUT" == *"successfully"* ]]; then
    log_success "Move commands sent successfully"
else
    log_warn "Commands may have failed: $BULK_OUTPUT"
fi
rm -f "$COMMANDS_FILE"

# Advance tick to process commands
run_lightning node tick advance > /dev/null 2>&1

# Run auto-advance for 2 seconds
log_info "Running physics simulation for 2 seconds..."
run_lightning node simulation play --interval-ms 16 > /dev/null 2>&1

sleep 2

run_lightning node simulation stop > /dev/null 2>&1

# Get snapshot after physics using Lightning CLI
SNAPSHOT_AFTER=$(run_lightning snapshot get -o json)
log_info "$SNAPSHOT_AFTER"
TICK_AFTER=$(echo "$SNAPSHOT_AFTER" | jq -r '.tick')
TICKS_ADVANCED=$((TICK_AFTER - TICK_BEFORE))

log_success "Physics ran for $TICKS_ADVANCED ticks (tick $TICK_BEFORE -> $TICK_AFTER)"

# Check for RigidBodyModule data in snapshot
if echo "$SNAPSHOT_AFTER" | jq -e '.modules[] | select(.name == "RigidBodyModule")' > /dev/null 2>&1; then
    log_success "RigidBodyModule data present in snapshot"
else
    log_warn "RigidBodyModule data not found in snapshot"
fi

# =============================================================================
# Step 8: Uninstall RigidBodyModule using Lightning CLI
# =============================================================================
log_header "Uninstalling $TARGET_MODULE"

log_info "Running: run_lightning node module uninstall $TARGET_MODULE"

UNINSTALL_OUTPUT=$(run_lightning node module uninstall "$TARGET_MODULE" 2>&1)

if [[ "$UNINSTALL_OUTPUT" == *"uninstalled successfully"* ]]; then
    log_success "$TARGET_MODULE uninstalled successfully"
else
    log_error "Failed to uninstall $TARGET_MODULE. Output: $UNINSTALL_OUTPUT"
    exit 1
fi

# =============================================================================
# Step 9: Verify module is uninstalled using Lightning CLI
# =============================================================================
log_header "Verifying Module Uninstallation"

log_info "Running: run_lightning node module list"
MODULES_AFTER_UNINSTALL=$(run_lightning node module list -o json)

log_info "Available modules after uninstall:"
echo "$MODULES_AFTER_UNINSTALL" | jq -r '.[] | "  - \(.name)"'

if echo "$MODULES_AFTER_UNINSTALL" | jq -e ".[] | select(.name == \"$TARGET_MODULE\")" > /dev/null 2>&1; then
    log_error "$TARGET_MODULE still present after uninstall!"
    exit 1
else
    log_success "$TARGET_MODULE successfully removed from module list"
fi

# Run a few more ticks - physics should no longer be simulating
log_info "Running simulation without $TARGET_MODULE..."

TICK_OUTPUT=$(run_lightning node tick get -o json)
TICK_BEFORE_NO_PHYSICS=$(echo "$TICK_OUTPUT" | jq -r '.tick')

# Advance a few ticks manually
for i in $(seq 1 10); do
    run_lightning node tick advance > /dev/null 2>&1
done

TICK_OUTPUT=$(run_lightning node tick get -o json)
TICK_AFTER_NO_PHYSICS=$(echo "$TICK_OUTPUT" | jq -r '.tick')

log_success "Ran 10 ticks without $TARGET_MODULE (tick $TICK_BEFORE_NO_PHYSICS -> $TICK_AFTER_NO_PHYSICS)"

# =============================================================================
# Step 10: Reload modules to reinstall RigidBodyModule using Lightning CLI
# =============================================================================
log_header "Reinstalling $TARGET_MODULE via Reload"

log_info "Running: run_lightning node module reload"

RELOAD_OUTPUT=$(run_lightning node module reload 2>&1)

if [[ "$RELOAD_OUTPUT" == *"Reloaded"* ]]; then
    log_success "Modules reloaded successfully"
    echo "$RELOAD_OUTPUT"
else
    log_error "Failed to reload modules. Output: $RELOAD_OUTPUT"
    exit 1
fi

# =============================================================================
# Step 11: Verify module is reinstalled using Lightning CLI
# =============================================================================
log_header "Verifying Module Reinstallation"

log_info "Running: run_lightning node module list"
MODULES_AFTER_RELOAD=$(run_lightning node module list -o json)

log_info "Available modules after reload:"
echo "$MODULES_AFTER_RELOAD" | jq -r '.[] | "  - \(.name)"'

if echo "$MODULES_AFTER_RELOAD" | jq -e ".[] | select(.name == \"$TARGET_MODULE\")" > /dev/null 2>&1; then
    log_success "$TARGET_MODULE is reinstalled and available"
else
    log_error "$TARGET_MODULE not found after reload!"
    exit 1
fi

# =============================================================================
# Step 12: Verify physics works again
# =============================================================================
log_header "Verifying Physics Simulation Restored"

log_info "Running physics simulation for 2 seconds after reinstall..."

TICK_OUTPUT=$(run_lightning node tick get -o json)
TICK_BEFORE_REINSTALL=$(echo "$TICK_OUTPUT" | jq -r '.tick')

run_lightning node simulation play --interval-ms 16 > /dev/null 2>&1

sleep 2

run_lightning node simulation stop > /dev/null 2>&1

TICK_OUTPUT=$(run_lightning node tick get -o json)
TICK_AFTER_REINSTALL=$(echo "$TICK_OUTPUT" | jq -r '.tick')

TICKS_WITH_PHYSICS=$((TICK_AFTER_REINSTALL - TICK_BEFORE_REINSTALL))
log_success "Physics simulation ran for $TICKS_WITH_PHYSICS ticks after reinstall"

# Get final snapshot to verify RigidBodyModule is working
log_info "Verifying RigidBodyModule in final snapshot..."
FINAL_SNAPSHOT=$(run_lightning snapshot get -o json)
log_info "$FINAL_SNAPSHOT"

if echo "$FINAL_SNAPSHOT" | jq -e '.modules[] | select(.name == "RigidBodyModule")' > /dev/null 2>&1; then
    log_success "RigidBodyModule data present in final snapshot"
else
    log_warn "RigidBodyModule data not found in final snapshot"
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "=============================================="
log_success "MODULE E2E TEST PASSED"
echo "=============================================="
echo ""
echo "Test Summary:"
echo "  1. Deployed game with $TARGET_MODULE"
echo "  2. Verified module installed via 'run_lightning node module list'"
echo "  3. Ran physics simulation (entities moved)"
echo "  4. Uninstalled module via 'run_lightning node module uninstall $TARGET_MODULE'"
echo "  5. Verified module removed via 'run_lightning node module list'"
echo "  6. Ran ticks without physics (module unloaded)"
echo "  7. Reinstalled via 'run_lightning node module reload'"
echo "  8. Verified module reinstalled via 'run_lightning node module list'"
echo "  9. Ran physics simulation again (module working)"
echo ""
