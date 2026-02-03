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

    // =========================================================================
    // Additional RBAC tests - Alex
    // =========================================================================

    #[test]
    fn add_role_creates_custom_role() {
        let mut rbac = RbacService::new();

        // Add a custom "viewer" role with limited permissions
        let viewer_perms: HashSet<Permission> =
            [Permission::ContainerRead, Permission::MatchRead]
                .into_iter()
                .collect();

        rbac.add_role("viewer".to_string(), viewer_perms);

        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["viewer".to_string()],
        );

        // Should have the permissions we added
        assert!(rbac.has_permission(&claims, Permission::ContainerRead));
        assert!(rbac.has_permission(&claims, Permission::MatchRead));
        // Should NOT have other permissions
        assert!(!rbac.has_permission(&claims, Permission::MatchJoin));
        assert!(!rbac.has_permission(&claims, Permission::ContainerCreate));
    }

    #[test]
    fn add_role_overwrites_existing() {
        let mut rbac = RbacService::new();

        // User role should initially have MatchJoin
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["user".to_string()],
        );
        assert!(rbac.has_permission(&claims, Permission::MatchJoin));

        // Overwrite "user" role with different permissions
        let new_user_perms: HashSet<Permission> = [Permission::ContainerRead].into_iter().collect();
        rbac.add_role("user".to_string(), new_user_perms);

        // Now user should NOT have MatchJoin anymore
        assert!(!rbac.has_permission(&claims, Permission::MatchJoin));
        assert!(rbac.has_permission(&claims, Permission::ContainerRead));
    }

    #[test]
    fn empty_roles_has_no_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(UserId::new(), TenantId::new(), vec![]);

        // With no roles, should have no permissions
        assert!(!rbac.has_permission(&claims, Permission::ContainerRead));
        assert!(!rbac.has_permission(&claims, Permission::MatchJoin));
        assert!(!rbac.has_permission(&claims, Permission::AdminAccess));
    }

    #[test]
    fn role_permissions_unknown_role_returns_empty() {
        let rbac = RbacService::new();
        let perms = rbac.role_permissions("nonexistent_role");

        assert!(perms.is_empty());
    }

    #[test]
    fn role_permissions_admin_has_eleven_permissions() {
        let rbac = RbacService::new();
        let perms = rbac.role_permissions("admin");

        // Admin should have all 11 permissions
        assert_eq!(perms.len(), 11);
    }

    #[test]
    fn add_role_with_empty_permissions() {
        let mut rbac = RbacService::new();

        // Add a role with no permissions
        rbac.add_role("empty".to_string(), HashSet::new());

        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["empty".to_string()],
        );

        // Should have no permissions
        assert!(!rbac.has_permission(&claims, Permission::ContainerRead));
        assert!(!rbac.has_permission(&claims, Permission::AdminAccess));

        // But role should exist and return empty list
        let perms = rbac.role_permissions("empty");
        assert!(perms.is_empty());
    }

    #[test]
    fn moderator_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["moderator".to_string()],
        );

        // Moderator can read containers and manage matches
        assert!(rbac.has_permission(&claims, Permission::ContainerRead));
        assert!(rbac.has_permission(&claims, Permission::MatchCreate));
        assert!(rbac.has_permission(&claims, Permission::MatchRead));
        assert!(rbac.has_permission(&claims, Permission::MatchDelete));

        // Moderator cannot create containers or access admin features
        assert!(!rbac.has_permission(&claims, Permission::ContainerCreate));
        assert!(!rbac.has_permission(&claims, Permission::AdminAccess));
        assert!(!rbac.has_permission(&claims, Permission::ModuleUpload));
    }

    #[test]
    fn developer_permissions() {
        let rbac = RbacService::new();
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["developer".to_string()],
        );

        // Developer can create containers and upload modules
        assert!(rbac.has_permission(&claims, Permission::ContainerCreate));
        assert!(rbac.has_permission(&claims, Permission::ContainerRead));
        assert!(rbac.has_permission(&claims, Permission::ModuleUpload));
        assert!(rbac.has_permission(&claims, Permission::ModuleInstall));

        // Developer cannot delete containers or access admin features
        assert!(!rbac.has_permission(&claims, Permission::ContainerDelete));
        assert!(!rbac.has_permission(&claims, Permission::AdminAccess));
        assert!(!rbac.has_permission(&claims, Permission::TenantManage));
    }

    #[test]
    fn default_creates_same_as_new() {
        let rbac1 = RbacService::new();
        let rbac2 = RbacService::default();

        // Both should have the same default roles with identical permissions
        // Convert to HashSet for order-independent comparison
        let admin_perms1: HashSet<_> = rbac1.role_permissions("admin").into_iter().collect();
        let admin_perms2: HashSet<_> = rbac2.role_permissions("admin").into_iter().collect();
        assert_eq!(admin_perms1, admin_perms2, "Admin permissions should be identical");

        let user_perms1: HashSet<_> = rbac1.role_permissions("user").into_iter().collect();
        let user_perms2: HashSet<_> = rbac2.role_permissions("user").into_iter().collect();
        assert_eq!(user_perms1, user_perms2, "User permissions should be identical");

        let moderator_perms1: HashSet<_> = rbac1.role_permissions("moderator").into_iter().collect();
        let moderator_perms2: HashSet<_> = rbac2.role_permissions("moderator").into_iter().collect();
        assert_eq!(moderator_perms1, moderator_perms2, "Moderator permissions should be identical");

        let developer_perms1: HashSet<_> = rbac1.role_permissions("developer").into_iter().collect();
        let developer_perms2: HashSet<_> = rbac2.role_permissions("developer").into_iter().collect();
        assert_eq!(developer_perms1, developer_perms2, "Developer permissions should be identical");
    }

    // =========================================================================
    // Peer Review Improvements - Dana
    // =========================================================================

    #[test]
    fn developer_cannot_delete_matches() {
        let rbac = RbacService::new();
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["developer".to_string()],
        );

        // Developer should NOT have MatchDelete permission (security-relevant)
        assert!(!rbac.has_permission(&claims, Permission::MatchDelete));
        // But should have other match permissions
        assert!(rbac.has_permission(&claims, Permission::MatchCreate));
        assert!(rbac.has_permission(&claims, Permission::MatchRead));
        assert!(rbac.has_permission(&claims, Permission::MatchJoin));
    }
}
