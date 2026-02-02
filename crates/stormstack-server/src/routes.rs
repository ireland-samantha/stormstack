//! REST API route handlers.
//!
//! Provides the main router with health check, container, match, and WebSocket endpoints.

use axum::{
    extract::{Path, State},
    routing::{delete, get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use stormstack_core::{ContainerId, MatchConfig, MatchId};
use stormstack_net::{ApiError, ApiResponse, AuthUser};
use uuid::Uuid;

use crate::container::MatchState;
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

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use stormstack_auth::{Claims, JwtService};
    use stormstack_core::{TenantId, UserId};
    use stormstack_ecs::shared_world;
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
}
