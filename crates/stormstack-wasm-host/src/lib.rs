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
//! - Component: `component_get`, `component_set`, `component_has`
//! - Random: `random_u32`, `random_f32`, `random_range`
//! - Query: `query_entities`

#![warn(missing_docs)]
#![warn(clippy::all)]
#![forbid(unsafe_code)]

pub mod provider;
pub mod state;

pub use provider::HostFunctionProvider;
pub use state::WasmState;

// TODO: Implement host functions
// See docs/migration/WASM_HOST_FUNCTIONS.md for specifications
