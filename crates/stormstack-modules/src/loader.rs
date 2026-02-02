//! Dynamic module loader.

use std::path::Path;
use stormstack_core::Result;

/// Module loader for dynamic libraries.
pub struct ModuleLoader {
    // TODO: Track loaded libraries
}

impl ModuleLoader {
    /// Create a new module loader.
    #[must_use]
    pub fn new() -> Self {
        Self {}
    }

    /// Load a module from a dynamic library.
    ///
    /// # Safety
    ///
    /// This loads and executes native code. Only use with trusted modules.
    ///
    /// # Errors
    ///
    /// Returns an error if the library cannot be loaded.
    pub fn load(&mut self, _path: &Path) -> Result<()> {
        // TODO: Implement with libloading
        Ok(())
    }

    /// Reload a module.
    ///
    /// # Errors
    ///
    /// Returns an error if reload fails.
    pub fn reload(&mut self, _name: &str, _path: &Path) -> Result<()> {
        // TODO: Implement
        Ok(())
    }

    /// Unload a module.
    ///
    /// # Errors
    ///
    /// Returns an error if unload fails.
    pub fn unload(&mut self, _name: &str) -> Result<()> {
        // TODO: Implement
        Ok(())
    }
}

impl Default for ModuleLoader {
    fn default() -> Self {
        Self::new()
    }
}
