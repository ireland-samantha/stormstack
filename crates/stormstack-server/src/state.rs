//! Application state for axum handlers.
//!
//! Provides shared state that implements the `AuthState` trait
//! required by stormstack-net extractors.

use std::sync::Arc;

use stormstack_auth::JwtService;
use stormstack_ecs::SharedWorld;
use stormstack_net::AuthState;
use stormstack_wasm::WasmSandbox;
use stormstack_ws::SharedConnectionManager;

/// Shared application state type alias.
pub type SharedAppState = Arc<AppState>;

/// Application state for axum handlers.
///
/// Holds references to all core services and implements `AuthState`
/// for JWT extraction in request handlers.
#[derive(Clone)]
pub struct AppState {
    /// JWT authentication service.
    jwt_service: Arc<JwtService>,
    /// Shared ECS world.
    world: SharedWorld,
    /// WASM sandbox for module execution.
    sandbox: Arc<WasmSandbox>,
    /// WebSocket connection manager.
    connections: SharedConnectionManager,
}

impl AppState {
    /// Create new application state.
    pub fn new(
        jwt_service: Arc<JwtService>,
        world: SharedWorld,
        sandbox: Arc<WasmSandbox>,
        connections: SharedConnectionManager,
    ) -> Self {
        Self {
            jwt_service,
            world,
            sandbox,
            connections,
        }
    }

    /// Get the JWT service.
    #[must_use]
    pub fn jwt(&self) -> &JwtService {
        &self.jwt_service
    }

    /// Get the shared ECS world.
    #[must_use]
    pub fn world(&self) -> &SharedWorld {
        &self.world
    }

    /// Get the WASM sandbox.
    #[must_use]
    pub fn sandbox(&self) -> &Arc<WasmSandbox> {
        &self.sandbox
    }

    /// Get the connection manager.
    #[must_use]
    pub fn connections(&self) -> &SharedConnectionManager {
        &self.connections
    }
}

// Implement AuthState trait for JWT extraction
impl AuthState for AppState {
    fn jwt_service(&self) -> &JwtService {
        &self.jwt_service
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_ecs::shared_world;
    use stormstack_ws::{shared_connection_manager, shared_subscriptions};

    fn create_test_state() -> AppState {
        let jwt_service = Arc::new(JwtService::new(b"test-secret-key-32-bytes-long!!"));
        let world = shared_world();
        let sandbox = Arc::new(WasmSandbox::new().expect("sandbox"));
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);

        AppState::new(jwt_service, world, sandbox, connections)
    }

    #[test]
    fn app_state_creation() {
        let state = create_test_state();
        assert_eq!(state.world().read().entity_count(), 0);
    }

    #[test]
    fn app_state_jwt_accessible() {
        let state = create_test_state();
        // JWT service should be accessible
        let _ = state.jwt();
    }

    #[test]
    fn app_state_implements_auth_state() {
        let state = create_test_state();
        // Should be able to call AuthState method
        let _ = AuthState::jwt_service(&state);
    }

    #[test]
    fn shared_state_is_clone() {
        let state = create_test_state();
        let shared: SharedAppState = Arc::new(state);
        let _cloned = shared.clone();
    }
}
