//! REST API route handlers.
//!
//! Provides the main router with health check, container, and WebSocket endpoints.

use axum::{
    extract::State,
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use stormstack_core::ContainerId;
use stormstack_net::{ApiResponse, AuthUser};

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

/// Create the main application router.
pub fn create_router(state: SharedAppState) -> Router {
    Router::new()
        .route("/health", get(health_handler))
        .route("/api/containers", get(list_containers_handler))
        .route("/api/containers", post(create_container_handler))
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
/// Returns a list of containers (stub - returns empty list for now).
async fn list_containers_handler(
    State(_state): State<SharedAppState>,
) -> ApiResponse<Vec<ContainerSummary>> {
    // Stub: return empty list
    // TODO: Implement actual container listing
    ApiResponse::ok(vec![])
}

/// Create container endpoint.
///
/// POST /api/containers
///
/// Creates a new execution container (stub).
async fn create_container_handler(
    State(_state): State<SharedAppState>,
    _auth: AuthUser,
    Json(_request): Json<CreateContainerRequest>,
) -> ApiResponse<CreateContainerResponse> {
    // Stub: create a container ID but don't actually do anything
    // TODO: Implement actual container creation
    let container_id = ContainerId::new();

    ApiResponse::ok(CreateContainerResponse {
        id: container_id.to_string(),
        status: "created".to_string(),
    })
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
    async fn list_containers_returns_empty() {
        let state = create_test_state();
        let app = create_router(state);

        let request = Request::builder()
            .uri("/api/containers")
            .body(Body::empty())
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);
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
        // Should return 401 Unauthorized without auth header
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn create_container_with_auth() {
        let jwt_service = Arc::new(JwtService::new(b"test-secret-key-32-bytes-long!!"));
        let world = shared_world();
        let sandbox = Arc::new(WasmSandbox::new().expect("sandbox"));
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);

        let state = Arc::new(crate::state::AppState::new(
            jwt_service.clone(),
            world,
            sandbox,
            connections,
        ));

        let app = create_router(state);

        // Generate a valid token
        let claims = Claims::new(UserId::new(), TenantId::new(), vec!["user".to_string()]);
        let token = jwt_service.generate_token(&claims).expect("token");

        let request = Request::builder()
            .method("POST")
            .uri("/api/containers")
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Bearer {}", token))
            .body(Body::from("{}"))
            .unwrap();

        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);
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
}
