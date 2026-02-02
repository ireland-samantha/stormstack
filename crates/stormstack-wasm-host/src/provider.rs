//! Host function provider trait.

use crate::functions::register_host_functions;
use crate::state::WasmState;
use stormstack_core::WasmError;
use wasmtime::Linker;

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
    fn register(&self, linker: &mut Linker<WasmState>) -> Result<(), WasmError>;

    /// Name of this provider (for debugging/logging).
    fn name(&self) -> &'static str;
}

/// Core host function provider.
///
/// Provides the standard set of host functions:
/// - Logging: `log_debug`, `log_info`, `log_warn`, `log_error`
/// - Time: `get_tick`, `get_delta_time`
/// - Entity: `entity_spawn`, `entity_despawn`, `entity_exists`
/// - Random: `random_u32`, `random_f32`, `random_range`
pub struct CoreHostFunctions;

impl HostFunctionProvider for CoreHostFunctions {
    fn register(&self, linker: &mut Linker<WasmState>) -> Result<(), WasmError> {
        register_host_functions(linker)
    }

    fn name(&self) -> &'static str {
        "core"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wasmtime::Engine;

    #[test]
    fn core_provider_name() {
        let provider = CoreHostFunctions;
        assert_eq!(provider.name(), "core");
    }

    #[test]
    fn core_provider_register() {
        let engine = Engine::default();
        let mut linker = Linker::new(&engine);
        let provider = CoreHostFunctions;
        assert!(provider.register(&mut linker).is_ok());
    }
}
