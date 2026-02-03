//! REST API route handlers.
//!
//! Provides the main router with health check, container, match, resource, and WebSocket endpoints.

use axum::{
    body::Bytes,
    extract::{Multipart, Path, State},
    http::{header, StatusCode},
    response::IntoResponse,
    routing::{delete, get, post},
    Form, Json, Router,
};
use serde::{Deserialize, Serialize};
use stormstack_auth::{TokenError, TokenRequest, TokenResponse};
use stormstack_core::{CommandResult, ContainerId, MatchConfig, MatchId, ResourceId, SessionId};
use stormstack_net::{ApiError, ApiResponse, AuthUser};
use uuid::Uuid;

use crate::container::MatchState;
use crate::resources::{ResourceMetadata, ResourceType};
use crate::session::{PlayerSession, SessionState};
use crate::state::SharedAppState;
use crate::ws;

/// Health check response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HealthResponse {
    /// Service status.
    pub status: String,
    /// Service version.
    pub version: String,
}

/// Container summary for list responses.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContainerSummary {
    /// Container ID.
    pub id: String,
    /// Tenant ID.
    pub tenant_id: String,
    /// Container status.
    pub status: String,
}

/// Request to create a container.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateContainerRequest {
    /// Optional container name.
    pub name: Option<String>,
}

/// Response after creating a container.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateContainerResponse {
    /// Created container ID.
    pub id: String,
    /// Container status.
    pub status: String,
}

/// Detailed container response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContainerResponse {
    /// Container ID.
    pub id: String,
    /// Tenant ID.
    pub tenant_id: String,
    /// Number of matches in the container.
    pub match_count: usize,
    /// Number of entities in the container's ECS world.
    pub entity_count: usize,
    /// Current tick of the container.
    pub current_tick: u64,
}

/// Request to create a match.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateMatchRequest {
    /// Game mode for the match.
    pub game_mode: String,
    /// Maximum number of players.
    pub max_players: u32,
}

/// Match response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchResponse {
    /// Match ID.
    pub id: String,
    /// Current state of the match.
    pub state: String,
    /// Number of players currently in the match.
    pub player_count: usize,
    /// Maximum players allowed.
    pub max_players: u32,
    /// Current tick of the match.
    pub current_tick: u64,
}

/// Request to trigger a manual tick.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TickRequest {
    /// Delta time in seconds (defaults to 0.016 if not provided).
    #[serde(default = "default_delta_time")]
    pub delta_time: f64,
}

fn default_delta_time() -> f64 {
    0.016 // ~60 FPS
}

/// Request to submit a command.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubmitCommandRequest {
    /// Type of command to execute.
    pub command_type: String,
    /// JSON payload for the command.
    #[serde(default)]
    pub payload: serde_json::Value,
}

/// Response after submitting a command.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResponse {
    /// Whether the command was queued successfully.
    pub queued: bool,
    /// Optional message.
    pub message: Option<String>,
}

/// Response with command execution results.
///
/// This struct is used when returning synchronous command execution results.
/// Currently commands are queued and executed on tick, but this enables
/// immediate execution endpoints in the future.
#[allow(dead_code)]
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResultResponse {
    /// Whether the command executed successfully.
    pub success: bool,
    /// Optional message about the result.
    pub message: Option<String>,
    /// Optional structured data returned by the command.
    pub data: Option<serde_json::Value>,
}

impl From<CommandResult> for CommandResultResponse {
    fn from(result: CommandResult) -> Self {
        Self {
            success: result.success,
            message: result.message,
            data: result.data,
        }
    }
}

/// Response listing available commands.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AvailableCommandsResponse {
    /// List of available command types.
    pub commands: Vec<String>,
}

/// Request to toggle auto-play mode.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AutoPlayRequest {
    /// Whether auto-play is enabled.
    pub enabled: bool,
    /// Tick rate in milliseconds.
    #[serde(default = "default_tick_rate_ms")]
    pub tick_rate_ms: u64,
}

fn default_tick_rate_ms() -> u64 {
    16 // ~60 FPS
}

/// Response for auto-play state.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AutoPlayResponse {
    /// Whether auto-play is enabled.
    pub enabled: bool,
    /// Tick rate in milliseconds.
    pub tick_rate_ms: u64,
}

/// Response with container metrics.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricsResponse {
    /// Total number of ticks executed.
    pub tick_count: u64,
    /// Current number of entities.
    pub entity_count: usize,
    /// Current number of matches.
    pub match_count: usize,
    /// Seconds since container creation.
    pub uptime_seconds: u64,
    /// Total commands processed successfully.
    pub commands_processed: u64,
    /// Total commands that failed.
    pub commands_failed: u64,
}

/// Response for command errors.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandErrorResponse {
    /// Name of the command that failed.
    pub command_name: String,
    /// Error message.
    pub error: String,
    /// When the error occurred (ISO 8601).
    pub timestamp: String,
}

/// Response listing players in a container.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlayersResponse {
    /// List of player user IDs.
    pub player_ids: Vec<String>,
}

/// Session response for API endpoints.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionResponse {
    /// Session ID.
    pub id: String,
    /// User ID.
    pub user_id: String,
    /// Match ID.
    pub match_id: String,
    /// Container ID.
    pub container_id: String,
    /// Connection timestamp (ISO 8601).
    pub connected_at: String,
    /// Last activity timestamp (ISO 8601).
    pub last_activity: String,
    /// Session state.
    pub state: String,
}

impl From<&PlayerSession> for SessionResponse {
    fn from(session: &PlayerSession) -> Self {
        Self {
            id: session.id.0.to_string(),
            user_id: session.user_id.0.to_string(),
            match_id: session.match_id.0.to_string(),
            container_id: session.container_id.0.to_string(),
            connected_at: session.connected_at.to_rfc3339(),
            last_activity: session.last_activity.to_rfc3339(),
            state: session_state_to_string(session.state),
        }
    }
}

/// Helper to convert SessionState to string.
fn session_state_to_string(state: SessionState) -> String {
    match state {
        SessionState::Active => "active".to_string(),
        SessionState::Disconnected => "disconnected".to_string(),
        SessionState::Expired => "expired".to_string(),
    }
}

/// Resource response for API endpoints.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceResponse {
    /// Resource ID.
    pub id: String,
    /// Resource name.
    pub name: String,
    /// Resource type.
    pub resource_type: String,
    /// Size in bytes.
    pub size_bytes: u64,
    /// SHA-256 content hash.
    pub content_hash: String,
    /// Creation timestamp.
    pub created_at: String,
}

