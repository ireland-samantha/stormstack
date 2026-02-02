//! # StormStack Modules
//!
//! Native hot-reload module system for TRUSTED code.
//!
//! ## Important Security Note
//!
//! This module system is for INTERNAL/DEVELOPER use only.
//! It loads native code that has full system access.
//!
//! For UNTRUSTED user-uploaded code, use the WASM sandbox
//! in `stormstack-wasm` instead.
//!
//! ## Features
//!
//! - Dynamic library loading via libloading
//! - Hot reload without server restart
//! - Module discovery via inventory crate

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod loader;
pub mod descriptor;

pub use descriptor::ModuleDescriptor;
pub use loader::ModuleLoader;

// Collect all registered modules
inventory::collect!(ModuleDescriptor);

/// Discover all registered modules.
#[must_use]
pub fn discover_modules() -> Vec<&'static ModuleDescriptor> {
    inventory::iter::<ModuleDescriptor>().collect()
}
