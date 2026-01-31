// Package api provides HTTP clients for Lightning services.
package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// IAMClient is an HTTP client for the Lightning Auth IAM APIs.
type IAMClient struct {
	baseURL    string
	authToken  string
	httpClient *http.Client
}

// NewIAMClient creates a new IAM API client.
func NewIAMClient(baseURL, authToken string) *IAMClient {
	return &IAMClient{
		baseURL:   baseURL,
		authToken: authToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// User represents a user in the IAM system.
type User struct {
	ID        string   `json:"id"`
	Username  string   `json:"username"`
	RoleIDs   []string `json:"roleIds"`
	Scopes    []string `json:"scopes"`
	Enabled   bool     `json:"enabled"`
	CreatedAt string   `json:"createdAt"`
}

// CreateUserRequest is the request body for creating a user.
type CreateUserRequest struct {
	Username string   `json:"username"`
	Password string   `json:"password"`
	RoleIDs  []string `json:"roleIds,omitempty"`
	Scopes   []string `json:"scopes,omitempty"`
}

// UpdateUserRequest is the request body for updating a user.
type UpdateUserRequest struct {
	Password *string  `json:"password,omitempty"`
	RoleIDs  []string `json:"roleIds,omitempty"`
	Scopes   []string `json:"scopes,omitempty"`
	Enabled  *bool    `json:"enabled,omitempty"`
}

// Role represents a role in the IAM system.
type Role struct {
	ID              string   `json:"id"`
	Name            string   `json:"name"`
	Description     string   `json:"description"`
	IncludedRoleIDs []string `json:"includedRoleIds"`
	Scopes          []string `json:"scopes"`
}

// CreateRoleRequest is the request body for creating a role.
type CreateRoleRequest struct {
	Name            string   `json:"name"`
	Description     string   `json:"description,omitempty"`
	IncludedRoleIDs []string `json:"includedRoleIds,omitempty"`
	Scopes          []string `json:"scopes,omitempty"`
}

// UpdateRoleRequest is the request body for updating a role.
type UpdateRoleRequest struct {
	Name            string   `json:"name,omitempty"`
	Description     string   `json:"description,omitempty"`
	IncludedRoleIDs []string `json:"includedRoleIds,omitempty"`
	Scopes          []string `json:"scopes,omitempty"`
}

// ApiToken represents an API token in the IAM system.
type ApiToken struct {
	ID        string   `json:"id"`
	Name      string   `json:"name"`
	UserID    string   `json:"userId"`
	Scopes    []string `json:"scopes"`
	CreatedAt string   `json:"createdAt"`
	ExpiresAt string   `json:"expiresAt,omitempty"`
	Revoked   bool     `json:"revoked"`
}

// CreateApiTokenRequest is the request body for creating an API token.
type CreateApiTokenRequest struct {
	Name      string   `json:"name"`
	Scopes    []string `json:"scopes,omitempty"`
	ExpiresAt string   `json:"expiresAt,omitempty"`
}

// CreateApiTokenResponse includes the plaintext token (only shown once).
type CreateApiTokenResponse struct {
	ApiToken
	PlaintextToken string `json:"plaintextToken"`
}

// ========== User Operations ==========

// ListUsers lists all users.
func (c *IAMClient) ListUsers() ([]User, error) {
	var users []User
	if err := c.get("/api/users", &users); err != nil {
		return nil, err
	}
	return users, nil
}

// GetUser gets a user by ID.
func (c *IAMClient) GetUser(id string) (*User, error) {
	var user User
	if err := c.get(fmt.Sprintf("/api/users/%s", id), &user); err != nil {
		return nil, err
	}
	return &user, nil
}

// CreateUser creates a new user.
func (c *IAMClient) CreateUser(req *CreateUserRequest) (*User, error) {
	var user User
	if err := c.post("/api/users", req, &user); err != nil {
		return nil, err
	}
	return &user, nil
}

// UpdateUser updates a user.
func (c *IAMClient) UpdateUser(id string, req *UpdateUserRequest) (*User, error) {
	var user User
	if err := c.put(fmt.Sprintf("/api/users/%s", id), req, &user); err != nil {
		return nil, err
	}
	return &user, nil
}

// DeleteUser deletes a user.
func (c *IAMClient) DeleteUser(id string) error {
	return c.delete(fmt.Sprintf("/api/users/%s", id))
}

// ========== Role Operations ==========

// ListRoles lists all roles.
func (c *IAMClient) ListRoles() ([]Role, error) {
	var roles []Role
	if err := c.get("/api/roles", &roles); err != nil {
		return nil, err
	}
	return roles, nil
}

// GetRole gets a role by ID.
func (c *IAMClient) GetRole(id string) (*Role, error) {
	var role Role
	if err := c.get(fmt.Sprintf("/api/roles/%s", id), &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// CreateRole creates a new role.
func (c *IAMClient) CreateRole(req *CreateRoleRequest) (*Role, error) {
	var role Role
	if err := c.post("/api/roles", req, &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// UpdateRole updates a role.
func (c *IAMClient) UpdateRole(id string, req *UpdateRoleRequest) (*Role, error) {
	var role Role
	if err := c.put(fmt.Sprintf("/api/roles/%s", id), req, &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// DeleteRole deletes a role.
func (c *IAMClient) DeleteRole(id string) error {
	return c.delete(fmt.Sprintf("/api/roles/%s", id))
}

// UpdateRoleScopes replaces all scopes for a role.
func (c *IAMClient) UpdateRoleScopes(id string, scopes []string) (*Role, error) {
	var role Role
	if err := c.put(fmt.Sprintf("/api/roles/%s/scopes", id), scopes, &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// AddRoleScope adds a scope to a role.
func (c *IAMClient) AddRoleScope(id, scope string) (*Role, error) {
	var role Role
	if err := c.post(fmt.Sprintf("/api/roles/%s/scopes/%s", id, scope), nil, &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// RemoveRoleScope removes a scope from a role.
func (c *IAMClient) RemoveRoleScope(id, scope string) (*Role, error) {
	var role Role
	req, err := http.NewRequest(http.MethodDelete, c.baseURL+fmt.Sprintf("/api/roles/%s/scopes/%s", id, scope), nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}
	if err := c.doRequest(req, &role); err != nil {
		return nil, err
	}
	return &role, nil
}

// GetResolvedScopes gets all resolved scopes for a role.
func (c *IAMClient) GetResolvedScopes(id string) ([]string, error) {
	var scopes []string
	if err := c.get(fmt.Sprintf("/api/roles/%s/scopes/resolved", id), &scopes); err != nil {
		return nil, err
	}
	return scopes, nil
}

// ========== API Token Operations ==========

// ListApiTokens lists all API tokens.
func (c *IAMClient) ListApiTokens() ([]ApiToken, error) {
	var tokens []ApiToken
	if err := c.get("/api/tokens", &tokens); err != nil {
		return nil, err
	}
	return tokens, nil
}

// GetApiToken gets an API token by ID.
func (c *IAMClient) GetApiToken(id string) (*ApiToken, error) {
	var token ApiToken
	if err := c.get(fmt.Sprintf("/api/tokens/%s", id), &token); err != nil {
		return nil, err
	}
	return &token, nil
}

// CreateApiToken creates a new API token.
func (c *IAMClient) CreateApiToken(req *CreateApiTokenRequest) (*CreateApiTokenResponse, error) {
	var resp CreateApiTokenResponse
	if err := c.post("/api/tokens", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// RevokeApiToken revokes an API token.
func (c *IAMClient) RevokeApiToken(id string) error {
	return c.delete(fmt.Sprintf("/api/tokens/%s", id))
}

// ========== HTTP helpers ==========

func (c *IAMClient) get(path string, result interface{}) error {
	req, err := http.NewRequest(http.MethodGet, c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return c.doRequest(req, result)
}

func (c *IAMClient) post(path string, body, result interface{}) error {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("marshaling request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(http.MethodPost, c.baseURL+path, bodyReader)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	return c.doRequest(req, result)
}

func (c *IAMClient) put(path string, body, result interface{}) error {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("marshaling request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(http.MethodPut, c.baseURL+path, bodyReader)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	return c.doRequest(req, result)
}

func (c *IAMClient) delete(path string) error {
	req, err := http.NewRequest(http.MethodDelete, c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return c.doRequest(req, nil)
}

func (c *IAMClient) doRequest(req *http.Request, result interface{}) error {
	req.Header.Set("Accept", "application/json")
	setAuthHeaders(req, c.authToken)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("making request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode >= 400 {
		var apiErr APIError
		if err := json.Unmarshal(body, &apiErr); err != nil {
			return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
		}
		return &apiErr
	}

	if result != nil && len(body) > 0 {
		if err := json.Unmarshal(body, result); err != nil {
			return fmt.Errorf("parsing response: %w", err)
		}
	}

	return nil
}