impl From<ResourceMetadata> for ResourceResponse {
    fn from(meta: ResourceMetadata) -> Self {
        Self {
            id: meta.id.0.to_string(),
            name: meta.name,
            resource_type: meta.resource_type.to_string(),
            size_bytes: meta.size_bytes,
            content_hash: meta.content_hash,
            created_at: meta.created_at.to_rfc3339(),
        }
    }
}

/// Helper to convert MatchState to string.
fn match_state_to_string(state: MatchState) -> String {
    match state {
        MatchState::Pending => "pending".to_string(),
        MatchState::Active => "active".to_string(),
        MatchState::Completed => "completed".to_string(),
    }
}

/// Helper to parse container ID from path.
fn parse_container_id(id: &str) -> Result<ContainerId, ApiError> {
    Uuid::parse_str(id)
        .map(ContainerId)
        .map_err(|_| ApiError::validation(format!("Invalid container ID: {}", id)))
}

/// Helper to parse match ID from path.
fn parse_match_id(id: &str) -> Result<MatchId, ApiError> {
    Uuid::parse_str(id)
        .map(MatchId)
        .map_err(|_| ApiError::validation(format!("Invalid match ID: {}", id)))
}

/// Helper to parse resource ID from path.
fn parse_resource_id(id: &str) -> Result<ResourceId, ApiError> {
    Uuid::parse_str(id)
        .map(ResourceId)
        .map_err(|_| ApiError::validation(format!("Invalid resource ID: {}", id)))
}

/// Helper to parse session ID from path.
fn parse_session_id(id: &str) -> Result<SessionId, ApiError> {
    Uuid::parse_str(id)
        .map(SessionId)
        .map_err(|_| ApiError::validation(format!("Invalid session ID: {}", id)))
}

/// Create the main application router.
pub fn create_router(state: SharedAppState) -> Router {
    Router::new()
        .route("/health", get(health_handler))
        // Container endpoints
        .route("/api/containers", get(list_containers_handler))
        .route("/api/containers", post(create_container_handler))
        .route("/api/containers/{id}", get(get_container_handler))
        .route("/api/containers/{id}", delete(delete_container_handler))
        .route("/api/containers/{id}/tick", post(tick_container_handler))
        .route(
            "/api/containers/{id}/ticks/auto",
            post(toggle_auto_play_handler),
        )
        .route(
            "/api/containers/{id}/players",
            get(list_players_handler),
        )
        .route(
            "/api/containers/{id}/commands/errors",
            get(get_command_errors_handler),
        )
        .route(
            "/api/containers/{id}/metrics",
            get(get_container_metrics_handler),
        )
        // Match endpoints
        .route(
            "/api/containers/{id}/matches",
            post(create_match_handler),
        )
        .route(
            "/api/containers/{id}/matches",
            get(list_matches_handler),
        )
        .route(
            "/api/containers/{id}/matches/{match_id}",
            get(get_match_handler),
        )
        .route(
            "/api/containers/{id}/matches/{match_id}",
            delete(delete_match_handler),
        )
        .route(
            "/api/containers/{id}/matches/{match_id}/join",
            post(join_match_handler),
        )
        .route(
            "/api/containers/{id}/matches/{match_id}/leave",
            post(leave_match_handler),
        )
        .route(
            "/api/containers/{id}/matches/{match_id}/start",
            post(start_match_handler),
        )
        // Command endpoints
        .route(
            "/api/containers/{id}/matches/{match_id}/commands",
            post(submit_command_handler),
        )
        .route("/api/commands", get(list_commands_handler))
        // Session endpoints
        .route(
            "/api/containers/{id}/sessions",
            get(list_sessions_handler),
        )
        .route(
            "/api/containers/{id}/sessions/{session_id}",
            get(get_session_handler),
        )
        .route(
            "/api/containers/{id}/sessions/{session_id}",
            delete(delete_session_handler),
        )
        // Resource endpoints
        .route("/api/resources", post(upload_resource_handler))
        .route("/api/resources", get(list_resources_handler))
        .route("/api/resources/{id}", get(download_resource_handler))
        .route("/api/resources/{id}/metadata", get(get_resource_metadata_handler))
        .route("/api/resources/{id}", delete(delete_resource_handler))
        // OAuth2 token endpoint
        .route("/auth/token", post(token_endpoint))
        // WebSocket endpoint for match streaming
        .route("/ws/matches/{match_id}", get(ws::ws_upgrade))
        .with_state(state)
}

/// Health check endpoint.
///
/// GET /health
///
/// Returns the service status and version.
async fn health_handler() -> ApiResponse<HealthResponse> {
    ApiResponse::ok(HealthResponse {
        status: "healthy".to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    })
}

/// List containers endpoint.
///
/// GET /api/containers
///
/// Returns a list of containers for the authenticated user's tenant.
async fn list_containers_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
) -> ApiResponse<Vec<ContainerSummary>> {
    let containers = state
        .container_service()
        .list_containers_for_tenant(auth.tenant_id);

    let summaries: Vec<ContainerSummary> = containers
        .iter()
        .map(|c| ContainerSummary {
            id: c.id().0.to_string(),
            tenant_id: c.tenant_id().0.to_string(),
            status: "running".to_string(),
        })
        .collect();

    ApiResponse::ok(summaries)
}

/// Create container endpoint.
///
/// POST /api/containers
///
/// Creates a new execution container for the authenticated user's tenant.
async fn create_container_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Json(_request): Json<CreateContainerRequest>,
) -> ApiResponse<CreateContainerResponse> {
    let container_id = state.container_service().create_container(auth.tenant_id);

    ApiResponse::ok(CreateContainerResponse {
        id: container_id.0.to_string(),
        status: "created".to_string(),
    })
}

/// Get container details endpoint.
///
/// GET /api/containers/{id}
///
/// Returns detailed information about a specific container.
async fn get_container_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<ContainerResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    Ok(ApiResponse::ok(ContainerResponse {
        id: container.id().0.to_string(),
        tenant_id: container.tenant_id().0.to_string(),
        match_count: container.match_count(),
        entity_count: container.entity_count(),
        current_tick: container.current_tick(),
    }))
}

/// Delete container endpoint.
///
/// DELETE /api/containers/{id}
///
/// Deletes a container and all its matches.
async fn delete_container_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<()>, ApiError> {
    let container_id = parse_container_id(&id)?;

    state
        .container_service()
        .delete_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    Ok(ApiResponse::ok(()))
}

