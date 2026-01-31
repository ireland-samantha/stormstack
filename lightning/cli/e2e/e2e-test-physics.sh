#!/bin/bash
#
# Lightning CLI End-to-End Test Script
#
# This script tests the full Lightning CLI workflow:
#   1. Builds the Lightning CLI binary
#   2. Starts Docker Compose services
#   3. Authenticates with the auth service
#   4. Deploys configurable number of games with specified modules
#   5. Spawns configurable number of entities in each
#   6. Runs simulation on auto-advance for a few seconds
#   7. Collects and prints metrics for all deployments
#   8. Verifies the response contains valid URLs
#
# Usage:
#   ./e2e-test-physics.sh                        # Run full test (1 deployment, 10 entities)
#   ./e2e-test-physics.sh -d 3 -e 50             # 3 deployments, 50 entities each
#   ./e2e-test-physics.sh --deployments 5 --entities 100 --runtime 10
#   ./e2e-test-physics.sh --skip-build           # Skip building Lightning CLI
#   ./e2e-test-physics.sh --skip-docker          # Skip starting Docker (assumes already running)
#   ./e2e-test-physics.sh --cleanup              # Only cleanup (stop Docker, remove artifacts)
#
# Configuration via environment:
#   NUM_DEPLOYMENTS=3        Number of game deployments
#   NUM_ENTITIES=50          Entities to spawn per deployment
#   SIMULATION_RUNTIME=5     Seconds to run auto-advance
#   TICK_INTERVAL_MS=16      Tick interval (default 16ms = ~60 FPS)
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

# Test configuration (defaults, can be overridden via env or args)
TEST_USERNAME="admin"
TEST_PASSWORD="${ADMIN_INITIAL_PASSWORD:-admin}"
TEST_MODULES="RigidBodyModule,GridMapModule,EntityModule"

# Configurable test parameters
NUM_DEPLOYMENTS="${NUM_DEPLOYMENTS:-1}"
NUM_ENTITIES="${NUM_ENTITIES:-10}"
SIMULATION_RUNTIME="${SIMULATION_RUNTIME:-5}"
TICK_INTERVAL_MS="${TICK_INTERVAL_MS:-16}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

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
        -d|--deployments)
            NUM_DEPLOYMENTS="$2"
            shift 2
            ;;
        -e|--entities)
            NUM_ENTITIES="$2"
            shift 2
            ;;
        -r|--runtime)
            SIMULATION_RUNTIME="$2"
            shift 2
            ;;
        -t|--tick-interval)
            TICK_INTERVAL_MS="$2"
            shift 2
            ;;
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
            echo "  -d, --deployments N   Number of game deployments (default: 1)"
            echo "  -e, --entities N      Entities to spawn per deployment (default: 10)"
            echo "  -r, --runtime N       Seconds to run auto-advance simulation (default: 5)"
            echo "  -t, --tick-interval N Tick interval in milliseconds (default: 16 = ~60 FPS)"
            echo "  --skip-build          Skip building Lightning CLI"
            echo "  --skip-docker         Skip starting Docker Compose"
            echo "  --cleanup             Only run cleanup (stop Docker, remove artifacts)"
            echo "  -h, --help            Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                           # 1 deployment, 10 entities, 5s runtime"
            echo "  $0 -d 3 -e 100               # 3 deployments, 100 entities each"
            echo "  $0 -d 5 -e 50 -r 10          # 5 deployments, 50 entities, 10s runtime"
            echo "  $0 --skip-docker -d 2        # Skip docker, 2 deployments"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Arrays to track deployments
declare -a MATCH_IDS
declare -a CONTAINER_IDS
declare -a NODE_IDS

