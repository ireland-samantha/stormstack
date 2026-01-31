// Package config provides configuration management for the Thunder CLI.
package config

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/viper"
)

const (
	// DefaultControlPlaneURL is the default control plane endpoint.
	DefaultControlPlaneURL = "http://localhost:8081"
	// DefaultAuthURL is the default auth service endpoint.
	DefaultAuthURL = "http://localhost:8082"
	// ConfigFileName is the name of the config file.
	ConfigFileName = ".thunder"
	// ConfigFileType is the config file format.
	ConfigFileType = "yaml"
)

// Config holds the CLI configuration.
type Config struct {
	ControlPlaneURL string `mapstructure:"control_plane_url"`
	AuthURL         string `mapstructure:"auth_url"`
	AuthToken       string `mapstructure:"auth_token"`
	RefreshToken    string `mapstructure:"refresh_token"`
	OutputFormat    string `mapstructure:"output_format"`
	// Node context - set by 'thunder node context set'
	CurrentNodeID    string `mapstructure:"current_node_id"`
	CurrentEngineURL string `mapstructure:"current_engine_url"`
	// Match context - set by 'thunder node context set' (for match-specific operations)
	CurrentMatchID     string `mapstructure:"current_match_id"`
	CurrentContainerID int64  `mapstructure:"current_container_id"`
	// UseNodeProxy controls whether to route node requests through the control plane proxy.
	// When true (default), requests to nodes go through the control plane at
	// /api/nodes/{nodeId}/proxy/{path}, allowing access when nodes are on
	// Docker-internal networks.
	UseNodeProxy bool `mapstructure:"use_node_proxy"`
	// WebSocket context - set by 'thunder match join'
	CurrentMatchToken    string `mapstructure:"current_match_token"`
	CurrentCommandWsURL  string `mapstructure:"current_command_ws_url"`
	CurrentSnapshotWsURL string `mapstructure:"current_snapshot_ws_url"`
}

// Load reads configuration from file and environment.
func Load() (*Config, error) {
	viper.SetConfigName(ConfigFileName)
	viper.SetConfigType(ConfigFileType)

	// Search paths: current dir, home dir
	viper.AddConfigPath(".")
	if home, err := os.UserHomeDir(); err == nil {
		viper.AddConfigPath(home)
	}

	// Environment variables
	viper.SetEnvPrefix("THUNDER")
	viper.AutomaticEnv()

	// Defaults
	viper.SetDefault("control_plane_url", DefaultControlPlaneURL)
	viper.SetDefault("auth_url", DefaultAuthURL)
	viper.SetDefault("output_format", "table")
	viper.SetDefault("use_node_proxy", true)

	// Read config file (ignore if not found)
	if err := viper.ReadInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, fmt.Errorf("error reading config: %w", err)
		}
	}

	var cfg Config
	if err := viper.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("error parsing config: %w", err)
	}

	return &cfg, nil
}

// Save writes the configuration to the user's home directory.
func Save(cfg *Config) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return fmt.Errorf("cannot find home directory: %w", err)
	}

	viper.Set("control_plane_url", cfg.ControlPlaneURL)
	viper.Set("auth_url", cfg.AuthURL)
	viper.Set("auth_token", cfg.AuthToken)
	viper.Set("refresh_token", cfg.RefreshToken)
	viper.Set("output_format", cfg.OutputFormat)
	viper.Set("current_node_id", cfg.CurrentNodeID)
	viper.Set("current_engine_url", cfg.CurrentEngineURL)
	viper.Set("current_match_id", cfg.CurrentMatchID)
	viper.Set("current_container_id", cfg.CurrentContainerID)
	viper.Set("use_node_proxy", cfg.UseNodeProxy)
	viper.Set("current_match_token", cfg.CurrentMatchToken)
	viper.Set("current_command_ws_url", cfg.CurrentCommandWsURL)
	viper.Set("current_snapshot_ws_url", cfg.CurrentSnapshotWsURL)

	configPath := filepath.Join(home, ConfigFileName+"."+ConfigFileType)
	return viper.WriteConfigAs(configPath)
}