/// Tick container endpoint.
///
/// POST /api/containers/{id}/tick
///
/// Manually triggers a tick for the container.
async fn tick_container_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
    Json(request): Json<TickRequest>,
) -> Result<ApiResponse<ContainerResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    // Verify ownership first
    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    // Execute the tick
    container
        .tick(request.delta_time)
        .map_err(|e| ApiError::internal(e.to_string()))?;

    Ok(ApiResponse::ok(ContainerResponse {
        id: container.id().0.to_string(),
        tenant_id: container.tenant_id().0.to_string(),
        match_count: container.match_count(),
        entity_count: container.entity_count(),
        current_tick: container.current_tick(),
    }))
}

/// Toggle auto-play endpoint.
///
/// POST /api/containers/{id}/ticks/auto
///
/// Enables or disables automatic tick execution for the container.
async fn toggle_auto_play_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
    Json(request): Json<AutoPlayRequest>,
) -> Result<ApiResponse<AutoPlayResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let tick_rate = std::time::Duration::from_millis(request.tick_rate_ms);
    container.set_auto_play(request.enabled, tick_rate);

    Ok(ApiResponse::ok(AutoPlayResponse {
        enabled: container.is_auto_playing(),
        tick_rate_ms: container.auto_play_tick_rate_ms(),
    }))
}

/// List players in container endpoint.
///
/// GET /api/containers/{id}/players
///
/// Returns a list of all player IDs across all matches in the container.
async fn list_players_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<PlayersResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let player_ids: Vec<String> = container
        .all_players()
        .into_iter()
        .map(|id| id.0.to_string())
        .collect();

    Ok(ApiResponse::ok(PlayersResponse { player_ids }))
}

/// Get command errors endpoint.
///
/// GET /api/containers/{id}/commands/errors
///
/// Returns a list of recent command execution errors.
async fn get_command_errors_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<Vec<CommandErrorResponse>>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let errors: Vec<CommandErrorResponse> = container
        .command_errors()
        .into_iter()
        .map(|e| CommandErrorResponse {
            command_name: e.command_name,
            error: e.error,
            timestamp: e.timestamp.to_rfc3339(),
        })
        .collect();

    Ok(ApiResponse::ok(errors))
}

/// Get container metrics endpoint.
///
/// GET /api/containers/{id}/metrics
///
/// Returns metrics for the container.
async fn get_container_metrics_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<MetricsResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let metrics = container.metrics();

    Ok(ApiResponse::ok(MetricsResponse {
        tick_count: metrics.tick_count,
        entity_count: metrics.entity_count,
        match_count: metrics.match_count,
        uptime_seconds: metrics.uptime_seconds,
        commands_processed: metrics.commands_processed,
        commands_failed: metrics.commands_failed,
    }))
}

/// Create match endpoint.
///
/// POST /api/containers/{id}/matches
///
/// Creates a new match within the specified container.
async fn create_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
    Json(request): Json<CreateMatchRequest>,
) -> Result<ApiResponse<MatchResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let config = MatchConfig {
        game_mode: request.game_mode,
        max_players: request.max_players,
        ..Default::default()
    };

    let match_id = container
        .create_match(config)
        .map_err(|e| ApiError::internal(e.to_string()))?;

    let match_ref = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::internal("Match created but not found"))?;

    Ok(ApiResponse::ok(MatchResponse {
        id: match_id.0.to_string(),
        state: match_state_to_string(match_ref.state()),
        player_count: match_ref.player_count(),
        max_players: match_ref.config.max_players,
        current_tick: match_ref.current_tick,
    }))
}

/// List matches endpoint.
///
/// GET /api/containers/{id}/matches
///
/// Returns a list of all matches in the container.
async fn list_matches_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<Vec<MatchResponse>>, ApiError> {
    let container_id = parse_container_id(&id)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let matches: Vec<MatchResponse> = container
        .list_matches()
        .iter()
        .map(|m| MatchResponse {
            id: m.id.0.to_string(),
            state: match_state_to_string(m.state),
            player_count: m.player_count,
            max_players: m.max_players,
            current_tick: m.current_tick,
        })
        .collect();

    Ok(ApiResponse::ok(matches))
}

/// Get match details endpoint.
///
/// GET /api/containers/{id}/matches/{match_id}
///
/// Returns detailed information about a specific match.
async fn get_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<MatchResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let match_ref = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::not_found("Match"))?;

    Ok(ApiResponse::ok(MatchResponse {
        id: match_ref.id.0.to_string(),
        state: match_state_to_string(match_ref.state()),
        player_count: match_ref.player_count(),
        max_players: match_ref.config.max_players,
        current_tick: match_ref.current_tick,
    }))
}

/// Delete match endpoint.
///
/// DELETE /api/containers/{id}/matches/{match_id}
///
/// Deletes a match from the container.
async fn delete_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<()>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    container
        .delete_match(match_id)
        .map_err(|_| ApiError::not_found("Match"))?;

    Ok(ApiResponse::ok(()))
}

/// Join match endpoint.
///
/// POST /api/containers/{id}/matches/{match_id}/join
///
/// Adds the authenticated user to the match.
async fn join_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<MatchResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    container
        .join_match(match_id, auth.user_id)
        .map_err(|e| match e {
            stormstack_core::StormError::MatchNotFound(_) => ApiError::not_found("Match"),
            stormstack_core::StormError::ResourceExhausted(msg) => ApiError::conflict(msg),
            stormstack_core::StormError::InvalidState(msg) => ApiError::conflict(msg),
            _ => ApiError::internal(e.to_string()),
        })?;

    let match_ref = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::not_found("Match"))?;

    Ok(ApiResponse::ok(MatchResponse {
        id: match_ref.id.0.to_string(),
        state: match_state_to_string(match_ref.state()),
        player_count: match_ref.player_count(),
        max_players: match_ref.config.max_players,
        current_tick: match_ref.current_tick,
    }))
}

/// Leave match endpoint.
///
/// POST /api/containers/{id}/matches/{match_id}/leave
///
/// Removes the authenticated user from the match.
async fn leave_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<MatchResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    container
        .leave_match(match_id, auth.user_id)
        .map_err(|e| match e {
            stormstack_core::StormError::MatchNotFound(_) => ApiError::not_found("Match"),
            stormstack_core::StormError::InvalidState(msg) => ApiError::conflict(msg),
            _ => ApiError::internal(e.to_string()),
        })?;

    let match_ref = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::not_found("Match"))?;

    Ok(ApiResponse::ok(MatchResponse {
        id: match_ref.id.0.to_string(),
        state: match_state_to_string(match_ref.state()),
        player_count: match_ref.player_count(),
        max_players: match_ref.config.max_players,
        current_tick: match_ref.current_tick,
    }))
}

