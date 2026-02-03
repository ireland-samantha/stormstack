//! Application state for axum handlers.
//!
//! Provides shared state that implements the `AuthState` trait
//! required by stormstack-net extractors.

use std::sync::Arc;

use parking_lot::RwLock;
use stormstack_auth::{JwtService, OAuth2Service};
use stormstack_ecs::SharedWorld;
use stormstack_net::AuthState;
use stormstack_wasm::WasmSandbox;
use stormstack_ws::SharedConnectionManager;

use crate::commands::{shared_command_registry, SharedCommandRegistry};
use crate::container::{shared_container_service, SharedContainerService};
use crate::resources::{shared_file_storage, SharedResourceStorage};

/// Shared application state type alias.
pub type SharedAppState = Arc<AppState>;

/// Shared OAuth2 service type alias.
pub type SharedOAuth2Service = Arc<RwLock<OAuth2Service>>;

/// Application state for axum handlers.
///
/// Holds references to all core services and implements `AuthState`
/// for JWT extraction in request handlers.
#[derive(Clone)]
pub struct AppState {
    /// JWT authentication service.
    jwt_service: Arc<JwtService>,
    /// OAuth2 token service.
    oauth2_service: Option<SharedOAuth2Service>,
    /// Shared ECS world.
    world: SharedWorld,
    /// WASM sandbox for module execution.
    sandbox: Arc<WasmSandbox>,
    /// WebSocket connection manager.
    connections: SharedConnectionManager,
    /// Container service for managing execution containers.
    container_service: SharedContainerService,
    /// Command registry for mapping command names to factories.
    command_registry: SharedCommandRegistry,
    /// Resource storage for game assets.
    resource_storage: SharedResourceStorage,
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
            oauth2_service: None,
            world,
            sandbox,
            connections,
            container_service: shared_container_service(),
            command_registry: shared_command_registry(),
            resource_storage: shared_file_storage(),
        }
    }

    /// Create application state with OAuth2 service.
    pub fn with_oauth2(
        jwt_service: Arc<JwtService>,
        oauth2_service: SharedOAuth2Service,
        world: SharedWorld,
        sandbox: Arc<WasmSandbox>,
        connections: SharedConnectionManager,
    ) -> Self {
        Self {
            jwt_service,
            oauth2_service: Some(oauth2_service),
            world,
            sandbox,
            connections,
            container_service: shared_container_service(),
            command_registry: shared_command_registry(),
            resource_storage: shared_file_storage(),
        }
    }

    /// Create application state with a custom command registry.
    pub fn with_command_registry(
        jwt_service: Arc<JwtService>,
        world: SharedWorld,
        sandbox: Arc<WasmSandbox>,
        connections: SharedConnectionManager,
        command_registry: SharedCommandRegistry,
    ) -> Self {
        Self {
            jwt_service,
            oauth2_service: None,
            world,
            sandbox,
            connections,
            container_service: shared_container_service(),
            command_registry,
            resource_storage: shared_file_storage(),
        }
    }

    /// Get the resource storage.
    #[must_use]
    pub fn resource_storage(&self) -> &SharedResourceStorage {
        &self.resource_storage
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

    /// Get the container service.
    #[must_use]
    pub fn container_service(&self) -> &SharedContainerService {
        &self.container_service
    }

    /// Get the OAuth2 service.
    #[must_use]
    pub fn oauth2(&self) -> Option<&SharedOAuth2Service> {
        self.oauth2_service.as_ref()
    }

    /// Get the command registry.
    #[must_use]
    pub fn command_registry(&self) -> &SharedCommandRegistry {
        &self.command_registry
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
