//! Dynamic module loader using libloading.
//!
//! Provides functionality for loading, unloading, and hot-reloading
//! native dynamic library modules at runtime.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use libloading::{Library, Symbol};
use parking_lot::RwLock;
use stormstack_core::{ModuleError, Result, StormError};
use stormstack_ecs::StormWorld;
use tracing::{debug, error, info, warn};

use crate::descriptor::MODULE_ABI_VERSION;
use crate::module_trait::{Module, ModuleContext, ModuleCreateFn, MODULE_CREATE_SYMBOL};

/// Information about a loaded module.
#[derive(Debug)]
pub struct LoadedModule {
    /// Module name.
    pub name: String,
    /// Module version.
    pub version: String,
    /// Path the module was loaded from.
    pub path: PathBuf,
    /// Whether the module is currently active (initialized).
    pub active: bool,
}

/// Internal representation of a loaded module.
struct ModuleInstance {
    /// The loaded dynamic library (must be kept alive).
    _library: Library,
    /// The module instance.
    module: Box<dyn Module>,
    /// Path the module was loaded from.
    path: PathBuf,
    /// Whether on_load has been called successfully.
    initialized: bool,
}

/// Module loader for dynamic libraries.
///
/// Manages the lifecycle of native modules including loading,
/// unloading, and hot-reloading.
///
/// # Thread Safety
///
/// The loader is thread-safe and can be used from multiple threads.
/// It uses interior mutability with `RwLock` for thread-safe access.
///
/// # Example
///
/// ```ignore
/// use stormstack_modules::ModuleLoader;
/// use std::path::Path;
///
/// let mut loader = ModuleLoader::new();
/// loader.load(Path::new("./target/release/libmy_module.so"))?;
///
/// // Later, reload with a new version
/// loader.reload("my-module", Path::new("./target/release/libmy_module.so"))?;
///
/// // Clean up
/// loader.unload("my-module")?;
/// ```
pub struct ModuleLoader {
    /// Loaded modules by name.
    modules: HashMap<String, ModuleInstance>,
    /// Load order for deterministic iteration.
    load_order: Vec<String>,
}

impl ModuleLoader {
    /// Create a new module loader.
    #[must_use]
    pub fn new() -> Self {
        debug!("Creating new ModuleLoader");
        Self {
            modules: HashMap::new(),
            load_order: Vec::new(),
        }
    }

    /// Load a module from a dynamic library.
    ///
    /// # Safety
    ///
    /// This loads and executes native code. Only use with trusted modules
    /// compiled from known source code.
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - The library cannot be loaded
    /// - The module creation symbol is not found
    /// - The module's ABI version is incompatible
    /// - A module with the same name is already loaded
    pub fn load(&mut self, path: &Path) -> Result<LoadedModule> {
        info!("Loading module from {:?}", path);

        // Load the library
        let library = unsafe {
            Library::new(path).map_err(|e| {
                StormError::Module(ModuleError::LoadFailed {
                    name: path.display().to_string(),
                    reason: e.to_string(),
                })
            })?
        };

        // Get the module creation function
        let create_fn: Symbol<ModuleCreateFn> = unsafe {
            library
                .get(MODULE_CREATE_SYMBOL)
                .map_err(|_e| {
                    StormError::Module(ModuleError::SymbolNotFound {
                        module: path.display().to_string(),
                        symbol: "_stormstack_module_create".to_string(),
                    })
                })?
        };

        // Create the module instance
        let module_ptr = unsafe { create_fn() };
        if module_ptr.is_null() {
            return Err(StormError::Module(ModuleError::LoadFailed {
                name: path.display().to_string(),
                reason: "Module creation function returned null".to_string(),
            }));
        }

        let module = unsafe { Box::from_raw(module_ptr) };
        let descriptor = module.descriptor();

        // Check ABI version
        if descriptor.abi_version != MODULE_ABI_VERSION {
            return Err(StormError::Module(ModuleError::AbiMismatch {
                module_abi: descriptor.abi_version,
                expected_abi: MODULE_ABI_VERSION,
            }));
        }

        let name = descriptor.name.to_string();
        let version = descriptor.version.to_string();

        // Check for duplicate
        if self.modules.contains_key(&name) {
            return Err(StormError::Module(ModuleError::AlreadyLoaded(name)));
        }

        debug!(
            "Module '{}' v{} loaded successfully (ABI v{})",
            name, version, descriptor.abi_version
        );

        // Create result before inserting (to avoid borrow issues)
        let result = LoadedModule {
            name: name.clone(),
            version: version.clone(),
            path: path.to_path_buf(),
            active: false,
        };

        // Store the module
        self.modules.insert(
            name.clone(),
            ModuleInstance {
                _library: library,
                module,
                path: path.to_path_buf(),
                initialized: false,
            },
        );
        self.load_order.push(name);

        // Return info about the loaded module
        Ok(result)
    }