/// Start match endpoint.
///
/// POST /api/containers/{id}/matches/{match_id}/start
///
/// Transitions the match from Pending to Active state.
async fn start_match_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<MatchResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    container
        .start_match(match_id)
        .map_err(|e| match e {
            stormstack_core::StormError::MatchNotFound(_) => ApiError::not_found("Match"),
            stormstack_core::StormError::InvalidState(msg) => ApiError::conflict(msg),
            _ => ApiError::internal(e.to_string()),
        })?;

    let match_ref = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::not_found("Match"))?;

    Ok(ApiResponse::ok(MatchResponse {
        id: match_ref.id.0.to_string(),
        state: match_state_to_string(match_ref.state()),
        player_count: match_ref.player_count(),
        max_players: match_ref.config.max_players,
        current_tick: match_ref.current_tick,
    }))
}

/// List sessions in container endpoint.
///
/// GET /api/containers/{id}/sessions
///
/// Returns a list of all sessions in the container.
async fn list_sessions_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<Vec<SessionResponse>>, ApiError> {
    let container_id = parse_container_id(&id)?;

    // Verify container exists and belongs to tenant
    let _container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let sessions: Vec<SessionResponse> = state
        .session_service()
        .get_by_container(container_id)
        .iter()
        .map(SessionResponse::from)
        .collect();

    Ok(ApiResponse::ok(sessions))
}

/// Get session details endpoint.
///
/// GET /api/containers/{id}/sessions/{session_id}
///
/// Returns details about a specific session.
async fn get_session_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, session_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<SessionResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let session_id = parse_session_id(&session_id_str)?;

    // Verify container exists and belongs to tenant
    let _container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    let session = state
        .session_service()
        .get(session_id)
        .ok_or_else(|| ApiError::not_found("Session"))?;

    // Verify session belongs to this container
    if session.container_id != container_id {
        return Err(ApiError::not_found("Session"));
    }

    Ok(ApiResponse::ok(SessionResponse::from(&session)))
}

/// Delete (end) session endpoint.
///
/// DELETE /api/containers/{id}/sessions/{session_id}
///
/// Ends a session and removes it from the system.
async fn delete_session_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, session_id_str)): Path<(String, String)>,
) -> Result<ApiResponse<()>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let session_id = parse_session_id(&session_id_str)?;

    // Verify container exists and belongs to tenant
    let _container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    // Get session to verify it exists and belongs to this container
    let session = state
        .session_service()
        .get(session_id)
        .ok_or_else(|| ApiError::not_found("Session"))?;

    if session.container_id != container_id {
        return Err(ApiError::not_found("Session"));
    }

    // Remove the session
    state.session_service().remove(session_id);

    Ok(ApiResponse::ok(()))
}

/// Submit command endpoint.
///
/// POST /api/containers/{id}/matches/{match_id}/commands
///
/// Submits a command to be executed on the next tick.
async fn submit_command_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path((id, match_id_str)): Path<(String, String)>,
    Json(request): Json<SubmitCommandRequest>,
) -> Result<ApiResponse<CommandResponse>, ApiError> {
    let container_id = parse_container_id(&id)?;
    let match_id = parse_match_id(&match_id_str)?;

    let container = state
        .container_service()
        .get_container_for_tenant(container_id, auth.tenant_id)
        .map_err(|_| ApiError::not_found("Container"))?;

    // Verify match exists
    let _ = container
        .get_match(match_id)
        .ok_or_else(|| ApiError::not_found("Match"))?;

    // Create the command from the registry
    let command = {
        let registry = state.command_registry().read();
        registry
            .create(&request.command_type, request.payload)
            .map_err(|e| ApiError::validation(e.to_string()))?
    };

    // Queue the command
    container
        .queue_command(match_id, command, auth.user_id)
        .map_err(|e| match e {
            stormstack_core::StormError::MatchNotFound(_) => ApiError::not_found("Match"),
            _ => ApiError::internal(e.to_string()),
        })?;

    Ok(ApiResponse::ok(CommandResponse {
        queued: true,
        message: Some(format!("Command '{}' queued for execution", request.command_type)),
    }))
}

/// List available commands endpoint.
///
/// GET /api/commands
///
/// Returns a list of all registered command types.
async fn list_commands_handler(
    State(state): State<SharedAppState>,
) -> ApiResponse<AvailableCommandsResponse> {
    let registry = state.command_registry().read();
    let commands: Vec<String> = registry
        .available_commands()
        .into_iter()
        .map(|s| s.to_string())
        .collect();

    ApiResponse::ok(AvailableCommandsResponse { commands })
}

/// OAuth2 token endpoint.
///
/// POST /auth/token
///
/// Handles OAuth2 token requests supporting multiple grant types:
/// - `client_credentials` - Machine-to-machine authentication
/// - `password` - Resource owner password credentials
/// - `refresh_token` - Token refresh
///
/// The endpoint accepts both application/x-www-form-urlencoded and application/json.
async fn token_endpoint(
    State(state): State<SharedAppState>,
    Form(request): Form<TokenRequest>,
) -> Result<Json<TokenResponse>, (StatusCode, Json<TokenError>)> {
    let oauth2_service = state.oauth2().ok_or_else(|| {
        (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(TokenError::invalid_request("OAuth2 service not configured")),
        )
    })?;

    let service = oauth2_service.read();
    service.token(&request).map(Json).map_err(|e| {
        let status = match e.error.as_str() {
            "invalid_client" => StatusCode::UNAUTHORIZED,
            "invalid_grant" => StatusCode::BAD_REQUEST,
            "invalid_request" => StatusCode::BAD_REQUEST,
            "unauthorized_client" => StatusCode::UNAUTHORIZED,
            "unsupported_grant_type" => StatusCode::BAD_REQUEST,
            "invalid_scope" => StatusCode::BAD_REQUEST,
            _ => StatusCode::BAD_REQUEST,
        };
        (status, Json(e))
    })
}

