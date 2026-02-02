//! Module registry for querying and managing loaded modules.
//!
//! Provides a central registry for tracking loaded modules,
//! querying their status, and resolving dependencies.

use std::collections::{HashMap, HashSet};

use stormstack_core::{ModuleError, Result, StormError};
use tracing::debug;

use crate::descriptor::ModuleDependency;

/// Information about a registered module.
#[derive(Debug, Clone)]
pub struct RegistryEntry {
    /// Module name.
    pub name: String,
    /// Module version.
    pub version: String,
    /// Module description.
    pub description: String,
    /// Dependencies.
    pub dependencies: Vec<ModuleDependency>,
    /// Whether the module is currently loaded.
    pub loaded: bool,
    /// Whether the module is initialized.
    pub initialized: bool,
}

/// Module registry for tracking and querying modules.
///
/// The registry maintains information about available and loaded modules,
/// supports dependency resolution, and provides query capabilities.
#[derive(Debug, Default)]
pub struct ModuleRegistry {
    /// Registered modules by name.
    entries: HashMap<String, RegistryEntry>,
}

impl ModuleRegistry {
    /// Create a new empty registry.
    #[must_use]
    pub fn new() -> Self {
        Self {
            entries: HashMap::new(),
        }
    }

    /// Register a module in the registry.
    ///
    /// This adds a module entry without loading it.
    pub fn register(
        &mut self,
        name: &str,
        version: &str,
        description: &str,
        dependencies: &[ModuleDependency],
    ) {
        debug!("Registering module '{}' v{}", name, version);
        self.entries.insert(
            name.to_string(),
            RegistryEntry {
                name: name.to_string(),
                version: version.to_string(),
                description: description.to_string(),
                dependencies: dependencies.to_vec(),
                loaded: false,
                initialized: false,
            },
        );
    }

    /// Mark a module as loaded.
    pub fn mark_loaded(&mut self, name: &str) {
        if let Some(entry) = self.entries.get_mut(name) {
            entry.loaded = true;
            debug!("Module '{}' marked as loaded", name);
        }
    }

    /// Mark a module as initialized.
    pub fn mark_initialized(&mut self, name: &str) {
        if let Some(entry) = self.entries.get_mut(name) {
            entry.initialized = true;
            debug!("Module '{}' marked as initialized", name);
        }
    }

    /// Mark a module as unloaded.
    pub fn mark_unloaded(&mut self, name: &str) {
        if let Some(entry) = self.entries.get_mut(name) {
            entry.loaded = false;
            entry.initialized = false;
            debug!("Module '{}' marked as unloaded", name);
        }
    }

    /// Get a module entry.
    #[must_use]
    pub fn get(&self, name: &str) -> Option<&RegistryEntry> {
        self.entries.get(name)
    }

    /// Check if a module is registered.
    #[must_use]
    pub fn is_registered(&self, name: &str) -> bool {
        self.entries.contains_key(name)
    }

    /// Check if a module is loaded.
    #[must_use]
    pub fn is_loaded(&self, name: &str) -> bool {
        self.entries.get(name).map(|e| e.loaded).unwrap_or(false)
    }

    /// Check if a module is initialized.
    #[must_use]
    pub fn is_initialized(&self, name: &str) -> bool {
        self.entries.get(name).map(|e| e.initialized).unwrap_or(false)
    }

    /// Get all registered modules.
    #[must_use]
    pub fn list(&self) -> Vec<&RegistryEntry> {
        self.entries.values().collect()
    }

    /// Get all loaded modules.
    #[must_use]
    pub fn loaded(&self) -> Vec<&RegistryEntry> {
        self.entries.values().filter(|e| e.loaded).collect()
    }

    /// Get all initialized modules.
    #[must_use]
    pub fn initialized(&self) -> Vec<&RegistryEntry> {
        self.entries.values().filter(|e| e.initialized).collect()
    }

    /// Get the count of registered modules.
    #[must_use]
    pub fn count(&self) -> usize {
        self.entries.len()
    }

    /// Remove a module from the registry.
    pub fn unregister(&mut self, name: &str) -> Option<RegistryEntry> {
        self.entries.remove(name)
    }

    /// Get modules that depend on the given module.
    #[must_use]
    pub fn dependents(&self, name: &str) -> Vec<&RegistryEntry> {
        self.entries
            .values()
            .filter(|entry| entry.dependencies.iter().any(|d| d.name == name))
            .collect()
    }

    /// Get the dependencies of a module.
    #[must_use]
    pub fn dependencies(&self, name: &str) -> Option<Vec<&RegistryEntry>> {
        let entry = self.entries.get(name)?;
        Some(
            entry
                .dependencies
                .iter()
                .filter_map(|d| self.entries.get(d.name))
                .collect(),
        )
    }

    /// Resolve the load order for a set of modules.
    ///
    /// Returns modules in topological order (dependencies first).
    ///
    /// # Errors
    ///
    /// Returns an error if there are circular dependencies.
    pub fn resolve_load_order(&self, names: &[&str]) -> Result<Vec<String>> {
        let mut result = Vec::new();
        let mut visited = HashSet::new();
        let mut in_stack = HashSet::new();

        for name in names {
            self.visit_for_order(*name, &mut result, &mut visited, &mut in_stack)?;
        }

        Ok(result)
    }

    fn visit_for_order(
        &self,
        name: &str,
        result: &mut Vec<String>,
        visited: &mut HashSet<String>,
        in_stack: &mut HashSet<String>,
    ) -> Result<()> {
        if visited.contains(name) {
            return Ok(());
        }

        if in_stack.contains(name) {
            return Err(StormError::Module(ModuleError::CircularDependency(
                name.to_string(),
            )));
        }

        in_stack.insert(name.to_string());

        if let Some(entry) = self.entries.get(name) {
            for dep in &entry.dependencies {
                self.visit_for_order(dep.name, result, visited, in_stack)?;
            }
        }

        in_stack.remove(name);
        visited.insert(name.to_string());
        result.push(name.to_string());

        Ok(())
    }

