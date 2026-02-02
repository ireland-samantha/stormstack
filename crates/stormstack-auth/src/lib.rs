//! # StormStack Auth
//!
//! Authentication and authorization for StormStack.
//!
//! ## Features
//!
//! - JWT token generation and validation (HS256)
//! - Password hashing with Argon2id (OWASP-recommended parameters)
//! - Role-based access control (RBAC)
//!
//! ## Security
//!
//! This crate handles sensitive credentials. It:
//! - Uses constant-time comparisons for secrets
//! - Never logs passwords or tokens
//! - Uses strong algorithms (Argon2id, HS256)
//! - Follows OWASP password storage recommendations
//!
//! ## Example
//!
//! ```no_run
//! use stormstack_auth::{JwtService, PasswordService, Claims, RbacService, Permission};
//! use stormstack_core::{UserId, TenantId};
//!
//! // Password hashing
//! let pwd_service = PasswordService::new();
//! let hash = pwd_service.hash_password("SecurePassword123").unwrap();
//! assert!(pwd_service.verify_password("SecurePassword123", &hash).unwrap());
//!
//! // JWT tokens
//! let jwt_service = JwtService::new(b"your-secret-key-at-least-32-bytes!");
//! let claims = Claims::new(UserId::new(), TenantId::new(), vec!["user".to_string()]);
//! let token = jwt_service.generate_token(&claims).unwrap();
//! let validated = jwt_service.validate_token(&token).unwrap();
//!
//! // RBAC
//! let rbac = RbacService::new();
//! assert!(rbac.has_permission(&validated, Permission::MatchJoin));
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod claims;
pub mod jwt;
pub mod password;
pub mod rbac;

pub use claims::Claims;
pub use jwt::{JwtConfig, JwtService};
pub use password::{PasswordConfig, PasswordService};
pub use rbac::{Permission, RbacService};
