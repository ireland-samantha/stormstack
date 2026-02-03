// Package commands implements CLI commands for the Lightning CLI.
package commands

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"syscall"

	"github.com/ireland-samantha/stormstack/lightning-cli/internal/config"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Authentication management",
	Long: `Manage authentication for the Lightning CLI.

Commands:
  login   - Authenticate with username/password and save token
  refresh - Refresh authentication token using stored refresh token
  token   - Set an API token directly
  status  - Show current authentication status
  logout  - Remove saved authentication`,
}

// Login subcommand
var authLoginCmd = &cobra.Command{
	Use:   "login",
	Short: "Authenticate with username and password",
	Long: `Authenticate via the Control Plane using username and password.
The control plane proxies the request to the auth service via OAuth2 password grant.
The resulting access and refresh tokens are saved to ~/.lightning.yaml for future use.

Examples:
  lightning auth login
  lightning auth login --username admin --password admin
  lightning auth login --control-plane-url http://localhost:8081`,
	RunE: runAuthLogin,
}

var (
	authUsername string
	authPassword string
)

func init() {
	authLoginCmd.Flags().StringVar(&authUsername, "username", "", "Username (for non-interactive login)")
	authLoginCmd.Flags().StringVar(&authPassword, "password", "", "Password (for non-interactive login)")
	authCmd.AddCommand(authLoginCmd)
}

func runAuthLogin(cmd *cobra.Command, args []string) error {
	var username, password string

	// Check if credentials provided via flags (non-interactive)
	if authUsername != "" && authPassword != "" {
		username = authUsername
		password = authPassword
	} else if authUsername != "" || authPassword != "" {
		return fmt.Errorf("both --username and --password must be provided for non-interactive login")
	} else {
		// Interactive mode - prompt for credentials
		reader := bufio.NewReader(os.Stdin)

		// Prompt for username
		fmt.Print("Username: ")
		usernameInput, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("failed to read username: %w", err)
		}
		username = strings.TrimSpace(usernameInput)

		// Prompt for password (hidden)
		fmt.Print("Password: ")
		passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
		if err != nil {
			return fmt.Errorf("failed to read password: %w", err)
		}
		fmt.Println() // New line after password input
		password = string(passwordBytes)
	}

	// Get control plane URL from config
	controlPlaneURL := config.GetControlPlaneURL()

	// Make login request via control plane auth proxy
	// The control plane translates this to OAuth2 password grant internally
	loginReq := map[string]string{
		"username": username,
		"password": password,
	}
	body, err := json.Marshal(loginReq)
	if err != nil {
		return fmt.Errorf("failed to encode request: %w", err)
	}

	resp, err := http.Post(controlPlaneURL+"/api/auth/login", "application/json", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("failed to connect to control plane: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != 200 {
		return fmt.Errorf("authentication failed: %s", string(respBody))
	}

	var loginResp struct {
		Token        string `json:"token"`
		RefreshToken string `json:"refreshToken"`
	}
	if err := json.Unmarshal(respBody, &loginResp); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	// Save tokens to config
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.AuthToken = loginResp.Token
	cfg.RefreshToken = loginResp.RefreshToken
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save token: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Authenticated as %s", username))
	return nil
}

// Token subcommand - set API token directly
var authTokenCmd = &cobra.Command{
	Use:   "token <api-token>",
	Short: "Set an API token directly",
	Long: `Set an API token for authentication.
Use this with API tokens generated from the Lightning Auth admin panel.

Examples:
  lightning auth token lat_abc123...`,
	Args: cobra.ExactArgs(1),
	RunE: runAuthToken,
}

func init() {
	authCmd.AddCommand(authTokenCmd)
}

func runAuthToken(cmd *cobra.Command, args []string) error {
	token := args[0]

	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.AuthToken = token
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save token: %w", err)
	}

	out.PrintSuccess("API token saved")
	return nil
}

// Status subcommand
var authStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show current authentication status",
	RunE:  runAuthStatus,
}

func init() {
	authCmd.AddCommand(authStatusCmd)
}

func runAuthStatus(cmd *cobra.Command, args []string) error {
	token := config.GetAuthToken()
	if token == "" {
		out.PrintMessage("Not authenticated")
		return nil
	}

	// Show token type (JWT vs API token)
	if strings.HasPrefix(token, "lat_") {
		out.PrintSuccess("Authenticated with API token")
	} else if strings.HasPrefix(token, "eyJ") {
		out.PrintSuccess("Authenticated with JWT token")
	} else {
		out.PrintSuccess("Authenticated (unknown token type)")
	}
	return nil
}

// Logout subcommand
var authLogoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Remove saved authentication",
	RunE:  runAuthLogout,
}

func init() {
	authCmd.AddCommand(authLogoutCmd)
}

func runAuthLogout(cmd *cobra.Command, args []string) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.AuthToken = ""
	cfg.RefreshToken = ""
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	out.PrintSuccess("Logged out")
	return nil
}

// Refresh subcommand
var authRefreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Refresh the authentication token",
	Long: `Refresh the current authentication token using the stored refresh token.
This command uses the OAuth2 refresh_token grant type to obtain new access
and refresh tokens without requiring credentials.

The refresh token must have been obtained from a previous 'lightning auth login'.

Examples:
  lightning auth refresh`,
	RunE: runAuthRefresh,
}

func init() {
	authCmd.AddCommand(authRefreshCmd)
}

func runAuthRefresh(cmd *cobra.Command, args []string) error {
	// Get refresh token from config
	refreshToken := config.GetRefreshToken()
	if refreshToken == "" {
		return fmt.Errorf("no refresh token available - please run 'lightning auth login' first")
	}

	// Get control plane URL from config
	controlPlaneURL := config.GetControlPlaneURL()

	// Make refresh request via control plane auth proxy
	// The control plane translates this to OAuth2 refresh_token grant internally
	refreshReq := map[string]string{
		"grant_type":    "refresh_token",
		"refresh_token": refreshToken,
	}
	body, err := json.Marshal(refreshReq)
	if err != nil {
		return fmt.Errorf("failed to encode request: %w", err)
	}

	resp, err := http.Post(controlPlaneURL+"/api/auth/token", "application/json", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("failed to connect to control plane: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != 200 {
		return fmt.Errorf("token refresh failed: %s", string(respBody))
	}

	var tokenResp struct {
		AccessToken  string `json:"access_token"`
		RefreshToken string `json:"refresh_token"`
		TokenType    string `json:"token_type"`
		ExpiresIn    int    `json:"expires_in"`
	}
	if err := json.Unmarshal(respBody, &tokenResp); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	// Save new tokens to config
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.AuthToken = tokenResp.AccessToken
	if tokenResp.RefreshToken != "" {
		cfg.RefreshToken = tokenResp.RefreshToken
	}
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save tokens: %w", err)
	}

	out.PrintSuccess("Token refreshed successfully")
	return nil
}