    /// Initialize a loaded module by calling its on_load callback.
    ///
    /// # Errors
    ///
    /// Returns an error if the module is not found or initialization fails.
    pub fn initialize(&mut self, name: &str, world: &mut StormWorld) -> Result<()> {
        // First, check if already initialized (immutable borrow)
        {
            let instance = self.modules.get(name).ok_or_else(|| {
                StormError::Module(ModuleError::NotFound(name.to_string()))
            })?;

            if instance.initialized {
                debug!("Module '{}' already initialized", name);
                return Ok(());
            }
        }

        // Collect dependency info (immutable borrow)
        let dependency_checks: Vec<(String, String, crate::ModuleDependency)> = {
            let instance = self.modules.get(name).unwrap();
            instance
                .module
                .descriptor()
                .dependencies
                .iter()
                .map(|d| (d.name.to_string(), d.min_version.map(|s| s.to_string()).unwrap_or_default(), d.clone()))
                .collect()
        };

        // Check dependencies (immutable borrow)
        for (dep_name, _, required) in &dependency_checks {
            let dep = self.modules.get(dep_name).ok_or_else(|| {
                StormError::Module(ModuleError::DependencyNotSatisfied {
                    module: name.to_string(),
                    dependency: dep_name.clone(),
                })
            })?;

            if !dep.initialized {
                return Err(StormError::Module(ModuleError::DependencyNotSatisfied {
                    module: name.to_string(),
                    dependency: dep_name.clone(),
                }));
            }

            let dep_version = dep.module.descriptor().version;
            if !required.satisfies(dep_version) {
                return Err(StormError::Module(ModuleError::VersionConflict {
                    name: dep_name.clone(),
                    required: format!("{:?}", required),
                    found: dep_version.to_string(),
                }));
            }
        }

        // Initialize the module (mutable borrow)
        {
            let mut ctx = ModuleContext::new(world, 0, 0.0);
            let instance = self.modules.get_mut(name).unwrap();
            instance.module.on_load(&mut ctx)?;
            instance.initialized = true;
        }

        info!("Module '{}' initialized", name);

        // Notify other modules
        let other_names: Vec<_> = self
            .modules
            .keys()
            .filter(|k| *k != name)
            .cloned()
            .collect();

        for other_name in other_names {
            if let Some(other) = self.modules.get_mut(&other_name)
                && other.initialized
                    && let Err(e) = other.module.on_dependency_loaded(name) {
                        warn!(
                            "Module '{}' failed to handle dependency load: {}",
                            other_name, e
                        );
                    }
        }

        Ok(())
    }

    /// Tick all initialized modules.
    ///
    /// Calls `on_tick` on each module in load order.
    ///
    /// # Errors
    ///
    /// Returns the first error encountered. Processing continues for all modules.
    pub fn tick_all(&mut self, world: &mut StormWorld, tick: u64, delta_time: f64) -> Result<()> {
        let mut first_error = None;

        for name in &self.load_order.clone() {
            if let Some(instance) = self.modules.get_mut(name)
                && instance.initialized {
                    let mut ctx = ModuleContext::new(world, tick, delta_time);
                    if let Err(e) = instance.module.on_tick(&mut ctx) {
                        error!("Module '{}' tick failed: {}", name, e);
                        if first_error.is_none() {
                            first_error = Some(e);
                        }
                    }
                }
        }

        if let Some(e) = first_error {
            return Err(e);
        }

        Ok(())
    }

