//! Test harness for isolated testing.

use stormstack_core::TenantId;
use stormstack_ecs::{EcsWorld, StormWorld};

/// Test harness providing an isolated test environment.
pub struct TestHarness {
    /// Tenant ID for this test.
    pub tenant_id: TenantId,
    /// ECS world for this test.
    pub world: StormWorld,
}

impl TestHarness {
    /// Create a new test harness.
    #[must_use]
    pub fn new() -> Self {
        Self {
            tenant_id: TenantId::new(),
            world: StormWorld::new(),
        }
    }

    /// Create a test harness with a specific tenant.
    #[must_use]
    pub fn with_tenant(tenant_id: TenantId) -> Self {
        Self {
            tenant_id,
            world: StormWorld::new(),
        }
    }
}

impl Default for TestHarness {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn harness_creates_world() {
        let harness = TestHarness::new();
        assert_eq!(harness.world.tick(), 0);
    }
}
