// Package api provides the HTTP client for the Lightning Control Plane API.
package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// setAuthHeaders sets the appropriate authentication headers based on token type.
// API tokens (lat_...) are sent in X-Api-Token header and take priority.
// JWTs are sent in Authorization: Bearer header.
func setAuthHeaders(req *http.Request, token string) {
	if token == "" {
		return
	}
	if strings.HasPrefix(token, "lat_") {
		// API token - send in X-Api-Token header
		req.Header.Set("X-Api-Token", token)
	} else {
		// JWT or other token - send in Authorization header
		req.Header.Set("Authorization", "Bearer "+token)
	}
}

// Client is the HTTP client for the Lightning Control Plane API.
type Client struct {
	baseURL    string
	authToken  string
	httpClient *http.Client
}

// NewClient creates a new API client.
func NewClient(baseURL, authToken string) *Client {
	return &Client{
		baseURL:   baseURL,
		authToken: authToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// DeployRequest is the request body for deploying a game.
type DeployRequest struct {
	Modules         []string `json:"modules"`
	PreferredNodeID *string  `json:"preferredNodeId,omitempty"`
	AutoStart       *bool    `json:"autoStart,omitempty"`
}

// DeployResponse is the response from a deploy operation.
type DeployResponse struct {
	MatchID     string    `json:"matchId"`
	NodeID      string    `json:"nodeId"`
	ContainerID int64     `json:"containerId"`
	Status      string    `json:"status"`
	CreatedAt   time.Time `json:"createdAt"`
	Modules     []string  `json:"modules"`
	Endpoints   Endpoints `json:"endpoints"`
}

// Endpoints contains connection information for a deployed game.
type Endpoints struct {
	HTTP      string `json:"http"`
	WebSocket string `json:"websocket"`
	Commands  string `json:"commands"`
}

// ClusterStatus represents the cluster health overview.
type ClusterStatus struct {
	TotalNodes        int     `json:"totalNodes"`
	HealthyNodes      int     `json:"healthyNodes"`
	DrainingNodes     int     `json:"drainingNodes"`
	TotalCapacity     int     `json:"totalCapacity"`
	UsedCapacity      int     `json:"usedCapacity"`
	AverageSaturation float64 `json:"averageSaturation"`
}

// Node represents a cluster node.
type Node struct {
	NodeID           string    `json:"nodeId"`
	AdvertiseAddress string    `json:"advertiseAddress"`
	Status           string    `json:"status"`
	Capacity         Capacity  `json:"capacity"`
	Metrics          *Metrics  `json:"metrics,omitempty"`
	RegisteredAt     time.Time `json:"registeredAt"`
	LastHeartbeat    time.Time `json:"lastHeartbeat"`
}

// Capacity represents node capacity.
type Capacity struct {
	MaxContainers int `json:"maxContainers"`
}

// Metrics represents node metrics.
type Metrics struct {
	ContainerCount int     `json:"containerCount"`
	MatchCount     int     `json:"matchCount"`
	CPUUsage       float64 `json:"cpuUsage"`
	MemoryUsedMB   int64   `json:"memoryUsedMb"`
	MemoryMaxMB    int64   `json:"memoryMaxMb"`
}

// Match represents a match in the cluster.
type Match struct {
	MatchID          string    `json:"matchId"`
	NodeID           string    `json:"nodeId"`
	ContainerID      int64     `json:"containerId"`
	Status           string    `json:"status"`
	CreatedAt        time.Time `json:"createdAt"`
	ModuleNames      []string  `json:"moduleNames"`
	AdvertiseAddress string    `json:"advertiseAddress"`
	WebSocketURL     string    `json:"websocketUrl"`
	PlayerCount      int       `json:"playerCount"`
	PlayerLimit      int       `json:"playerLimit"`
}

// JoinMatchRequest is the request body for joining a match.
type JoinMatchRequest struct {
	PlayerID   string `json:"playerId"`
	PlayerName string `json:"playerName"`
}

// JoinMatchResponse is the response from joining a match.
type JoinMatchResponse struct {
	MatchID            string    `json:"matchId"`
	PlayerID           string    `json:"playerId"`
	PlayerName         string    `json:"playerName"`
	MatchToken         string    `json:"matchToken"`
	CommandWebSocketURL string   `json:"commandWebSocketUrl"`
	SnapshotWebSocketURL string  `json:"snapshotWebSocketUrl"`
	TokenExpiresAt     time.Time `json:"tokenExpiresAt"`
}

// Module represents a module in the registry.
type Module struct {
	Name        string   `json:"name"`
	Version     string   `json:"version"`
	Description string   `json:"description"`
	Versions    []string `json:"versions,omitempty"`
}

// DistributeResult represents the result of a module distribution.
type DistributeResult struct {
	ModuleName    string `json:"moduleName"`
	ModuleVersion string `json:"moduleVersion"`
	NodesUpdated  int    `json:"nodesUpdated"`
}

// APIError represents an error response from the API.
type APIError struct {
	Code      string    `json:"code"`
	Message   string    `json:"message"`
	Timestamp time.Time `json:"timestamp"`
}

func (e *APIError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

// Deploy deploys a new game match to the cluster.
func (c *Client) Deploy(req *DeployRequest) (*DeployResponse, error) {
	var resp DeployResponse
	if err := c.post("/api/deploy", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// GetDeployment gets the status of a deployed match.
func (c *Client) GetDeployment(matchID string) (*DeployResponse, error) {
	var resp DeployResponse
	if err := c.get(fmt.Sprintf("/api/deploy/%s", matchID), &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// Undeploy removes a deployed match from the cluster.
func (c *Client) Undeploy(matchID string) error {
	return c.delete(fmt.Sprintf("/api/deploy/%s", matchID))
}

// GetClusterStatus gets the cluster health overview.
func (c *Client) GetClusterStatus() (*ClusterStatus, error) {
	var resp ClusterStatus
	if err := c.get("/api/cluster/status", &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// ListNodes lists all nodes in the cluster.
func (c *Client) ListNodes() ([]Node, error) {
	var resp []Node
	if err := c.get("/api/cluster/nodes", &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// GetNode gets a specific node by ID.
func (c *Client) GetNode(nodeID string) (*Node, error) {
	var resp Node
	if err := c.get(fmt.Sprintf("/api/cluster/nodes/%s", nodeID), &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// ListMatches lists all matches in the cluster.
func (c *Client) ListMatches(status string) ([]Match, error) {
	path := "/api/matches"
	if status != "" {
		path = fmt.Sprintf("%s?status=%s", path, status)
	}
	var resp []Match
	if err := c.get(path, &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// GetMatch gets a specific match by ID.
func (c *Client) GetMatch(matchID string) (*Match, error) {
	var resp Match
	if err := c.get(fmt.Sprintf("/api/matches/%s", matchID), &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// FinishMatch marks a match as finished.
func (c *Client) FinishMatch(matchID string) (*Match, error) {
	var resp Match
	if err := c.post(fmt.Sprintf("/api/matches/%s/finish", matchID), nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// DeleteMatch deletes a match from the cluster.
func (c *Client) DeleteMatch(matchID string) error {
	return c.delete(fmt.Sprintf("/api/matches/%s", matchID))
}

// JoinMatch joins a match and returns the match token and WebSocket URLs.
func (c *Client) JoinMatch(matchID, playerID, playerName string) (*JoinMatchResponse, error) {
	req := &JoinMatchRequest{
		PlayerID:   playerID,
		PlayerName: playerName,
	}
	var resp JoinMatchResponse
	if err := c.post(fmt.Sprintf("/api/matches/%s/join", matchID), req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// ListModules lists all modules in the registry.
func (c *Client) ListModules() ([]Module, error) {
	var resp []Module
	if err := c.get("/api/modules", &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// GetModuleVersions gets all versions of a module.
func (c *Client) GetModuleVersions(name string) (*Module, error) {
	var resp Module
	if err := c.get(fmt.Sprintf("/api/modules/%s", name), &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// UploadModule uploads a module JAR file to the registry.
func (c *Client) UploadModule(name, version, description, filePath string) (*Module, error) {
	// Read the JAR file
	fileData, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("reading file: %w", err)
	}

	// Create multipart form
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)

	// Add form fields
	if err := writer.WriteField("name", name); err != nil {
		return nil, fmt.Errorf("writing name field: %w", err)
	}
	if err := writer.WriteField("version", version); err != nil {
		return nil, fmt.Errorf("writing version field: %w", err)
	}
	if err := writer.WriteField("description", description); err != nil {
		return nil, fmt.Errorf("writing description field: %w", err)
	}

	// Add file
	part, err := writer.CreateFormFile("file", filepath.Base(filePath))
	if err != nil {
		return nil, fmt.Errorf("creating form file: %w", err)
	}
	if _, err := part.Write(fileData); err != nil {
		return nil, fmt.Errorf("writing file data: %w", err)
	}

	if err := writer.Close(); err != nil {
		return nil, fmt.Errorf("closing multipart writer: %w", err)
	}

	// Create request
	req, err := http.NewRequest(http.MethodPost, c.baseURL+"/api/modules", body)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	req.Header.Set("Content-Type", writer.FormDataContentType())
	setAuthHeaders(req, c.authToken)

	// Send request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("sending request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated && resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("upload failed with status %d: %s", resp.StatusCode, string(bodyBytes))
	}

	// Parse response
	var module Module
	if err := json.NewDecoder(resp.Body).Decode(&module); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}

	return &module, nil
}

// DistributeModule distributes a module to all nodes.
func (c *Client) DistributeModule(name, version string) (*DistributeResult, error) {
	var resp DistributeResult
	if err := c.post(fmt.Sprintf("/api/modules/%s/%s/distribute", name, version), nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// EngineClient is an HTTP client for direct Lightning Engine node APIs.
type EngineClient struct {
	baseURL    string
	authToken  string
	httpClient *http.Client
}

// NewEngineClient creates a new engine API client.
func NewEngineClient(baseURL, authToken string) *EngineClient {
	return &EngineClient{
		baseURL:   baseURL,
		authToken: authToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// NewProxiedEngineClient creates an engine client that routes requests through the control plane proxy.
// This is useful when the engine node is not directly reachable (e.g., Docker internal networks).
// Requests are routed to: {controlPlaneURL}/api/nodes/{nodeID}/proxy/{path}
func NewProxiedEngineClient(controlPlaneURL, nodeID, authToken string) *EngineClient {
	// Build the proxy base URL
	proxyBaseURL := fmt.Sprintf("%s/api/nodes/%s/proxy", controlPlaneURL, nodeID)
	return &EngineClient{
		baseURL:   proxyBaseURL,
		authToken: authToken,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// CommandRequest is the request body for sending a command.
type CommandRequest struct {
	CommandName string                 `json:"commandName"`
	Parameters  map[string]interface{} `json:"parameters"`
}

// CommandInfo describes an available command.
type CommandInfo struct {
	Name        string           `json:"name"`
	Description string           `json:"description"`
	Module      string           `json:"module"`
	Parameters  []ParameterInfo  `json:"parameters"`
}

// ParameterInfo describes a command parameter.
type ParameterInfo struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	Required    bool   `json:"required"`
	Description string `json:"description"`
}

// Snapshot represents the game state at a point in time.
type Snapshot struct {
	MatchID int64          `json:"matchId"`
	Tick    int64          `json:"tick"`
	Modules []ModuleData   `json:"modules"`
	Error   *string        `json:"error,omitempty"`
}

// ModuleData contains component data for a module.
type ModuleData struct {
	Name       string          `json:"name"`
	Version    string          `json:"version"`
	Components []ComponentData `json:"components"`
}

// ComponentData contains values for a component.
type ComponentData struct {
	Name   string    `json:"name"`
	Values []float64 `json:"values"`
}

// SendCommand sends a command to a container.
func (e *EngineClient) SendCommand(containerID int64, req *CommandRequest) error {
	path := fmt.Sprintf("/api/containers/%d/commands", containerID)
	return e.post(path, req, nil)
}

// ListCommands lists available commands for a container.
func (e *EngineClient) ListCommands(containerID int64) ([]CommandInfo, error) {
	var resp []CommandInfo
	path := fmt.Sprintf("/api/containers/%d/commands", containerID)
	if err := e.get(path, &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// GetSnapshot retrieves the current snapshot for a match.
func (e *EngineClient) GetSnapshot(containerID int64, matchID int64) (*Snapshot, error) {
	var resp Snapshot
	path := fmt.Sprintf("/api/containers/%d/matches/%d/snapshot", containerID, matchID)
	if err := e.get(path, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// EngineModule represents a module installed on an engine node.
type EngineModule struct {
	Name           string `json:"name"`
	FlagComponent  string `json:"flagComponent,omitempty"`
	EnabledMatches int    `json:"enabledMatches"`
}

// ListEngineModules lists all modules installed on the engine node.
func (e *EngineClient) ListEngineModules() ([]EngineModule, error) {
	var resp []EngineModule
	if err := e.get("/api/modules", &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// GetEngineModule gets a specific module from the engine node.
func (e *EngineClient) GetEngineModule(name string) (*EngineModule, error) {
	var resp EngineModule
	if err := e.get(fmt.Sprintf("/api/modules/%s", name), &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// UninstallModule removes a module from the engine node.
func (e *EngineClient) UninstallModule(name string) error {
	return e.delete(fmt.Sprintf("/api/modules/%s", name))
}

// ReloadModules reloads all modules from disk on the engine node.
func (e *EngineClient) ReloadModules() ([]EngineModule, error) {
	var resp []EngineModule
	if err := e.post("/api/modules/reload", nil, &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

func (e *EngineClient) delete(path string) error {
	req, err := http.NewRequest(http.MethodDelete, e.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return e.doRequest(req, nil)
}

// TickResponse represents the response from tick operations.
type TickResponse struct {
	Tick int64 `json:"tick"`
}

// AdvanceTick advances the container by one tick.
func (e *EngineClient) AdvanceTick(containerID int64) (*TickResponse, error) {
	var resp TickResponse
	path := fmt.Sprintf("/api/containers/%d/tick", containerID)
	if err := e.post(path, nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// GetTick gets the current tick for a container.
func (e *EngineClient) GetTick(containerID int64) (*TickResponse, error) {
	var resp TickResponse
	path := fmt.Sprintf("/api/containers/%d/tick", containerID)
	if err := e.get(path, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// PlayResponse represents the response from play/stop operations.
type PlayResponse struct {
	ID        int64  `json:"id"`
	Status    string `json:"status"`
	IsPlaying bool   `json:"isPlaying"`
	Tick      int64  `json:"tick"`
	Interval  int64  `json:"interval"`
}

// StartPlay starts auto-advancing the container.
func (e *EngineClient) StartPlay(containerID int64, intervalMs int64) (*PlayResponse, error) {
	var resp PlayResponse
	path := fmt.Sprintf("/api/containers/%d/play?intervalMs=%d", containerID, intervalMs)
	if err := e.post(path, nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// StopPlay stops auto-advancing the container.
func (e *EngineClient) StopPlay(containerID int64) (*PlayResponse, error) {
	var resp PlayResponse
	path := fmt.Sprintf("/api/containers/%d/stop-auto", containerID)
	if err := e.post(path, nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// ContainerMetrics represents container metrics.
type ContainerMetrics struct {
	ContainerID         int64                  `json:"containerId"`
	CurrentTick         int64                  `json:"currentTick"`
	LastTickMs          float64                `json:"lastTickMs"`
	AvgTickMs           float64                `json:"avgTickMs"`
	MinTickMs           float64                `json:"minTickMs"`
	MaxTickMs           float64                `json:"maxTickMs"`
	TotalTicks          int64                  `json:"totalTicks"`
	TotalEntities       int                    `json:"totalEntities"`
	TotalComponentTypes int                    `json:"totalComponentTypes"`
	CommandQueueSize    int                    `json:"commandQueueSize"`
	SnapshotMetrics     *SnapshotMetrics       `json:"snapshotMetrics,omitempty"`
	LastTickSystems     []SystemMetrics        `json:"lastTickSystems,omitempty"`
	LastTickCommands    []CommandMetrics       `json:"lastTickCommands,omitempty"`
}

// SnapshotMetrics represents snapshot generation metrics.
type SnapshotMetrics struct {
	TotalGenerations   int64   `json:"totalGenerations"`
	CacheHits          int64   `json:"cacheHits"`
	CacheMisses        int64   `json:"cacheMisses"`
	AvgGenerationMs    float64 `json:"avgGenerationMs"`
	LastGenerationMs   float64 `json:"lastGenerationMs"`
	MaxGenerationMs    float64 `json:"maxGenerationMs"`
	CacheHitRate       float64 `json:"cacheHitRate"`
	IncrementalRate    float64 `json:"incrementalRate"`
}

// SystemMetrics represents per-system execution metrics.
type SystemMetrics struct {
	SystemName      string  `json:"systemName"`
	ExecutionTimeMs float64 `json:"executionTimeMs"`
	Success         bool    `json:"success"`
}

// CommandMetrics represents per-command execution metrics.
type CommandMetrics struct {
	CommandName     string  `json:"commandName"`
	ExecutionTimeMs float64 `json:"executionTimeMs"`
	Success         bool    `json:"success"`
}

// GetContainerMetrics gets metrics for a container.
func (e *EngineClient) GetContainerMetrics(containerID int64) (*ContainerMetrics, error) {
	var resp ContainerMetrics
	path := fmt.Sprintf("/api/containers/%d/metrics", containerID)
	if err := e.get(path, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// NodeMetrics represents node-level metrics.
type NodeMetrics struct {
	CPUUsage         float64 `json:"cpuUsage"`
	MemoryUsed       int64   `json:"memoryUsed"`
	MemoryTotal      int64   `json:"memoryTotal"`
	ActiveContainers int     `json:"activeContainers"`
	ActiveMatches    int     `json:"activeMatches"`
}

// GetNodeMetrics gets metrics for the engine node.
func (e *EngineClient) GetNodeMetrics() (*NodeMetrics, error) {
	var resp NodeMetrics
	if err := e.get("/api/node/metrics", &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// SimulationStatus represents the play status of a container.
type SimulationStatus struct {
	Playing     bool  `json:"playing"`
	CurrentTick int64 `json:"currentTick"`
	IntervalMs  int64 `json:"intervalMs"`
}

// StartSimulation starts auto-advancing the container.
func (e *EngineClient) StartSimulation(containerID int64, intervalMs int64) error {
	path := fmt.Sprintf("/api/containers/%d/play?intervalMs=%d", containerID, intervalMs)
	return e.post(path, nil, nil)
}

// StopSimulation stops auto-advancing the container.
func (e *EngineClient) StopSimulation(containerID int64) error {
	path := fmt.Sprintf("/api/containers/%d/stop-auto", containerID)
	return e.post(path, nil, nil)
}

// GetSimulationStatus gets the play status of a container.
func (e *EngineClient) GetSimulationStatus(containerID int64) (*SimulationStatus, error) {
	var resp SimulationStatus
	path := fmt.Sprintf("/api/containers/%d/status", containerID)
	if err := e.get(path, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

func (e *EngineClient) get(path string, result interface{}) error {
	req, err := http.NewRequest(http.MethodGet, e.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return e.doRequest(req, result)
}

func (e *EngineClient) post(path string, body, result interface{}) error {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("marshaling request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(http.MethodPost, e.baseURL+path, bodyReader)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	return e.doRequest(req, result)
}

func (e *EngineClient) doRequest(req *http.Request, result interface{}) error {
	req.Header.Set("Accept", "application/json")
	setAuthHeaders(req, e.authToken)

	resp, err := e.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("making request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	// 202 Accepted is success for commands
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

// Helper methods for HTTP operations

func (c *Client) get(path string, result interface{}) error {
	req, err := http.NewRequest(http.MethodGet, c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return c.doRequest(req, result)
}

func (c *Client) post(path string, body, result interface{}) error {
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

func (c *Client) delete(path string) error {
	req, err := http.NewRequest(http.MethodDelete, c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("creating request: %w", err)
	}
	return c.doRequest(req, nil)
}

func (c *Client) doRequest(req *http.Request, result interface{}) error {
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
