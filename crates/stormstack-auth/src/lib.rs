//! # StormStack Auth
//!
//! Authentication and authorization for StormStack.
//!
//! ## Features
//!
//! - JWT token generation and validation
//! - Password hashing with Argon2
//! - Role-based access control (RBAC)
//!
//! ## Security
//!
//! This crate handles sensitive credentials. It:
//! - Uses constant-time comparisons for secrets
//! - Zeroizes sensitive data after use
//! - Never logs passwords or tokens
//! - Uses strong algorithms (Argon2, RS256/ES256)

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod claims;
pub mod jwt;
pub mod password;
pub mod rbac;

pub use claims::Claims;
pub use jwt::JwtService;
pub use password::PasswordService;
pub use rbac::{Permission, RbacService};

// TODO: Implement authentication services
// SECURITY CRITICAL - Requires Security Reviewer approval
