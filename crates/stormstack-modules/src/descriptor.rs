//! Module descriptor for static registration.

/// Module descriptor for inventory-based discovery.
pub struct ModuleDescriptor {
    /// Module name.
    pub name: &'static str,
    /// Module version.
    pub version: &'static str,
    /// Module description.
    pub description: &'static str,
}

impl ModuleDescriptor {
    /// Create a new module descriptor.
    #[must_use]
    pub const fn new(name: &'static str, version: &'static str, description: &'static str) -> Self {
        Self {
            name,
            version,
            description,
        }
    }
}
