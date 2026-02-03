//! Module descriptor for static registration.
//!
//! Provides metadata about native modules including name, version,
//! and dependencies for use with the inventory crate.

use serde::{Deserialize, Serialize};

/// Current ABI version for module compatibility checking.
///
/// This version must match between the host and loaded modules.
/// Increment this when making breaking changes to the module ABI.
pub const MODULE_ABI_VERSION: u32 = 1;

/// Dependency specification for a module.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ModuleDependency {
    /// Name of the required module.
    pub name: &'static str,
    /// Minimum required version (semver format).
    pub min_version: Option<&'static str>,
    /// Maximum allowed version (exclusive, semver format).
    pub max_version: Option<&'static str>,
}

impl ModuleDependency {
    /// Create a dependency on any version of a module.
    #[must_use]
    pub const fn any(name: &'static str) -> Self {
        Self {
            name,
            min_version: None,
            max_version: None,
        }
    }

    /// Create a dependency with a minimum version requirement.
    #[must_use]
    pub const fn min(name: &'static str, version: &'static str) -> Self {
        Self {
            name,
            min_version: Some(version),
            max_version: None,
        }
    }

    /// Create a dependency with a version range.
    #[must_use]
    pub const fn range(name: &'static str, min: &'static str, max: &'static str) -> Self {
        Self {
            name,
            min_version: Some(min),
            max_version: Some(max),
        }
    }

    /// Check if a given version satisfies this dependency.
    ///
    /// Uses simple string comparison for now. In production,
    /// would use proper semver parsing and comparison.
    #[must_use]
    pub fn satisfies(&self, version: &str) -> bool {
        if let Some(min) = self.min_version
            && version < min {
                return false;
            }
        if let Some(max) = self.max_version
            && version >= max {
                return false;
            }
        true
    }
}

/// Module descriptor for inventory-based discovery.
///
/// This struct contains all metadata about a module that can be
/// statically registered using the `inventory` crate.
///
/// # Example
///
/// ```ignore
/// use stormstack_modules::ModuleDescriptor;
///
/// inventory::submit! {
///     ModuleDescriptor::new("my-module", "1.0.0", "My game module")
/// }
/// ```
pub struct ModuleDescriptor {
    /// Unique module name.
    pub name: &'static str,
    /// Module version in semver format.
    pub version: &'static str,
    /// Human-readable module description.
    pub description: &'static str,
    /// ABI version this module was compiled against.
    pub abi_version: u32,
    /// Module dependencies.
    pub dependencies: &'static [ModuleDependency],
}

impl ModuleDescriptor {
    /// Create a new module descriptor without dependencies.
    #[must_use]
    pub const fn new(name: &'static str, version: &'static str, description: &'static str) -> Self {
        Self {
            name,
            version,
            description,
            abi_version: MODULE_ABI_VERSION,
            dependencies: &[],
        }
    }

    /// Create a new module descriptor with dependencies.
    #[must_use]
    pub const fn with_dependencies(
        name: &'static str,
        version: &'static str,
        description: &'static str,
        dependencies: &'static [ModuleDependency],
    ) -> Self {
        Self {
            name,
            version,
            description,
            abi_version: MODULE_ABI_VERSION,
            dependencies,
        }
    }

    /// Check if this descriptor's ABI version is compatible.
    #[must_use]
    pub fn is_abi_compatible(&self) -> bool {
        self.abi_version == MODULE_ABI_VERSION
    }
}

impl std::fmt::Debug for ModuleDescriptor {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ModuleDescriptor")
            .field("name", &self.name)
            .field("version", &self.version)
            .field("description", &self.description)
            .field("abi_version", &self.abi_version)
            .field("dependencies", &self.dependencies.len())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn descriptor_new() {
        let desc = ModuleDescriptor::new("test", "1.0.0", "Test module");
        assert_eq!(desc.name, "test");
        assert_eq!(desc.version, "1.0.0");
        assert_eq!(desc.description, "Test module");
        assert_eq!(desc.abi_version, MODULE_ABI_VERSION);
        assert!(desc.dependencies.is_empty());
    }

    #[test]
    fn descriptor_with_dependencies() {
        static DEPS: &[ModuleDependency] = &[ModuleDependency::any("core")];
        let desc = ModuleDescriptor::with_dependencies("ext", "2.0.0", "Extension", DEPS);
        assert_eq!(desc.dependencies.len(), 1);
        assert_eq!(desc.dependencies[0].name, "core");
    }

    #[test]
    fn dependency_any() {
        let dep = ModuleDependency::any("foo");
        assert!(dep.satisfies("0.0.1"));
        assert!(dep.satisfies("999.999.999"));
    }

    #[test]
    fn dependency_min() {
        let dep = ModuleDependency::min("foo", "1.0.0");
        assert!(!dep.satisfies("0.9.0"));
        assert!(dep.satisfies("1.0.0"));
        assert!(dep.satisfies("2.0.0"));
    }

    #[test]
    fn dependency_range() {
        let dep = ModuleDependency::range("foo", "1.0.0", "2.0.0");
        assert!(!dep.satisfies("0.9.0"));
        assert!(dep.satisfies("1.0.0"));
        assert!(dep.satisfies("1.5.0"));
        assert!(!dep.satisfies("2.0.0"));
        assert!(!dep.satisfies("3.0.0"));
    }

    #[test]
    fn abi_compatibility() {
        let desc = ModuleDescriptor::new("test", "1.0.0", "Test");
        assert!(desc.is_abi_compatible());
    }
}
