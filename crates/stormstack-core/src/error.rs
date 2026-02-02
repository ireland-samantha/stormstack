//! Error types for StormStack.
//!
//! Provides a unified error hierarchy:
//! - `StormError`: Top-level error type
//! - `AuthError`: Authentication/authorization errors
//! - `WasmError`: WASM sandbox execution errors

use crate::id::{ConnectionId, ContainerId, EntityId, MatchId, UserId};
use thiserror::Error;

/// Top-level error type for StormStack operations.
#[derive(Debug, Error)]
pub enum StormError {
    /// Entity was not found in the ECS world.
    #[error("Entity not found: {0}")]
    EntityNotFound(EntityId),

    /// Container was not found.
    #[error("Container not found: {0}")]
    ContainerNotFound(ContainerId),

    /// Match was not found.
    #[error("Match not found: {0}")]
    MatchNotFound(MatchId),

    /// WebSocket connection was not found.
    #[error("Connection not found: {0}")]
    ConnectionNotFound(ConnectionId),

    /// WebSocket connection was closed.
    #[error("Connection closed: {0}")]
    ConnectionClosed(ConnectionId),

    /// Authentication or authorization error.
    #[error("Authentication failed: {0}")]
    Auth(#[from] AuthError),

    /// WASM execution error.
    #[error("WASM execution failed: {0}")]
    Wasm(#[from] WasmError),

    /// Native module error.
    #[error("Module error: {0}")]
    Module(#[from] ModuleError),

    /// Invalid state transition or operation.
    #[error("Invalid state: {0}")]
    InvalidState(String),

    /// Resource limit exceeded.
    #[error("Resource exhausted: {0}")]
    ResourceExhausted(String),

    /// Serialization/deserialization error.
    #[error("Serialization error: {0}")]
    Serialization(String),

    /// Configuration error.
    #[error("Configuration error: {0}")]
    Configuration(String),

    /// Internal error (catch-all).
    #[error("Internal error: {0}")]
    Internal(#[from] anyhow::Error),
}

/// Authentication and authorization errors.
#[derive(Debug, Error)]
pub enum AuthError {
    /// JWT token is invalid or malformed.
    #[error("Invalid token: {0}")]
    InvalidToken(String),

    /// JWT token has expired.
    #[error("Token expired")]
    TokenExpired,

    /// Credentials (username/password) are invalid.
    #[error("Invalid credentials")]
    InvalidCredentials,

    /// User lacks required permission.
    #[error("Access denied: {0}")]
    AccessDenied(String),

    /// User was not found.
    #[error("User not found: {0}")]
    UserNotFound(UserId),

    /// Password hashing failed.
    #[error("Password hashing failed: {0}")]
    HashingFailed(String),
}

/// Native module system errors.
///
/// These errors indicate issues with loading, unloading,
/// or managing native dynamic library modules.
#[derive(Debug, Error)]
pub enum ModuleError {
    /// Failed to load a dynamic library.
    #[error("Failed to load module '{name}': {reason}")]
    LoadFailed {
        /// Module name.
        name: String,
        /// Failure reason.
        reason: String,
    },

    /// Failed to unload a module.
    #[error("Failed to unload module '{name}': {reason}")]
    UnloadFailed {
        /// Module name.
        name: String,
        /// Failure reason.
        reason: String,
    },

    /// Module was not found in the registry.
    #[error("Module not found: {0}")]
    NotFound(String),

    /// Module is already loaded.
    #[error("Module already loaded: {0}")]
    AlreadyLoaded(String),

    /// Module symbol not found.
    #[error("Symbol not found in module '{module}': {symbol}")]
    SymbolNotFound {
        /// Module name.
        module: String,
        /// Symbol name.
        symbol: String,
    },

    /// Module version conflict.
    #[error("Module version conflict: '{name}' requires {required}, but {found} is loaded")]
    VersionConflict {
        /// Module name.
        name: String,
        /// Required version.
        required: String,
        /// Found version.
        found: String,
    },

    /// Dependency not satisfied.
    #[error("Dependency not satisfied for module '{module}': requires '{dependency}'")]
    DependencyNotSatisfied {
        /// Module that has the dependency.
        module: String,
        /// Missing dependency.
        dependency: String,
    },

    /// Circular dependency detected.
    #[error("Circular dependency detected involving module: {0}")]
    CircularDependency(String),

    /// ABI version mismatch.
    #[error("ABI version mismatch: module has {module_abi}, expected {expected_abi}")]
    AbiMismatch {
        /// Module's ABI version.
        module_abi: u32,
        /// Expected ABI version.
        expected_abi: u32,
    },

    /// Module is still in use and cannot be unloaded.
    #[error("Module '{0}' is still in use")]
    InUse(String),
}

/// WASM sandbox execution errors.
///
/// These errors indicate issues with WASM module execution,
/// including security-related resource exhaustion.
#[derive(Debug, Error)]
pub enum WasmError {
    /// WASM module failed to compile.
    #[error("Failed to compile module: {0}")]
    CompilationError(String),

    /// WASM module failed to instantiate.
    #[error("Failed to instantiate module: {0}")]
    InstantiationError(String),

    /// Fuel (instruction count) limit exceeded.
    #[error("Fuel exhausted after {consumed} fuel units")]
    FuelExhausted {
        /// Amount of fuel consumed before exhaustion.
        consumed: u64,
    },

    /// Epoch deadline (wall-clock time) exceeded.
    #[error("Epoch deadline exceeded")]
    EpochDeadlineExceeded,

    /// Memory allocation limit exceeded.
    #[error("Memory limit exceeded: requested {requested} bytes, limit {limit} bytes")]
    MemoryLimitExceeded {
        /// Requested allocation size.
        requested: usize,
        /// Maximum allowed size.
        limit: usize,
    },

    /// Requested function not found in module.
    #[error("Function not found: {0}")]
    FunctionNotFound(String),

    /// Type mismatch in function call.
    #[error("Type mismatch: expected {expected}, got {actual}")]
    TypeMismatch {
        /// Expected type description.
        expected: String,
        /// Actual type description.
        actual: String,
    },

    /// WASM trap (runtime error).
    #[error("Trap: {0}")]
    Trap(String),

    /// Invalid input received from WASM module.
    #[error("Invalid input from WASM: {0}")]
    InvalidInput(String),

    /// Stack overflow in WASM execution.
    #[error("Stack overflow")]
    StackOverflow,
}

impl WasmError {
    /// Check if this error is a resource exhaustion error.
    ///
    /// Resource exhaustion errors indicate the WASM module hit
    /// a security limit rather than having a bug.
    #[must_use]
    pub fn is_resource_exhaustion(&self) -> bool {
        matches!(
            self,
            WasmError::FuelExhausted { .. }
                | WasmError::EpochDeadlineExceeded
                | WasmError::MemoryLimitExceeded { .. }
                | WasmError::StackOverflow
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wasm_error_resource_exhaustion() {
        assert!(WasmError::FuelExhausted { consumed: 1000 }.is_resource_exhaustion());
        assert!(WasmError::EpochDeadlineExceeded.is_resource_exhaustion());
        assert!(WasmError::MemoryLimitExceeded {
            requested: 100,
            limit: 50
        }
        .is_resource_exhaustion());
        assert!(WasmError::StackOverflow.is_resource_exhaustion());

        assert!(!WasmError::FunctionNotFound("test".to_string()).is_resource_exhaustion());
        assert!(!WasmError::Trap("div by zero".to_string()).is_resource_exhaustion());
    }

    #[test]
    fn storm_error_from_auth() {
        let auth_err = AuthError::TokenExpired;
        let storm_err: StormError = auth_err.into();
        assert!(matches!(storm_err, StormError::Auth(AuthError::TokenExpired)));
    }

    #[test]
    fn storm_error_from_wasm() {
        let wasm_err = WasmError::FuelExhausted { consumed: 500 };
        let storm_err: StormError = wasm_err.into();
        assert!(matches!(
            storm_err,
            StormError::Wasm(WasmError::FuelExhausted { consumed: 500 })
        ));
    }
}
