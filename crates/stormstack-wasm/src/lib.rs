//! # StormStack WASM Sandbox
//!
//! Secure WASM sandbox for executing untrusted game modules.
//!
//! ## Security Features
//!
//! - **Fuel metering**: Limits instruction count to prevent infinite loops
//! - **Epoch interruption**: Wall-clock timeout backup
//! - **Memory limits**: Prevents memory exhaustion attacks
//! - **Capability-based security**: Zero capabilities by default
//!
//! ## CRITICAL SECURITY NOTES
//!
//! This crate executes UNTRUSTED code from users. All security tests
//! MUST pass before any integration:
//!
//! - Memory escape attempts must be blocked
//! - Infinite loops must be terminated
//! - Memory bombs must be prevented
//! - Stack overflows must be handled
//! - Host function inputs must be validated
//!
//! See `docs/migration/WASM_HOST_FUNCTIONS.md` for the security model.

#![warn(missing_docs)]
#![warn(clippy::all)]
#![forbid(unsafe_code)]

pub mod sandbox;
pub mod limits;

pub use limits::WasmResourceLimits;
pub use sandbox::{WasmModule, WasmSandbox, WasmInstance};

// TODO: Implement WASM sandbox
// SECURITY CRITICAL - Write tests FIRST (TDD)
//
// Required tests (in order):
// 1. test_memory_escape_blocked
// 2. test_infinite_loop_terminated
// 3. test_memory_bomb_prevented
// 4. test_stack_overflow_handled
// 5. test_host_function_validation
// 6. test_fuel_exhausted
// 7. test_epoch_deadline_exceeded
// 8. test_valid_module_executes
