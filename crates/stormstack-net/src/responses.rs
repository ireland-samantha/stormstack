//! Standardized API responses.
//!
//! Provides consistent response formats for success and error cases.

use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};

/// Standard API response wrapper.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    /// Whether the request succeeded.
    pub success: bool,
    /// Response data (present on success).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    /// Error information (present on failure).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<ApiError>,
}

impl<T: Serialize> ApiResponse<T> {
    /// Create a success response with data.
    pub fn ok(data: T) -> Self {
        Self {
            success: true,
            data: Some(data),
            error: None,
        }
    }

    /// Create an error response.
    pub fn err(error: ApiError) -> Self {
        Self {
            success: false,
            data: None,
            error: Some(error),
        }
    }
}

impl<T: Serialize> IntoResponse for ApiResponse<T> {
    fn into_response(self) -> Response {
        let status = if self.success {
            StatusCode::OK
        } else {
            self.error
                .as_ref()
                .map(|e| e.status_code())
                .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR)
        };
        (status, Json(self)).into_response()
    }
}

/// API error information.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiError {
    /// Error code (e.g., "NOT_FOUND", "VALIDATION_ERROR").
    pub code: String,
    /// Human-readable error message.
    pub message: String,
    /// Optional field-level errors.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<Vec<FieldError>>,
}

impl ApiError {
    /// Create a new error.
    pub fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            details: None,
        }
    }

    /// Add field-level error details.
    pub fn with_details(mut self, details: Vec<FieldError>) -> Self {
        self.details = Some(details);
        self
    }

    /// Not found error.
    pub fn not_found(resource: &str) -> Self {
        Self::new("NOT_FOUND", format!("{} not found", resource))
    }

    /// Validation error.
    pub fn validation(message: impl Into<String>) -> Self {
        Self::new("VALIDATION_ERROR", message)
    }

    /// Unauthorized error.
    pub fn unauthorized() -> Self {
        Self::new("UNAUTHORIZED", "Authentication required")
    }

    /// Forbidden error.
    pub fn forbidden() -> Self {
        Self::new("FORBIDDEN", "Access denied")
    }

    /// Internal server error.
    pub fn internal(message: impl Into<String>) -> Self {
        Self::new("INTERNAL_ERROR", message)
    }

    /// Conflict error.
    pub fn conflict(message: impl Into<String>) -> Self {
        Self::new("CONFLICT", message)
    }

    /// Get the HTTP status code for this error.
    pub fn status_code(&self) -> StatusCode {
        match self.code.as_str() {
            "NOT_FOUND" => StatusCode::NOT_FOUND,
            "VALIDATION_ERROR" => StatusCode::BAD_REQUEST,
            "UNAUTHORIZED" => StatusCode::UNAUTHORIZED,
            "FORBIDDEN" => StatusCode::FORBIDDEN,
            "CONFLICT" => StatusCode::CONFLICT,
            _ => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        let response: ApiResponse<()> = ApiResponse::err(self);
        (status, Json(response)).into_response()
    }
}

/// Field-level error detail.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FieldError {
    /// Field name.
    pub field: String,
    /// Error message.
    pub message: String,
}

impl FieldError {
    /// Create a new field error.
    pub fn new(field: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            field: field.into(),
            message: message.into(),
        }
    }
}

/// Paginated response wrapper.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginatedResponse<T> {
    /// Page items.
    pub items: Vec<T>,
    /// Current page (1-indexed).
    pub page: u32,
    /// Items per page.
    pub per_page: u32,
    /// Total item count.
    pub total: u64,
    /// Total page count.
    pub total_pages: u32,
}

impl<T> PaginatedResponse<T> {
    /// Create a new paginated response.
    pub fn new(items: Vec<T>, page: u32, per_page: u32, total: u64) -> Self {
        let total_pages = ((total as f64) / (per_page as f64)).ceil() as u32;
        Self {
            items,
            page,
            per_page,
            total,
            total_pages,
        }
    }

    /// Check if there's a next page.
    pub fn has_next(&self) -> bool {
        self.page < self.total_pages
    }

    /// Check if there's a previous page.
    pub fn has_prev(&self) -> bool {
        self.page > 1
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn api_response_success() {
        let response: ApiResponse<String> = ApiResponse::ok("data".to_string());
        assert!(response.success);
        assert!(response.data.is_some());
        assert!(response.error.is_none());
    }

    #[test]
    fn api_response_error() {
        let response: ApiResponse<String> = ApiResponse::err(ApiError::not_found("User"));
        assert!(!response.success);
        assert!(response.data.is_none());
        assert!(response.error.is_some());
    }

    #[test]
    fn api_error_status_codes() {
        assert_eq!(ApiError::not_found("x").status_code(), StatusCode::NOT_FOUND);
        assert_eq!(ApiError::validation("x").status_code(), StatusCode::BAD_REQUEST);
        assert_eq!(ApiError::unauthorized().status_code(), StatusCode::UNAUTHORIZED);
        assert_eq!(ApiError::forbidden().status_code(), StatusCode::FORBIDDEN);
        assert_eq!(ApiError::internal("x").status_code(), StatusCode::INTERNAL_SERVER_ERROR);
    }

    #[test]
    fn field_error() {
        let err = FieldError::new("email", "invalid format");
        assert_eq!(err.field, "email");
        assert_eq!(err.message, "invalid format");
    }

    #[test]
    fn paginated_response() {
        let items = vec!["a", "b", "c"];
        let response = PaginatedResponse::new(items, 1, 10, 25);

        assert_eq!(response.page, 1);
        assert_eq!(response.per_page, 10);
        assert_eq!(response.total, 25);
        assert_eq!(response.total_pages, 3);
        assert!(response.has_next());
        assert!(!response.has_prev());
    }

    #[test]
    fn paginated_response_last_page() {
        let items: Vec<&str> = vec![];
        let response = PaginatedResponse::new(items, 3, 10, 25);

        assert!(!response.has_next());
        assert!(response.has_prev());
    }
}