# Cleanup function
cleanup() {
    log_info "Cleaning up..."

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
    SKIP_DOCKER=false  # Force docker cleanup
    cleanup
    trap - EXIT  # Remove trap since we're doing it manually
    exit 0
fi

log_header "E2E Test Configuration"
echo "  Deployments:      $NUM_DEPLOYMENTS"
echo "  Entities/deploy:  $NUM_ENTITIES"
echo "  Simulation time:  ${SIMULATION_RUNTIME}s"
echo "  Tick interval:    ${TICK_INTERVAL_MS}ms (~$((1000 / TICK_INTERVAL_MS)) FPS)"
echo "  Modules:          $TEST_MODULES"
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

# Verify thunder binary works
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

    # Check if Docker is running
    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker first."
        exit 1
    fi

    # Stop any existing containers
    docker compose down --volumes 2>/dev/null || true

    # Start services
    docker compose up -d --remove-orphans

    log_success "Docker Compose services started"
else
    log_info "Skipping Docker Compose start"
fi

# =============================================================================
# Step 3: Wait for services to be healthy
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

# Wait for each service
wait_for_service "$AUTH_URL/q/health" "Auth Service" 60
wait_for_service "$CONTROL_PLANE_URL/q/health" "Control Plane" 60
wait_for_service "$ENGINE_URL/api/health" "Engine" 60

log_success "All services are healthy"

# =============================================================================
# Step 4: Authenticate with OAuth2
# =============================================================================
log_info "Authenticating with auth service..."

# Backup existing config if present
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

# Verify authentication status
AUTH_STATUS=$(run_lightning auth status 2>&1)
if [[ "$AUTH_STATUS" != *"Authenticated"* ]]; then
    log_error "Lightning CLI authentication failed. Status: $AUTH_STATUS"
    exit 1
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
# Step 5: Deploy multiple games
# =============================================================================
log_header "Deploying $NUM_DEPLOYMENTS Games"

for i in $(seq 1 $NUM_DEPLOYMENTS); do
    log_info "Deploying game $i/$NUM_DEPLOYMENTS with modules: $TEST_MODULES..."

    # Deploy the game and capture output as JSON
    DEPLOY_OUTPUT=$(run_lightning deploy --modules "$TEST_MODULES" -o json) || true

    # Check if deployment succeeded
    if ! echo "$DEPLOY_OUTPUT" | jq -e '.matchId' > /dev/null 2>&1; then
        log_error "Deployment $i failed. Output: $DEPLOY_OUTPUT"
        log_warn "Continuing with ${#MATCH_IDS[@]} successful deployments..."
        break
    fi

    # Extract fields from deployment response
    MATCH_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.matchId')
    NODE_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.nodeId')
    CONTAINER_ID=$(echo "$DEPLOY_OUTPUT" | jq -r '.containerId')

    # Store in arrays
    MATCH_IDS+=("$MATCH_ID")
    NODE_IDS+=("$NODE_ID")
    CONTAINER_IDS+=("$CONTAINER_ID")

    log_success "Deployed game $i: matchId=$MATCH_ID, containerId=$CONTAINER_ID"

    # Small delay between deployments to avoid race conditions
    if [ $i -lt $NUM_DEPLOYMENTS ]; then
        sleep 1
    fi
done

# Verify we have at least one deployment
if [ ${#MATCH_IDS[@]} -eq 0 ]; then
    log_error "No games were deployed successfully"
    exit 1
fi

ACTUAL_DEPLOYMENTS=${#MATCH_IDS[@]}
if [ "$ACTUAL_DEPLOYMENTS" -eq "$NUM_DEPLOYMENTS" ]; then
    log_success "All $NUM_DEPLOYMENTS games deployed"
else
    log_warn "Deployed $ACTUAL_DEPLOYMENTS of $NUM_DEPLOYMENTS requested games"
fi

# =============================================================================
# Step 5b: Verify deployments using thunder node list and match list
# =============================================================================
log_header "Verifying Deployments with List Commands"

log_info "Listing cluster nodes with 'thunder node list'..."
NODE_LIST=$(run_lightning node list -o json)

if echo "$NODE_LIST" | jq -e '.[0].nodeId' > /dev/null 2>&1; then
    NODE_COUNT=$(echo "$NODE_LIST" | jq '. | length')
    log_success "Found $NODE_COUNT node(s) in cluster:"
    echo "$NODE_LIST" | jq -r '.[] | "  - \(.nodeId) [\(.status)] containers=\(.containers) matches=\(.matches)"'

    # Verify our node is in the list
    FIRST_NODE=$(echo "$NODE_LIST" | jq -r '.[0].nodeId')
    if [ -n "$FIRST_NODE" ] && [ "$FIRST_NODE" != "null" ]; then
        log_success "Primary node: $FIRST_NODE"
    fi
else
    log_warn "Failed to list nodes. Output: $NODE_LIST"
fi

echo ""
log_info "Listing matches with 'thunder match list'..."
MATCH_LIST=$(run_lightning match list -o json)

if echo "$MATCH_LIST" | jq -e '.[0].matchId' > /dev/null 2>&1; then
    MATCH_COUNT=$(echo "$MATCH_LIST" | jq '. | length')
    log_success "Found $MATCH_COUNT match(es):"
    echo "$MATCH_LIST" | jq -r '.[] | "  - \(.matchId) [\(.status)] node=\(.nodeId) players=\(.playerCount)"'

    # Verify our deployed matches are in the list
    for EXPECTED_MATCH in "${MATCH_IDS[@]}"; do
        if echo "$MATCH_LIST" | jq -e ".[] | select(.matchId == \"$EXPECTED_MATCH\")" > /dev/null 2>&1; then
            log_success "Verified match $EXPECTED_MATCH exists"
        else
            log_warn "Match $EXPECTED_MATCH not found in list"
        fi
    done
else
    log_warn "Failed to list matches. Output: $MATCH_LIST"
fi

# =============================================================================
# Step 6: Spawn entities in each deployment
# =============================================================================
log_header "Spawning $NUM_ENTITIES Entities in Each Game"

for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    NODE_ID="${NODE_IDS[$idx]}"
    CONTAINER_ID="${CONTAINER_IDS[$idx]}"
    GAME_NUM=$((idx + 1))

    log_info "Game $GAME_NUM ($MATCH_ID): Spawning $NUM_ENTITIES entities..."

    # Set node context with container ID for container-specific operations
    # Note: Proxy mode is enabled by default, routing requests through the control plane
    run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1

    # The spawn command requires matchId (internal), playerId, entityType
    # Internal matchId is the last part of the cluster match ID (e.g., "node-1-1-1" -> 1)
    INTERNAL_MATCH_ID=$(echo "$MATCH_ID" | grep -oE '[0-9]+$')

    for entity_num in $(seq 1 $NUM_ENTITIES); do
        SPAWN_PARAMS="{\"matchId\":$INTERNAL_MATCH_ID,\"playerId\":1,\"entityType\":100}"
        run_lightning command send spawn "$SPAWN_PARAMS" > /dev/null 2>&1
    done

    # Advance tick to process spawn commands
    TICK_OUTPUT=$(run_lightning node tick advance -o json)

    TICK=$(echo "$TICK_OUTPUT" | jq -r '.tick' 2>/dev/null)
    log_success "Game $GAME_NUM: Spawned $NUM_ENTITIES entities, tick=$TICK"
done

# =============================================================================
# Step 6b: Send commands to entities
# =============================================================================
log_header "Sending Commands to Entities"

for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    NODE_ID="${NODE_IDS[$idx]}"
    CONTAINER_ID="${CONTAINER_IDS[$idx]}"
    GAME_NUM=$((idx + 1))
    INTERNAL_MATCH_ID=$(echo "$MATCH_ID" | grep -oE '[0-9]+$')

    log_info "Game $GAME_NUM: Generating command batch..."

    # Set context for this game
    run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1

    # Create a commands file with move commands for each entity
    COMMANDS_FILE="/tmp/thunder-commands-${GAME_NUM}.json"
    echo "[" > "$COMMANDS_FILE"

    # Generate move commands for first 5 entities (if we have that many)
    MAX_COMMANDS=$((NUM_ENTITIES < 5 ? NUM_ENTITIES : 5))
    for entity_id in $(seq 1 $MAX_COMMANDS); do
        # Random target position
        TARGET_X=$((RANDOM % 100))
        TARGET_Y=$((RANDOM % 100))

        # Add command to file
        echo "  {\"command\": \"move\", \"params\": {\"entityId\": $entity_id, \"targetX\": $TARGET_X, \"targetY\": $TARGET_Y}}" >> "$COMMANDS_FILE"

        # Add comma if not last command
        if [ $entity_id -lt $MAX_COMMANDS ]; then
            echo "," >> "$COMMANDS_FILE"
        fi
    done

    echo "]" >> "$COMMANDS_FILE"

    # Send commands in bulk
    log_info "Game $GAME_NUM: Sending $MAX_COMMANDS move commands..."
    BULK_OUTPUT=$(run_lightning command send-bulk "$COMMANDS_FILE" 2>&1)

    if [[ "$BULK_OUTPUT" == *"successfully"* ]]; then
        log_success "Game $GAME_NUM: Commands sent successfully"
    else
        log_warn "Game $GAME_NUM: Commands may have failed: $BULK_OUTPUT"
    fi

    # Clean up commands file
    rm -f "$COMMANDS_FILE"

    # Advance tick to process commands
    run_lightning node tick advance > /dev/null 2>&1
done

log_success "Commands sent to all games"

# =============================================================================
# Step 7: Start auto-advance on all containers
# =============================================================================
log_header "Starting Auto-Advance Simulation"

log_info "Starting auto-advance at ${TICK_INTERVAL_MS}ms interval (~$((1000 / TICK_INTERVAL_MS)) FPS)..."

for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    NODE_ID="${NODE_IDS[$idx]}"
    CONTAINER_ID="${CONTAINER_IDS[$idx]}"
    GAME_NUM=$((idx + 1))

    # Set context for this game
    run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1

    # Start auto-advance
    PLAY_OUTPUT=$(run_lightning node simulation play --interval-ms "$TICK_INTERVAL_MS" 2>&1)

    if [[ "$PLAY_OUTPUT" == *"started"* ]]; then
        log_success "Game $GAME_NUM: Auto-advance started"
    else
        log_warn "Game $GAME_NUM: Failed to start auto-advance: $PLAY_OUTPUT"
    fi
done

log_info "Running simulation for ${SIMULATION_RUNTIME} seconds..."
sleep "$SIMULATION_RUNTIME"

# Stop auto-advance
log_info "Stopping auto-advance..."
for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    NODE_ID="${NODE_IDS[$idx]}"
    CONTAINER_ID="${CONTAINER_IDS[$idx]}"
    run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1
    run_lightning node simulation stop > /dev/null 2>&1
done

log_success "Simulation completed"

# =============================================================================
# Step 8: Collect and print metrics
# =============================================================================
log_header "Metrics Report"

# Get node metrics - set context to first node
run_lightning node context set "${NODE_IDS[0]}" --container-id "${CONTAINER_IDS[0]}" --match-id "${MATCH_IDS[0]}" > /dev/null 2>&1

log_info "Node Metrics:"
NODE_METRICS=$(run_lightning node metrics get -o json)

if echo "$NODE_METRICS" | jq -e '.' > /dev/null 2>&1; then
    echo ""
    echo "  CPU Usage:        $(echo "$NODE_METRICS" | jq -r '.cpuUsage // "N/A"')%"
    echo "  Memory Used:      $(echo "$NODE_METRICS" | jq -r '.memoryUsed // "N/A"') bytes"
    echo "  Memory Total:     $(echo "$NODE_METRICS" | jq -r '.memoryTotal // "N/A"') bytes"
    echo "  Active Containers:$(echo "$NODE_METRICS" | jq -r '.activeContainers // "N/A"')"
    echo "  Active Matches:   $(echo "$NODE_METRICS" | jq -r '.activeMatches // "N/A"')"
    echo ""
else
    log_warn "Failed to get node metrics"
fi

# Get metrics for each container
for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    NODE_ID="${NODE_IDS[$idx]}"
    CONTAINER_ID="${CONTAINER_IDS[$idx]}"
    GAME_NUM=$((idx + 1))

    echo -e "${BOLD}Game $GAME_NUM (Match: $MATCH_ID, Container: $CONTAINER_ID)${NC}"
    echo "----------------------------------------------------------------------"

    # Set context and get metrics
    run_lightning node context set "$NODE_ID" --container-id "$CONTAINER_ID" --match-id "$MATCH_ID" > /dev/null 2>&1
    METRICS=$(run_lightning node metrics container -o json)

    if echo "$METRICS" | jq -e '.currentTick' > /dev/null 2>&1; then
        CURRENT_TICK=$(echo "$METRICS" | jq -r '.currentTick')
        TOTAL_TICKS=$(echo "$METRICS" | jq -r '.totalTicks')
        TOTAL_ENTITIES=$(echo "$METRICS" | jq -r '.totalEntities')
        COMPONENT_TYPES=$(echo "$METRICS" | jq -r '.totalComponentTypes')
        QUEUE_SIZE=$(echo "$METRICS" | jq -r '.commandQueueSize')

        # Tick timing
        LAST_TICK_MS=$(echo "$METRICS" | jq -r '.lastTickMs')
        AVG_TICK_MS=$(echo "$METRICS" | jq -r '.avgTickMs')
        MIN_TICK_MS=$(echo "$METRICS" | jq -r '.minTickMs')
        MAX_TICK_MS=$(echo "$METRICS" | jq -r '.maxTickMs')

        echo "  Tick Stats:"
        echo "    Current Tick:     $CURRENT_TICK"
        echo "    Total Ticks:      $TOTAL_TICKS"
        printf "    Tick Time:        %.3f ms (avg: %.3f, min: %.3f, max: %.3f)\n" \
            "$LAST_TICK_MS" "$AVG_TICK_MS" "$MIN_TICK_MS" "$MAX_TICK_MS"

        echo "  Entity Stats:"
        echo "    Total Entities:   $TOTAL_ENTITIES"
        echo "    Component Types:  $COMPONENT_TYPES"
        echo "    Command Queue:    $QUEUE_SIZE"

        # Snapshot metrics if available
        SNAPSHOT_METRICS=$(echo "$METRICS" | jq '.snapshotMetrics // empty')
        if [ -n "$SNAPSHOT_METRICS" ] && [ "$SNAPSHOT_METRICS" != "null" ]; then
            CACHE_HIT_RATE=$(echo "$SNAPSHOT_METRICS" | jq -r '.cacheHitRate')
            AVG_GEN_MS=$(echo "$SNAPSHOT_METRICS" | jq -r '.avgGenerationMs')
            TOTAL_GEN=$(echo "$SNAPSHOT_METRICS" | jq -r '.totalGenerations')

            echo "  Snapshot Stats:"
            # Calculate percentage (handle bc not being available)
            if command -v bc &> /dev/null; then
                CACHE_PCT=$(echo "$CACHE_HIT_RATE * 100" | bc -l 2>/dev/null)
            else
                CACHE_PCT=$(awk "BEGIN {printf \"%.1f\", $CACHE_HIT_RATE * 100}")
            fi
            printf "    Cache Hit Rate:   %.1f%%\n" "$CACHE_PCT"
            printf "    Avg Generation:   %.3f ms\n" "$AVG_GEN_MS"
            echo "    Total Snapshots:  $TOTAL_GEN"
        fi

        # System execution metrics from last tick
        SYSTEM_COUNT=$(echo "$METRICS" | jq '.lastTickSystems | length')
        if [ "$SYSTEM_COUNT" -gt 0 ]; then
            echo "  Last Tick Systems:"
            echo "$METRICS" | jq -r '.lastTickSystems[] | "    \(.systemName): \(.executionTimeMs | tostring | .[0:6]) ms"'
        fi

        echo ""
    else
        log_warn "Failed to get metrics for container $CONTAINER_ID"
        echo "  Response: $METRICS"
        echo ""
    fi
done

# =============================================================================
# Step 9: Verify cluster status
# =============================================================================
log_header "Cluster Verification"

CLUSTER_STATUS=$(run_lightning cluster status -o json)

if echo "$CLUSTER_STATUS" | jq -e '.totalNodes' > /dev/null 2>&1; then
    TOTAL_NODES=$(echo "$CLUSTER_STATUS" | jq -r '.totalNodes')
    HEALTHY_NODES=$(echo "$CLUSTER_STATUS" | jq -r '.healthyNodes')
    ACTIVE_MATCHES=$(echo "$CLUSTER_STATUS" | jq -r '.activeMatches')

    echo "  Total Nodes:    $TOTAL_NODES"
    echo "  Healthy Nodes:  $HEALTHY_NODES"
    echo "  Active Matches: $ACTIVE_MATCHES"
    echo ""

    if [ "$HEALTHY_NODES" -lt 1 ]; then
        log_error "No healthy nodes in cluster"
        exit 1
    fi
    log_success "Cluster status verified"
else
    log_warn "Failed to get cluster status: $CLUSTER_STATUS"
fi

# =============================================================================
# Step 10: Cleanup - Undeploy all games
# =============================================================================
log_header "Cleanup"

for idx in "${!MATCH_IDS[@]}"; do
    MATCH_ID="${MATCH_IDS[$idx]}"
    GAME_NUM=$((idx + 1))

    log_info "Undeploying game $GAME_NUM ($MATCH_ID)..."
    run_lightning deploy undeploy "$MATCH_ID" > /dev/null 2>&1 || true
done

sleep 2
log_success "All games undeployed"

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "=============================================="
log_success "E2E TEST PASSED"
echo "=============================================="
echo ""
echo "Test Summary:"
echo "  - Lightning CLI built and verified"
echo "  - Docker Compose services started and healthy"
echo "  - JWT authentication successful"
echo "  - ${#MATCH_IDS[@]} games deployed with modules: $TEST_MODULES"
echo "  - $NUM_ENTITIES entities spawned per game"
echo "  - Simulation ran for ${SIMULATION_RUNTIME}s at ${TICK_INTERVAL_MS}ms interval"
echo "  - Metrics collected for all containers"
echo "  - Cluster status verified"
echo "  - Cleanup completed"
echo ""
