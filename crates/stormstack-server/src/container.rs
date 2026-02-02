//! Execution container management.

use stormstack_core::{ContainerId, MatchId, Result, TenantId};

/// Execution container for isolated game instances.
pub struct Container {
    id: ContainerId,
    tenant_id: TenantId,
    // TODO: Add ECS world, WASM instances, etc.
}

impl Container {
    /// Create a new container.
    #[must_use]
    pub fn new(tenant_id: TenantId) -> Self {
        Self {
            id: ContainerId::new(),
            tenant_id,
        }
    }

    /// Get container ID.
    #[must_use]
    pub fn id(&self) -> ContainerId {
        self.id
    }

    /// Get tenant ID.
    #[must_use]
    pub fn tenant_id(&self) -> TenantId {
        self.tenant_id
    }

    /// Create a match in this container.
    ///
    /// # Errors
    ///
    /// Returns an error if match creation fails.
    pub fn create_match(&mut self, _config: stormstack_core::MatchConfig) -> Result<MatchId> {
        // TODO: Implement
        Ok(MatchId::new())
    }

    /// Execute a tick for all matches.
    ///
    /// # Errors
    ///
    /// Returns an error if tick execution fails.
    pub fn tick(&mut self) -> Result<()> {
        // TODO: Implement
        Ok(())
    }
}

// Re-export for convenience
pub use stormstack_core::MatchConfig as Config;