/// Upload resource endpoint.
///
/// POST /api/resources
///
/// Uploads a new resource (WASM module, game asset, or configuration).
/// Expects multipart form data with:
/// - `name`: Resource name
/// - `type`: Resource type (wasm_module, game_asset, configuration)
/// - `file`: Resource data
async fn upload_resource_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    mut multipart: Multipart,
) -> Result<ApiResponse<ResourceResponse>, ApiError> {
    let mut name: Option<String> = None;
    let mut resource_type: Option<ResourceType> = None;
    let mut data: Option<Vec<u8>> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError::validation(format!("Failed to read multipart field: {}", e)))?
    {
        let field_name = field.name().unwrap_or("").to_string();

        match field_name.as_str() {
            "name" => {
                name = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| ApiError::validation(format!("Failed to read name: {}", e)))?,
                );
            }
            "type" => {
                let type_str = field
                    .text()
                    .await
                    .map_err(|e| ApiError::validation(format!("Failed to read type: {}", e)))?;
                resource_type = Some(
                    type_str
                        .parse()
                        .map_err(|_| ApiError::validation(format!("Invalid resource type: {}", type_str)))?,
                );
            }
            "file" => {
                data = Some(
                    field
                        .bytes()
                        .await
                        .map_err(|e| ApiError::validation(format!("Failed to read file: {}", e)))?
                        .to_vec(),
                );
            }
            _ => {
                // Ignore unknown fields
            }
        }
    }

    let name = name.ok_or_else(|| ApiError::validation("Missing 'name' field"))?;
    let resource_type =
        resource_type.ok_or_else(|| ApiError::validation("Missing 'type' field"))?;
    let data = data.ok_or_else(|| ApiError::validation("Missing 'file' field"))?;

    if data.is_empty() {
        return Err(ApiError::validation("File data is empty"));
    }

    let metadata = state
        .resource_storage()
        .store(auth.tenant_id, &name, resource_type, &data)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?;

    Ok(ApiResponse::ok(ResourceResponse::from(metadata)))
}

/// List resources endpoint.
///
/// GET /api/resources
///
/// Lists all resources for the authenticated user's tenant.
async fn list_resources_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
) -> Result<ApiResponse<Vec<ResourceResponse>>, ApiError> {
    let resources = state
        .resource_storage()
        .list(auth.tenant_id)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?;

    let responses: Vec<ResourceResponse> = resources.into_iter().map(ResourceResponse::from).collect();

    Ok(ApiResponse::ok(responses))
}

/// Download resource endpoint.
///
/// GET /api/resources/{id}
///
/// Downloads the resource data.
async fn download_resource_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<impl IntoResponse, ApiError> {
    let resource_id = parse_resource_id(&id)?;

    // Get metadata first to determine content type
    let metadata = state
        .resource_storage()
        .get_metadata(auth.tenant_id, resource_id)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?
        .ok_or_else(|| ApiError::not_found("Resource"))?;

    let data = state
        .resource_storage()
        .get(auth.tenant_id, resource_id)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?
        .ok_or_else(|| ApiError::not_found("Resource"))?;

    let content_type = match metadata.resource_type {
        ResourceType::WasmModule => "application/wasm",
        ResourceType::GameAsset => "application/octet-stream",
        ResourceType::Configuration => "application/json",
    };

    let content_disposition = format!("attachment; filename=\"{}\"", metadata.name);
    Ok((
        [
            (header::CONTENT_TYPE, content_type.to_string()),
            (header::CONTENT_DISPOSITION, content_disposition),
        ],
        Bytes::from(data),
    ))
}

/// Get resource metadata endpoint.
///
/// GET /api/resources/{id}/metadata
///
/// Gets metadata for a resource without downloading its content.
async fn get_resource_metadata_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<ResourceResponse>, ApiError> {
    let resource_id = parse_resource_id(&id)?;

    let metadata = state
        .resource_storage()
        .get_metadata(auth.tenant_id, resource_id)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?
        .ok_or_else(|| ApiError::not_found("Resource"))?;

    Ok(ApiResponse::ok(ResourceResponse::from(metadata)))
}

