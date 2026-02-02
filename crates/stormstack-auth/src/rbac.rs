//! Role-based access control.

use crate::claims::Claims;
use std::collections::{HashMap, HashSet};

/// Permission for role-based access control.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Permission {
    /// Create containers.
    ContainerCreate,
    /// Read container information.
    ContainerRead,
    /// Delete containers.
    ContainerDelete,
    /// Create matches.
    MatchCreate,
    /// Read match information.
    MatchRead,
    /// Join matches.
    MatchJoin,
    /// Delete matches.
    MatchDelete,
    /// Upload WASM modules.
    ModuleUpload,
    /// Install modules in containers.
    ModuleInstall,
    /// Administrative access.
    AdminAccess,
    /// Manage tenant settings.
    TenantManage,
}

/// Role-based access control service.
pub struct RbacService {
    role_permissions: HashMap<String, HashSet<Permission>>,
}

impl RbacService {
    /// Create a new RBAC service with default role definitions.
    #[must_use]
    pub fn new() -> Self {
        let mut role_permissions = HashMap::new();

        // Admin role - all permissions
        role_permissions.insert(
            "admin".to_string(),
            [
                Permission::ContainerCreate,
                Permission::ContainerRead,
                Permission::ContainerDelete,
                Permission::MatchCreate,
                Permission::MatchRead,
                Permission::MatchJoin,
                Permission::MatchDelete,
                Permission::ModuleUpload,
                Permission::ModuleInstall,
                Permission::AdminAccess,
                Permission::TenantManage,
            ]
            .into_iter()
            .collect(),
        );

        // Moderator role
        role_permissions.insert(
            "moderator".to_string(),
            [
                Permission::ContainerRead,
                Permission::MatchCreate,
                Permission::MatchRead,
                Permission::MatchDelete,
            ]
            .into_iter()
            .collect(),
        );

        // User role
        role_permissions.insert(
            "user".to_string(),
            [
                Permission::ContainerRead,
                Permission::MatchRead,
                Permission::MatchJoin,
            ]
            .into_iter()
            .collect(),
        );

        // Developer role
        role_permissions.insert(
            "developer".to_string(),
            [
                Permission::ContainerCreate,
                Permission::ContainerRead,
                Permission::MatchCreate,
                Permission::MatchRead,
                Permission::MatchJoin,
                Permission::ModuleUpload,
                Permission::ModuleInstall,
            ]
            .into_iter()
            .collect(),
        );

        Self { role_permissions }
    }

    /// Check if claims have a specific permission.
    #[must_use]
    pub fn has_permission(&self, claims: &Claims, permission: Permission) -> bool {
        claims.roles.iter().any(|role| {
            self.role_permissions
                .get(role)
                .is_some_and(|perms| perms.contains(&permission))
        })
    }

    /// Get all permissions for a role.
    #[must_use]
    pub fn role_permissions(&self, role: &str) -> Vec<Permission> {
        self.role_permissions
            .get(role)
            .map(|perms| perms.iter().copied().collect())
            .unwrap_or_default()
    }

    /// Add a custom role with specific permissions.
    pub fn add_role(&mut self, role: String, permissions: HashSet<Permission>) {
        self.role_permissions.insert(role, permissions);
    }
}

impl Default for RbacService {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_core::{TenantId, UserId};

    #[test]
    fn admin_has_all_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(UserId::new(), TenantId::new(), vec!["admin".to_string()]);

        assert!(rbac.has_permission(&claims, Permission::ContainerCreate));
        assert!(rbac.has_permission(&claims, Permission::AdminAccess));
        assert!(rbac.has_permission(&claims, Permission::TenantManage));
    }

    #[test]
    fn user_has_limited_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(UserId::new(), TenantId::new(), vec!["user".to_string()]);

        assert!(rbac.has_permission(&claims, Permission::MatchJoin));
        assert!(!rbac.has_permission(&claims, Permission::ContainerCreate));
        assert!(!rbac.has_permission(&claims, Permission::AdminAccess));
    }

    #[test]
    fn multiple_roles_combine_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["user".to_string(), "developer".to_string()],
        );

        // From user role
        assert!(rbac.has_permission(&claims, Permission::MatchJoin));
        // From developer role
        assert!(rbac.has_permission(&claims, Permission::ModuleUpload));
    }

    #[test]
    fn unknown_role_has_no_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["unknown_role".to_string()],
        );

        assert!(!rbac.has_permission(&claims, Permission::MatchJoin));
        assert!(!rbac.has_permission(&claims, Permission::ContainerRead));
    }

    #[test]
    fn role_permissions_list() {
        let rbac = RbacService::new();
        let perms = rbac.role_permissions("user");

        assert!(perms.contains(&Permission::MatchJoin));
        assert!(perms.contains(&Permission::ContainerRead));
        assert!(!perms.contains(&Permission::AdminAccess));
    }
}
