//! Host function provider trait.

use stormstack_core::WasmError;

/// Provider of host functions to WASM modules.
///
/// Implementations register functions with the wasmtime Linker,
/// making them available to WASM modules.
///
/// # Security
///
/// All providers must:
/// - Validate all inputs from WASM
/// - Scope operations to the calling tenant
/// - Respect rate limits
/// - Document functions in WASM_HOST_FUNCTIONS.md
pub trait HostFunctionProvider: Send + Sync {
    /// Register host functions with the linker.
    ///
    /// # Errors
    ///
    /// Returns an error if registration fails.
    fn register(
        &self,
        // TODO: linker: &mut wasmtime::Linker<WasmState>,
    ) -> Result<(), WasmError>;

    /// Name of this provider (for debugging/logging).
    fn name(&self) -> &'static str;
}

/// Core host function provider.
///
/// Provides the standard set of host functions:
/// - Logging
/// - Time access
/// - Entity operations
/// - Component operations
/// - Random numbers
/// - Entity queries
pub struct CoreHostFunctions;

impl HostFunctionProvider for CoreHostFunctions {
    fn register(&self) -> Result<(), WasmError> {
        // TODO: Register all core host functions
        Ok(())
    }

    fn name(&self) -> &'static str {
        "core"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_provider_name() {
        let provider = CoreHostFunctions;
        assert_eq!(provider.name(), "core");
    }

    #[test]
    fn core_provider_register() {
        let provider = CoreHostFunctions;
        assert!(provider.register().is_ok());
    }
}