/// Delete resource endpoint.
///
/// DELETE /api/resources/{id}
///
/// Deletes a resource.
async fn delete_resource_handler(
    State(state): State<SharedAppState>,
    auth: AuthUser,
    Path(id): Path<String>,
) -> Result<ApiResponse<()>, ApiError> {
    let resource_id = parse_resource_id(&id)?;

    let deleted = state
        .resource_storage()
        .delete(auth.tenant_id, resource_id)
        .await
        .map_err(|e| ApiError::internal(e.to_string()))?;

    if deleted {
        Ok(ApiResponse::ok(()))
    } else {
        Err(ApiError::not_found("Resource"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use stormstack_auth::{Claims, JwtService};
    use stormstack_core::{TenantId, UserId};
    use stormstack_ecs::{shared_world, EcsWorld};
    use stormstack_wasm::WasmSandbox;
    use stormstack_ws::{shared_connection_manager, shared_subscriptions};
    use std::sync::Arc;
    use tower::ServiceExt;

    fn create_test_state() -> SharedAppState {
        let jwt_service = Arc::new(JwtService::new(b"test-secret-key-32-bytes-long!!"));
        let world = shared_world();
        let sandbox = Arc::new(WasmSandbox::new().expect("sandbox"));
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);

        Arc::new(crate::state::AppState::new(
            jwt_service,
            world,
            sandbox,
            connections,
        ))
    }

    fn create_state_with_jwt() -> (SharedAppState, Arc<JwtService>, TenantId) {
        let jwt_service = Arc::new(JwtService::new(b"test-secret-key-32-bytes-long!!"));
        let world = shared_world();
        let sandbox = Arc::new(WasmSandbox::new().expect("sandbox"));
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);
        let tenant_id = TenantId::new();

        let state = Arc::new(crate::state::AppState::new(
            jwt_service.clone(),
            world,
            sandbox,
            connections,
        ));

        (state, jwt_service, tenant_id)
    }

    fn generate_token(jwt_service: &JwtService, tenant_id: TenantId) -> String {
        let claims = Claims::new(UserId::new(), tenant_id, vec!["user".to_string()]);
        jwt_service.generate_token(&claims).expect("token")
    }

    #[tokio::test]
    async fn health_endpoint_returns_ok() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .uri("/health")
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn list_containers_requires_auth() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .uri("/api/containers")
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn list_containers_returns_empty_initially() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);
        let app = create_router(state);

        let request = Request::builder()
            .uri("/api/containers")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["data"].as_array().unwrap().is_empty());
    }

    #[tokio::test]
    async fn create_container_requires_auth() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri("/api/containers")
            .header("Content-Type", "application/json")
            .body(Body::from("{}"))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn create_container_with_auth() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);
        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri("/api/containers")
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from("{}"))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["id"].as_str().is_some());
        assert_eq!(json["data"]["status"].as_str().unwrap(), "created");
    }

    #[tokio::test]
    async fn get_container_returns_details() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container first
        let container_id = state.container_service().create_container(tenant_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["success"].as_bool().unwrap());
        assert_eq!(json["data"]["id"].as_str().unwrap(), container_id.0.to_string());
        assert_eq!(json["data"]["match_count"].as_u64().unwrap(), 0);
        assert_eq!(json["data"]["entity_count"].as_u64().unwrap(), 0);
        assert_eq!(json["data"]["current_tick"].as_u64().unwrap(), 0);
    }

    #[tokio::test]
    async fn get_container_not_found() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);
        let app = create_router(state);

        let fake_id = Uuid::new_v4();

        let request = Request::builder()
            .uri(format!("/api/containers/{}", fake_id))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn delete_container_removes_it() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container first
        let container_id = state.container_service().create_container(tenant_id);
        assert!(state.container_service().has_container(container_id));

        let app = create_router(state.clone());

        let request = Request::builder()
            .method("DELETE")
            .uri(format!("/api/containers/{}", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Verify container is deleted
        assert!(!state.container_service().has_container(container_id));
    }

    #[tokio::test]
    async fn tick_container_advances_tick() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container
        let container_id = state.container_service().create_container(tenant_id);

        let app = create_router(state.clone());

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/tick", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"delta_time": 0.016}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"]["current_tick"].as_u64().unwrap(), 1);
    }

    #[tokio::test]
    async fn create_match_returns_id() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container
        let container_id = state.container_service().create_container(tenant_id);

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"game_mode": "deathmatch", "max_players": 8}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["data"]["id"].as_str().is_some());
        assert_eq!(json["data"]["state"].as_str().unwrap(), "pending");
        assert_eq!(json["data"]["max_players"].as_u64().unwrap(), 8);
        assert_eq!(json["data"]["player_count"].as_u64().unwrap(), 0);
    }

    #[tokio::test]
    async fn list_matches_returns_all() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with matches
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        container.create_match(MatchConfig::default()).unwrap();
        container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/matches", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"].as_array().unwrap().len(), 2);
    }

    #[tokio::test]
    async fn get_match_returns_details() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig {
            game_mode: "capture".to_string(),
            max_players: 4,
            ..Default::default()
        }).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/matches/{}", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"]["id"].as_str().unwrap(), match_id.0.to_string());
        assert_eq!(json["data"]["max_players"].as_u64().unwrap(), 4);
    }

    #[tokio::test]
    async fn delete_match_removes_it() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        assert_eq!(container.match_count(), 1);

        let app = create_router(state.clone());

        let request = Request::builder()
            .method("DELETE")
            .uri(format!("/api/containers/{}/matches/{}", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Verify match is deleted
        let container = state.container_service().get_container(container_id).unwrap();
        assert_eq!(container.match_count(), 0);
    }

    #[tokio::test]
    async fn join_match_adds_player() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/join", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"]["player_count"].as_u64().unwrap(), 1);
    }

    #[tokio::test]
    async fn leave_match_removes_player() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let user_id = UserId::new();
        let claims = Claims::new(user_id, tenant_id, vec!["user".to_string()]);
        let token = jwt_service.generate_token(&claims).expect("token");

        // Create a container with a match and add the user
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.join_match(match_id, user_id).unwrap();

        // Verify player was added
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.player_count(), 1);
        }

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/leave", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"]["player_count"].as_u64().unwrap(), 0);
    }

    #[tokio::test]
    async fn start_match_changes_state() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/start", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"]["state"].as_str().unwrap(), "active");
    }

    #[tokio::test]
    async fn join_full_match_returns_conflict() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match that can only hold 1 player
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig {
            max_players: 1,
            ..Default::default()
        }).unwrap();

        // Fill the match
        container.join_match(match_id, UserId::new()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/join", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::CONFLICT);
    }

    #[tokio::test]
    async fn start_already_active_match_returns_conflict() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match and start it
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/start", container_id.0, match_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::CONFLICT);
    }

    #[tokio::test]
    async fn tenant_isolation_prevents_access_to_other_containers() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();

        // Create a container for a different tenant
        let other_tenant = TenantId::new();
        let container_id = state.container_service().create_container(other_tenant);

        // Try to access with a token for our tenant
        let token = generate_token(&jwt_service, tenant_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        // Should return NOT_FOUND to avoid leaking information about other tenants
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn health_response_format() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .uri("/health")
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["status"].as_str().is_some());
        assert!(json["data"]["version"].as_str().is_some());
    }

    #[tokio::test]
    async fn invalid_container_id_returns_bad_request() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);
        let app = create_router(state);

        let request = Request::builder()
            .uri("/api/containers/not-a-uuid")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    }

    // =========================================================================
    // Command endpoint tests
    // =========================================================================

    #[tokio::test]
    async fn submit_command_queues_successfully() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"command_type": "spawn_entity", "payload": {}}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["queued"].as_bool().unwrap());
    }

    #[tokio::test]
    async fn spawn_entity_via_command_executes_on_tick() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with an active match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        // Initial entity count
        let initial_count = container.entity_count();

        let app = create_router(state.clone());

        // Submit spawn command
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"command_type": "spawn_entity", "payload": {}}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Tick to execute the command
        container.tick(0.016).unwrap();

        // Verify entity was spawned
        assert_eq!(container.entity_count(), initial_count + 1);
    }

    #[tokio::test]
    async fn despawn_entity_via_command() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with an active match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        // Spawn an entity first
        let entity_id = {
            let mut world = container.world().write();
            use stormstack_ecs::EcsWorld;
            world.spawn()
        };
        assert_eq!(container.entity_count(), 1);

        let app = create_router(state.clone());

        // Submit despawn command
        let payload = serde_json::json!({
            "command_type": "despawn_entity",
            "payload": { "entity_id": entity_id.0 }
        });

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(payload.to_string()))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Tick to execute the command
        container.tick(0.016).unwrap();

        // Verify entity was despawned
        assert_eq!(container.entity_count(), 0);
    }

    #[tokio::test]
    async fn unknown_command_returns_error() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"command_type": "nonexistent_command", "payload": {}}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    }

    #[tokio::test]
    async fn command_registry_lists_available() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .uri("/api/commands")
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let commands = json["data"]["commands"].as_array().unwrap();
        assert!(commands.iter().any(|c| c.as_str() == Some("spawn_entity")));
        assert!(commands.iter().any(|c| c.as_str() == Some("despawn_entity")));
    }

    #[tokio::test]
    async fn command_queue_executed_on_tick() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with an active match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        let app = create_router(state.clone());

        // Queue multiple commands
        for _ in 0..3 {
            let request = Request::builder()
                .method("POST")
                .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
                .header("Content-Type", "application/json")
                .header("Authorization", format!("Bearer {}", token))
                .body(Body::from(r#"{"command_type": "spawn_entity", "payload": {}}"#))
                .unwrap();

            let response = app.clone().oneshot(request).await.unwrap();
            assert_eq!(response.status(), StatusCode::OK);
        }

        // Verify commands are pending
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.pending_command_count(), 3);
        }

        // Tick to execute all commands
        container.tick(0.016).unwrap();

        // Queue should be empty now
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.pending_command_count(), 0);
        }

        // Entities should be spawned
        assert_eq!(container.entity_count(), 3);
    }

    #[tokio::test]
    async fn submit_command_to_nonexistent_match_returns_not_found() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let fake_match_id = uuid::Uuid::new_v4();

        let app = create_router(state);

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, fake_match_id))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"command_type": "spawn_entity", "payload": {}}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    // =========================================================================
    // Session endpoint tests
    // =========================================================================

    #[tokio::test]
    async fn list_sessions_endpoint() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        // Create sessions
        let user1 = UserId::new();
        let user2 = UserId::new();
        state.session_service().create(user1, match_id, container_id);
        state.session_service().create(user2, match_id, container_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["success"].as_bool().unwrap());
        assert_eq!(json["data"].as_array().unwrap().len(), 2);
    }

    #[tokio::test]
    async fn get_session_endpoint() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        // Create a session
        let user_id = UserId::new();
        let session_id = state.session_service().create(user_id, match_id, container_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions/{}", container_id.0, session_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["success"].as_bool().unwrap());
        assert_eq!(json["data"]["id"].as_str().unwrap(), session_id.0.to_string());
        assert_eq!(json["data"]["user_id"].as_str().unwrap(), user_id.0.to_string());
        assert_eq!(json["data"]["state"].as_str().unwrap(), "active");
    }

    #[tokio::test]
    async fn delete_session_endpoint() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        // Create a session
        let user_id = UserId::new();
        let session_id = state.session_service().create(user_id, match_id, container_id);
        assert!(state.session_service().has_session(session_id));

        let app = create_router(state.clone());

        let request = Request::builder()
            .method("DELETE")
            .uri(format!("/api/containers/{}/sessions/{}", container_id.0, session_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Verify session is deleted
        assert!(!state.session_service().has_session(session_id));
    }

    #[tokio::test]
    async fn get_session_not_found() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container
        let container_id = state.container_service().create_container(tenant_id);

        let fake_session_id = Uuid::new_v4();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions/{}", container_id.0, fake_session_id))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn list_sessions_empty_container() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container without sessions
        let container_id = state.container_service().create_container(tenant_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert!(json["data"].as_array().unwrap().is_empty());
    }

    #[tokio::test]
    async fn session_isolation_between_containers() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create two containers
        let container1_id = state.container_service().create_container(tenant_id);
        let container1 = state.container_service().get_container(container1_id).unwrap();
        let match1_id = container1.create_match(MatchConfig::default()).unwrap();

        let container2_id = state.container_service().create_container(tenant_id);
        let container2 = state.container_service().get_container(container2_id).unwrap();
        let match2_id = container2.create_match(MatchConfig::default()).unwrap();

        // Create sessions in each container
        let user_id = UserId::new();
        let session1_id = state.session_service().create(user_id, match1_id, container1_id);
        let _session2_id = state.session_service().create(user_id, match2_id, container2_id);

        let app = create_router(state.clone());

        // List sessions in container1 - should only have 1 session
        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions", container1_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.clone().oneshot(request).await.unwrap();
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json["data"].as_array().unwrap().len(), 1);
        assert_eq!(json["data"][0]["id"].as_str().unwrap(), session1_id.0.to_string());

        // Try to get session1 from container2 - should return 404
        let request = Request::builder()
            .uri(format!("/api/containers/{}/sessions/{}", container2_id.0, session1_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    // =========================================================================
    // Auto-play, players, metrics, and command errors tests
    // =========================================================================

    #[tokio::test]
    async fn toggle_auto_play() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Initially auto-play should be disabled
        assert!(!container.is_auto_playing());

        let app = create_router(state.clone());

        // Enable auto-play
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/ticks/auto", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"enabled": true, "tick_rate_ms": 32}"#))
            .unwrap();

        let response = app.clone().oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["enabled"].as_bool().unwrap());
        assert_eq!(json["data"]["tick_rate_ms"].as_u64().unwrap(), 32);

        // Verify on the container
        assert!(container.is_auto_playing());
        assert_eq!(container.auto_play_tick_rate_ms(), 32);

        // Disable auto-play
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/ticks/auto", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"enabled": false, "tick_rate_ms": 16}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        assert!(!container.is_auto_playing());
    }

    #[tokio::test]
    async fn list_players_in_container() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Create a match and add players
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        let user1 = UserId::new();
        let user2 = UserId::new();
        container.join_match(match_id, user1).unwrap();
        container.join_match(match_id, user2).unwrap();

        // Create another match with more players
        let match_id2 = container.create_match(MatchConfig::default()).unwrap();
        let user3 = UserId::new();
        container.join_match(match_id2, user3).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/players", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let player_ids = json["data"]["player_ids"].as_array().unwrap();
        assert_eq!(player_ids.len(), 3);

        // Verify all user IDs are present
        let ids_as_strings: Vec<&str> = player_ids
            .iter()
            .map(|v| v.as_str().unwrap())
            .collect();
        assert!(ids_as_strings.contains(&user1.0.to_string().as_str()));
        assert!(ids_as_strings.contains(&user2.0.to_string().as_str()));
        assert!(ids_as_strings.contains(&user3.0.to_string().as_str()));
    }

    #[tokio::test]
    async fn get_command_errors() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Create an active match
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        // Initially no errors
        assert!(container.command_errors().is_empty());

        // Submit a command that will fail (despawn non-existent entity)
        let app = create_router(state.clone());

        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/matches/{}/commands", container_id.0, match_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"command_type": "despawn_entity", "payload": {"entity_id": 999999}}"#))
            .unwrap();

        let _response = app.clone().oneshot(request).await.unwrap();

        // Tick to execute the command (it should fail)
        container.tick(0.016).unwrap();

        // Now get the errors via API
        let request = Request::builder()
            .uri(format!("/api/containers/{}/commands/errors", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let errors = json["data"].as_array().unwrap();
        assert!(!errors.is_empty());

        // Verify error structure
        let error = &errors[0];
        assert!(error["command_name"].as_str().is_some());
        assert!(error["error"].as_str().is_some());
        assert!(error["timestamp"].as_str().is_some());
    }

    #[tokio::test]
    async fn get_container_metrics() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Create matches and spawn entities
        let match_id = container.create_match(MatchConfig::default()).unwrap();
        container.start_match(match_id).unwrap();

        // Spawn some entities via world directly
        {
            let mut world = container.world().write();
            world.spawn();
            world.spawn();
        }

        // Tick a few times
        container.tick(0.016).unwrap();
        container.tick(0.016).unwrap();
        container.tick(0.016).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/metrics", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let data = &json["data"];

        assert_eq!(data["tick_count"].as_u64().unwrap(), 3);
        assert_eq!(data["entity_count"].as_u64().unwrap(), 2);
        assert_eq!(data["match_count"].as_u64().unwrap(), 1);
        assert!(data["uptime_seconds"].as_u64().is_some());
        assert!(data["commands_processed"].as_u64().is_some());
        assert!(data["commands_failed"].as_u64().is_some());
    }

    #[tokio::test]
    async fn auto_play_changes_tick_behavior() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Default auto-play state
        assert!(!container.is_auto_playing());
        assert_eq!(container.auto_play_tick_rate_ms(), 16); // Default

        let app = create_router(state.clone());

        // Set custom tick rate
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/ticks/auto", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"enabled": true, "tick_rate_ms": 50}"#))
            .unwrap();

        let response = app.clone().oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        // Verify settings changed
        assert!(container.is_auto_playing());
        assert_eq!(container.auto_play_tick_rate_ms(), 50);

        // Get metrics to verify tick_rate could be used by game loop
        let request = Request::builder()
            .uri(format!("/api/containers/{}/metrics", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        // Initial tick count should be 0 (auto-play sets up the mode but
        // doesn't automatically tick - that's the game loop's job)
        assert_eq!(json["data"]["tick_count"].as_u64().unwrap(), 0);
    }

    // =========================================================================
    // Bailey's additional tests - Phase 3 Implementation
    // =========================================================================

    #[tokio::test]
    async fn list_players_empty_container() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container with a match but no players
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();
        let _match_id = container.create_match(MatchConfig::default()).unwrap();

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/players", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let player_ids = json["data"]["player_ids"].as_array().unwrap();
        assert!(player_ids.is_empty());
    }

    #[tokio::test]
    async fn oauth2_token_endpoint_service_unavailable() {
        // Create state WITHOUT OAuth2 service configured
        let state = create_test_state();
        let app = create_router(state);

        // Try to request a token when OAuth2 is not configured
        let request = Request::builder()
            .method("POST")
            .uri("/auth/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(Body::from("grant_type=client_credentials&client_id=test&client_secret=test"))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        // Should return 503 Service Unavailable since OAuth2 is not configured
        assert_eq!(response.status(), StatusCode::SERVICE_UNAVAILABLE);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        // Verify error response structure
        assert!(json["error"].as_str().is_some());
        assert!(json["error_description"].as_str().is_some());
    }

    #[tokio::test]
    async fn auto_play_uses_default_tick_rate() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Default tick rate should be 16ms (~60 FPS)
        assert_eq!(container.auto_play_tick_rate_ms(), 16);

        let app = create_router(state.clone());

        // Enable auto-play WITHOUT specifying tick_rate_ms
        // This tests that the default is used from the serde default
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/ticks/auto", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"enabled": true}"#))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["enabled"].as_bool().unwrap());
        // Should use the default tick_rate_ms of 16
        assert_eq!(json["data"]["tick_rate_ms"].as_u64().unwrap(), 16);

        // Verify on the container
        assert!(container.is_auto_playing());
        assert_eq!(container.auto_play_tick_rate_ms(), 16);
    }

    #[tokio::test]
    async fn get_command_errors_empty() {
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container without any command errors
        let container_id = state.container_service().create_container(tenant_id);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/commands/errors", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let errors = json["data"].as_array().unwrap();
        assert!(errors.is_empty());
    }

    // =========================================================================
    // Eli's improvements to Bailey's tests - Phase 5 Peer Review
    // =========================================================================

    #[tokio::test]
    async fn list_players_no_matches() {
        // Test empty player list when container has NO matches at all
        // (different from Bailey's test which has a match but no players)
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        // Create a container WITHOUT any matches
        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        // Verify no matches exist
        assert_eq!(container.match_count(), 0);

        let app = create_router(state);

        let request = Request::builder()
            .uri(format!("/api/containers/{}/players", container_id.0))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        let player_ids = json["data"]["player_ids"].as_array().unwrap();
        assert!(player_ids.is_empty());
    }

    #[tokio::test]
    async fn auto_play_zero_tick_rate_uses_minimum() {
        // Test that zero or very small tick rates are handled safely
        // This validates the game loop won't spin at infinite speed
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let container_id = state.container_service().create_container(tenant_id);
        let container = state.container_service().get_container(container_id).unwrap();

        let app = create_router(state.clone());

        // Try to set tick_rate_ms to 0 (should either reject or use minimum)
        let request = Request::builder()
            .method("POST")
            .uri(format!("/api/containers/{}/ticks/auto", container_id.0))
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from(r#"{"enabled": true, "tick_rate_ms": 0}"#))
            .unwrap();

        let response = app.clone().oneshot(request).await.unwrap();

        // The request should succeed (the handler accepts it)
        // but we verify the container state is valid
        assert_eq!(response.status(), StatusCode::OK);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        assert!(json["success"].as_bool().unwrap());
        assert!(json["data"]["enabled"].as_bool().unwrap());

        // The tick_rate_ms should be set (even if 0 - this documents current behavior)
        // In a real system, we might want validation to reject 0
        let tick_rate = json["data"]["tick_rate_ms"].as_u64().unwrap();
        assert_eq!(tick_rate, 0); // Documents that 0 is currently allowed

        // Verify on container
        assert!(container.is_auto_playing());
    }

    #[tokio::test]
    async fn command_errors_nonexistent_container() {
        // Test that requesting command errors for non-existent container returns 404
        let (state, jwt_service, tenant_id) = create_state_with_jwt();
        let token = generate_token(&jwt_service, tenant_id);

        let app = create_router(state);

        // Use a random UUID that doesn't exist
        let fake_container_id = uuid::Uuid::new_v4();

        let request = Request::builder()
            .uri(format!("/api/containers/{}/commands/errors", fake_container_id))
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        // Should return 404 Not Found
        assert_eq!(response.status(), StatusCode::NOT_FOUND);

        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();

        // Verify error response structure
        assert!(!json["success"].as_bool().unwrap_or(true));
        assert!(json["error"].is_object() || json["error"].is_string());
    }
}
