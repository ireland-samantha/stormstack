//! # StormStack WASM Host Functions
//!
//! Provides host functions that WASM modules can call.
//!
//! ## Security Model
//!
//! All host functions:
//! - Validate all inputs from WASM
//! - Are tenant-scoped (cannot access other tenants' data)
//! - Have rate limits where applicable
//! - Are documented in `docs/migration/WASM_HOST_FUNCTIONS.md`
//!
//! ## Available Functions
//!
//! - Logging: `log_debug`, `log_info`, `log_warn`, `log_error`
//! - Time: `get_tick`, `get_delta_time`
//! - Entity: `entity_spawn`, `entity_despawn`, `entity_exists`
//! - Random: `random_u32`, `random_f32`, `random_range`
//!
//! ## Example
//!
//! ```rust,ignore
//! use stormstack_wasm_host::{CoreHostFunctions, HostFunctionProvider, WasmState};
//! use wasmtime::{Engine, Linker};
//!
//! let engine = Engine::default();
//! let mut linker = Linker::new(&engine);
//!
//! // Register host functions
//! let provider = CoreHostFunctions;
//! provider.register(&mut linker)?;
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod functions;
pub mod provider;
pub mod state;

pub use functions::register_host_functions;
pub use provider::{CoreHostFunctions, HostFunctionProvider};
pub use state::{LogEntry, LogLevel, RateLimits, WasmState};
