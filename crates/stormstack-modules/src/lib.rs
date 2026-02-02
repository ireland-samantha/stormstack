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
//! - Dependency resolution and version checking
//! - Thread-safe module management
//!
//! ## Module ABI Requirements
//!
//! Modules must:
//! 1. Be compiled with the same Rust version as the host
//! 2. Export a `_stormstack_module_create` function returning `*mut dyn Module`
//! 3. Implement the `Module` trait
//! 4. Match the current `MODULE_ABI_VERSION`
//!
//! ## Example
//!
//! ```ignore
//! use stormstack_modules::{Module, ModuleContext, ModuleDescriptor, ModuleLoader};
//! use stormstack_core::Result;
//! use std::path::Path;
//!
//! // Define a module
//! pub struct MyModule;
//!
//! impl Module for MyModule {
//!     fn descriptor(&self) -> &'static ModuleDescriptor {
//!         static DESC: ModuleDescriptor = ModuleDescriptor::new(
//!             "my-module", "1.0.0", "Example module"
//!         );
//!         &DESC
//!     }
//!
//!     fn on_load(&mut self, ctx: &mut ModuleContext) -> Result<()> {
//!         println!("Module loaded!");
//!         Ok(())
//!     }
//!
//!     fn on_tick(&mut self, ctx: &mut ModuleContext) -> Result<()> {
//!         Ok(())
//!     }
//!
//!     fn on_unload(&mut self) -> Result<()> {
//!         println!("Module unloaded!");
//!         Ok(())
//!     }
//! }
//!
//! // Export the module creation function
//! #[no_mangle]
//! pub extern "C" fn _stormstack_module_create() -> *mut dyn Module {
//!     Box::into_raw(Box::new(MyModule))
//! }
//!
//! // Load the module at runtime
//! fn main() -> Result<()> {
//!     let mut loader = ModuleLoader::new();
//!     loader.load(Path::new("./target/release/libmy_module.so"))?;
//!     Ok(())
//! }
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod descriptor;
pub mod loader;
pub mod module_trait;
pub mod registry;

pub use descriptor::{ModuleDependency, ModuleDescriptor, MODULE_ABI_VERSION};
pub use loader::{shared_loader, LoadedModule, ModuleLoader, SharedModuleLoader};
pub use module_trait::{
    Module, ModuleContext, ModuleCreateFn, ModuleDestroyFn, MODULE_CREATE_SYMBOL,
    MODULE_DESTROY_SYMBOL,
};
pub use registry::{ModuleRegistry, RegistryEntry};

// Collect all registered modules via inventory
inventory::collect!(ModuleDescriptor);

/// Discover all statically registered modules.
///
/// This function uses the `inventory` crate to collect all module descriptors
/// that have been registered via `inventory::submit!`.
///
/// # Example
///
/// ```ignore
/// inventory::submit! {
///     ModuleDescriptor::new("my-module", "1.0.0", "My module")
/// }
///
/// // Later:
/// let modules = discover_modules();
/// for module in modules {
///     println!("Found module: {} v{}", module.name, module.version);
/// }
/// ```
#[must_use]
pub fn discover_modules() -> Vec<&'static ModuleDescriptor> {
    inventory::iter::<ModuleDescriptor>().collect()
}

/// Declare a module for static registration.
///
/// This macro simplifies the process of registering a module with the
/// inventory crate.
///
/// # Example
///
/// ```ignore
/// declare_module!("my-module", "1.0.0", "My game module");
/// ```
#[macro_export]
macro_rules! declare_module {
    ($name:expr, $version:expr, $description:expr) => {
        inventory::submit! {
            $crate::ModuleDescriptor::new($name, $version, $description)
        }
    };
    ($name:expr, $version:expr, $description:expr, [$($dep:expr),* $(,)?]) => {
        inventory::submit! {
            $crate::ModuleDescriptor::with_dependencies(
                $name,
                $version,
                $description,
                &[$($dep),*]
            )
        }
    };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn discover_modules_returns_vec() {
        let modules = discover_modules();
        // May or may not have modules depending on what's linked
        // Just verify it returns a vec without panicking
        let _ = modules.len();
    }

    #[test]
    fn module_abi_version_is_defined() {
        assert!(MODULE_ABI_VERSION > 0);
    }

    #[test]
    fn module_symbols_are_valid_c_strings() {
        // Check that symbols are null-terminated
        assert!(MODULE_CREATE_SYMBOL.ends_with(&[0]));
        assert!(MODULE_DESTROY_SYMBOL.ends_with(&[0]));
    }
}