// GetControlPlaneURL returns the configured control plane URL.
func GetControlPlaneURL() string {
	return viper.GetString("control_plane_url")
}

// GetAuthURL returns the configured auth service URL.
func GetAuthURL() string {
	return viper.GetString("auth_url")
}

// GetAuthToken returns the configured auth token.
func GetAuthToken() string {
	return viper.GetString("auth_token")
}

// GetOutputFormat returns the configured output format.
func GetOutputFormat() string {
	return viper.GetString("output_format")
}

// SetControlPlaneURL sets the control plane URL.
func SetControlPlaneURL(url string) {
	viper.Set("control_plane_url", url)
}

// SetAuthURL sets the auth service URL.
func SetAuthURL(url string) {
	viper.Set("auth_url", url)
}

// SetAuthToken sets the auth token.
func SetAuthToken(token string) {
	viper.Set("auth_token", token)
}

// GetRefreshToken returns the configured refresh token.
func GetRefreshToken() string {
	return viper.GetString("refresh_token")
}

// SetRefreshToken sets the refresh token.
func SetRefreshToken(token string) {
	viper.Set("refresh_token", token)
}

// SetOutputFormat sets the output format.
func SetOutputFormat(format string) {
	viper.Set("output_format", format)
}

// GetCurrentNodeID returns the current node context.
func GetCurrentNodeID() string {
	return viper.GetString("current_node_id")
}

// GetCurrentEngineURL returns the current engine URL.
func GetCurrentEngineURL() string {
	return viper.GetString("current_engine_url")
}

// GetCurrentMatchID returns the current match context.
func GetCurrentMatchID() string {
	return viper.GetString("current_match_id")
}

// GetCurrentContainerID returns the current container ID.
func GetCurrentContainerID() int64 {
	return viper.GetInt64("current_container_id")
}

// SetNodeContext sets the current node context.
func SetNodeContext(nodeID string, engineURL string) {
	viper.Set("current_node_id", nodeID)
	viper.Set("current_engine_url", engineURL)
}

// SetMatchContext sets the current match context (for match-specific operations).
func SetMatchContext(matchID string, containerID int64) {
	viper.Set("current_match_id", matchID)
	viper.Set("current_container_id", containerID)
}

// ClearNodeContext clears the current node context.
func ClearNodeContext() {
	viper.Set("current_node_id", "")
	viper.Set("current_engine_url", "")
	viper.Set("current_match_id", "")
	viper.Set("current_container_id", 0)
}

// GetUseNodeProxy returns whether to use the node proxy.
func GetUseNodeProxy() bool {
	return viper.GetBool("use_node_proxy")
}

// SetUseNodeProxy sets whether to use the node proxy.
func SetUseNodeProxy(useProxy bool) {
	viper.Set("use_node_proxy", useProxy)
}

// GetCurrentMatchToken returns the current match token.
func GetCurrentMatchToken() string {
	return viper.GetString("current_match_token")
}

// GetCurrentCommandWsURL returns the current command WebSocket URL.
func GetCurrentCommandWsURL() string {
	return viper.GetString("current_command_ws_url")
}

// GetCurrentSnapshotWsURL returns the current snapshot WebSocket URL.
func GetCurrentSnapshotWsURL() string {
	return viper.GetString("current_snapshot_ws_url")
}

// SetWebSocketContext sets the current WebSocket context.
func SetWebSocketContext(matchToken, commandWsURL, snapshotWsURL string) {
	viper.Set("current_match_token", matchToken)
	viper.Set("current_command_ws_url", commandWsURL)
	viper.Set("current_snapshot_ws_url", snapshotWsURL)
}

// ClearWebSocketContext clears the current WebSocket context.
func ClearWebSocketContext() {
	viper.Set("current_match_token", "")
	viper.Set("current_command_ws_url", "")
	viper.Set("current_snapshot_ws_url", "")
}