    /// Check if all dependencies of a module are satisfied.
    ///
    /// # Errors
    ///
    /// Returns an error describing which dependencies are missing.
    pub fn check_dependencies(&self, name: &str) -> Result<()> {
        let entry = self
            .entries
            .get(name)
            .ok_or_else(|| StormError::Module(ModuleError::NotFound(name.to_string())))?;

        for dep in &entry.dependencies {
            let dep_entry = self.entries.get(dep.name).ok_or_else(|| {
                StormError::Module(ModuleError::DependencyNotSatisfied {
                    module: name.to_string(),
                    dependency: dep.name.to_string(),
                })
            })?;

            if !dep.satisfies(&dep_entry.version) {
                return Err(StormError::Module(ModuleError::VersionConflict {
                    name: dep.name.to_string(),
                    required: format!("{:?}", dep),
                    found: dep_entry.version.clone(),
                }));
            }
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registry_new() {
        let registry = ModuleRegistry::new();
        assert_eq!(registry.count(), 0);
        assert!(registry.list().is_empty());
    }

    #[test]
    fn registry_register() {
        let mut registry = ModuleRegistry::new();
        registry.register("test", "1.0.0", "Test module", &[]);

        assert!(registry.is_registered("test"));
        assert!(!registry.is_loaded("test"));
        assert_eq!(registry.count(), 1);
    }

    #[test]
    fn registry_mark_loaded() {
        let mut registry = ModuleRegistry::new();
        registry.register("test", "1.0.0", "Test", &[]);

        assert!(!registry.is_loaded("test"));
        registry.mark_loaded("test");
        assert!(registry.is_loaded("test"));
    }

    #[test]
    fn registry_mark_initialized() {
        let mut registry = ModuleRegistry::new();
        registry.register("test", "1.0.0", "Test", &[]);

        assert!(!registry.is_initialized("test"));
        registry.mark_initialized("test");
        assert!(registry.is_initialized("test"));
    }

    #[test]
    fn registry_dependents() {
        let mut registry = ModuleRegistry::new();
        registry.register("core", "1.0.0", "Core", &[]);
        registry.register(
            "ext",
            "1.0.0",
            "Extension",
            &[ModuleDependency::any("core")],
        );

        let dependents = registry.dependents("core");
        assert_eq!(dependents.len(), 1);
        assert_eq!(dependents[0].name, "ext");
    }

    #[test]
    fn registry_dependencies() {
        let mut registry = ModuleRegistry::new();
        registry.register("core", "1.0.0", "Core", &[]);
        registry.register(
            "ext",
            "1.0.0",
            "Extension",
            &[ModuleDependency::any("core")],
        );

        let deps = registry.dependencies("ext").unwrap();
        assert_eq!(deps.len(), 1);
        assert_eq!(deps[0].name, "core");
    }

    #[test]
    fn registry_resolve_load_order() {
        let mut registry = ModuleRegistry::new();
        registry.register("core", "1.0.0", "Core", &[]);
        registry.register(
            "mid",
            "1.0.0",
            "Middle",
            &[ModuleDependency::any("core")],
        );
        registry.register(
            "top",
            "1.0.0",
            "Top",
            &[ModuleDependency::any("mid")],
        );

        let order = registry.resolve_load_order(&["top"]).unwrap();
        assert_eq!(order, vec!["core", "mid", "top"]);
    }

    #[test]
    fn registry_circular_dependency() {
        let mut registry = ModuleRegistry::new();
        registry.register("a", "1.0.0", "A", &[ModuleDependency::any("b")]);
        registry.register("b", "1.0.0", "B", &[ModuleDependency::any("a")]);

        let result = registry.resolve_load_order(&["a"]);
        assert!(matches!(
            result,
            Err(StormError::Module(ModuleError::CircularDependency(_)))
        ));
    }

    #[test]
    fn registry_check_dependencies_ok() {
        let mut registry = ModuleRegistry::new();
        registry.register("core", "1.0.0", "Core", &[]);
        registry.register(
            "ext",
            "1.0.0",
            "Extension",
            &[ModuleDependency::any("core")],
        );

        assert!(registry.check_dependencies("ext").is_ok());
    }

    #[test]
    fn registry_check_dependencies_missing() {
        let mut registry = ModuleRegistry::new();
        registry.register(
            "ext",
            "1.0.0",
            "Extension",
            &[ModuleDependency::any("core")],
        );

        let result = registry.check_dependencies("ext");
        assert!(matches!(
            result,
            Err(StormError::Module(ModuleError::DependencyNotSatisfied { .. }))
        ));
    }

    #[test]
    fn registry_check_dependencies_version_mismatch() {
        let mut registry = ModuleRegistry::new();
        registry.register("core", "0.5.0", "Core", &[]);
        registry.register(
            "ext",
            "1.0.0",
            "Extension",
            &[ModuleDependency::min("core", "1.0.0")],
        );

        let result = registry.check_dependencies("ext");
        assert!(matches!(
            result,
            Err(StormError::Module(ModuleError::VersionConflict { .. }))
        ));
    }

    #[test]
    fn registry_unregister() {
        let mut registry = ModuleRegistry::new();
        registry.register("test", "1.0.0", "Test", &[]);

        assert!(registry.is_registered("test"));
        let removed = registry.unregister("test");
        assert!(removed.is_some());
        assert!(!registry.is_registered("test"));
    }
}