    /// Unload a module.
    ///
    /// Calls `on_unload` and then drops the module and library.
    ///
    /// # Errors
    ///
    /// Returns an error if the module is not found or unload fails.
    pub fn unload(&mut self, name: &str) -> Result<()> {
        info!("Unloading module '{}'", name);

        // Check if any other module depends on this one
        for (other_name, other) in &self.modules {
            if other_name == name {
                continue;
            }
            for dep in other.module.descriptor().dependencies {
                if dep.name == name {
                    return Err(StormError::Module(ModuleError::InUse(name.to_string())));
                }
            }
        }

        // Notify other modules before unloading
        let other_names: Vec<_> = self
            .modules
            .keys()
            .filter(|k| *k != name)
            .cloned()
            .collect();

        for other_name in other_names {
            if let Some(other) = self.modules.get_mut(&other_name)
                && other.initialized
                    && let Err(e) = other.module.on_dependency_unloading(name) {
                        warn!(
                            "Module '{}' failed to handle dependency unload: {}",
                            other_name, e
                        );
                    }
        }

        // Remove and unload
        let mut instance = self.modules.remove(name).ok_or_else(|| {
            StormError::Module(ModuleError::NotFound(name.to_string()))
        })?;

        self.load_order.retain(|n| n != name);

        if instance.initialized
            && let Err(e) = instance.module.on_unload() {
                error!("Module '{}' on_unload failed: {}", name, e);
                // Continue with unload anyway
            }

        // Drop happens automatically
        debug!("Module '{}' unloaded", name);
        Ok(())
    }

    /// Reload a module with a new version.
    ///
    /// Unloads the current version and loads the new one from the given path.
    ///
    /// # Errors
    ///
    /// Returns an error if unload or load fails.
    pub fn reload(&mut self, name: &str, path: &Path, world: &mut StormWorld) -> Result<()> {
        info!("Reloading module '{}' from {:?}", name, path);

        // Unload old version
        self.unload(name)?;

        // Load new version
        self.load(path)?;

        // Initialize new version
        self.initialize(name, world)?;

        info!("Module '{}' reloaded successfully", name);
        Ok(())
    }

    /// Get information about a loaded module.
    #[must_use]
    pub fn get(&self, name: &str) -> Option<LoadedModule> {
        self.modules.get(name).map(|instance| LoadedModule {
            name: instance.module.descriptor().name.to_string(),
            version: instance.module.descriptor().version.to_string(),
            path: instance.path.clone(),
            active: instance.initialized,
        })
    }

    /// Get all loaded modules.
    #[must_use]
    pub fn list(&self) -> Vec<LoadedModule> {
        self.load_order
            .iter()
            .filter_map(|name| self.get(name))
            .collect()
    }

    /// Check if a module is loaded.
    #[must_use]
    pub fn is_loaded(&self, name: &str) -> bool {
        self.modules.contains_key(name)
    }

    /// Check if a module is initialized.
    #[must_use]
    pub fn is_initialized(&self, name: &str) -> bool {
        self.modules
            .get(name)
            .map(|m| m.initialized)
            .unwrap_or(false)
    }

    /// Get the number of loaded modules.
    #[must_use]
    pub fn count(&self) -> usize {
        self.modules.len()
    }

    /// Unload all modules in reverse load order.
    pub fn unload_all(&mut self) {
        let names: Vec<_> = self.load_order.iter().rev().cloned().collect();
        for name in names {
            if let Err(e) = self.unload(&name) {
                error!("Failed to unload module '{}': {}", name, e);
            }
        }
    }
}

impl Default for ModuleLoader {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for ModuleLoader {
    fn drop(&mut self) {
        self.unload_all();
    }
}

/// Thread-safe wrapper for shared module loader access.
pub type SharedModuleLoader = Arc<RwLock<ModuleLoader>>;

/// Create a new shared module loader.
#[must_use]
pub fn shared_loader() -> SharedModuleLoader {
    Arc::new(RwLock::new(ModuleLoader::new()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loader_new() {
        let loader = ModuleLoader::new();
        assert_eq!(loader.count(), 0);
        assert!(loader.list().is_empty());
    }

    #[test]
    fn loader_not_found() {
        let loader = ModuleLoader::new();
        assert!(loader.get("nonexistent").is_none());
        assert!(!loader.is_loaded("nonexistent"));
        assert!(!loader.is_initialized("nonexistent"));
    }

    #[test]
    fn loader_load_invalid_path() {
        let mut loader = ModuleLoader::new();
        let result = loader.load(Path::new("/nonexistent/path/to/module.so"));
        assert!(result.is_err());
    }

    #[test]
    fn loader_unload_not_found() {
        let mut loader = ModuleLoader::new();
        let result = loader.unload("nonexistent");
        assert!(matches!(
            result,
            Err(StormError::Module(ModuleError::NotFound(_)))
        ));
    }

    #[test]
    fn shared_loader_works() {
        let loader = shared_loader();

        {
            let l = loader.read();
            assert_eq!(l.count(), 0);
        }
    }
}
