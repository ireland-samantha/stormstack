package commands

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"github.com/ireland-samantha/stormstack/lightning-cli/internal/config"
	"github.com/ireland-samantha/stormstack/lightning-cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	matchStatusFilter string
)

var matchCmd = &cobra.Command{
	Use:   "match",
	Short: "Match management commands",
	Long: `Manage matches in the Lightning Engine cluster.

Commands:
  list    - List all matches
  get     - Get match details
  join    - Join a match and get a match token
  finish  - Mark a match as finished
  delete  - Delete a match
  context - Context management (set, show)`,
}

// =============================================================================
// Context Commands
// =============================================================================

var matchContextCmd = &cobra.Command{
	Use:   "context",
	Short: "Context management commands",
	Long: `Set or show the current match context for subsequent commands.

Commands:
  set   - Set context from match ID
  show  - Show current context`,
}

func init() {
	matchCmd.AddCommand(matchContextCmd)
}

// List subcommand
var matchListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all matches in the cluster",
	Long: `List all matches in the cluster, optionally filtered by status.

Examples:
  lightning match list
  lightning match list --status RUNNING
  lightning match list -o json`,
	RunE: runMatchList,
}

func init() {
	matchListCmd.Flags().StringVarP(&matchStatusFilter, "status", "s", "", "Filter by status (RUNNING, FINISHED, ERROR)")
	matchCmd.AddCommand(matchListCmd)
}

func runMatchList(cmd *cobra.Command, args []string) error {
	matches, err := apiClient.ListMatches(matchStatusFilter)
	if err != nil {
		out.PrintError(err)
		return err
	}

	rows := make([]output.MatchRow, 0, len(matches))
	for _, m := range matches {
		rows = append(rows, output.MatchRow{
			MatchID:     m.MatchID,
			NodeID:      m.NodeID,
			Status:      m.Status,
			PlayerCount: m.PlayerCount,
			Modules:     strings.Join(m.ModuleNames, ", "),
		})
	}

	out.PrintMatches(rows)
	return nil
}

// Get subcommand
var matchGetCmd = &cobra.Command{
	Use:   "get <match-id>",
	Short: "Get details for a specific match",
	Long: `Get detailed information about a specific match.

Examples:
  lightning match get node-1-42-7
  lightning match get node-1-42-7 -o json`,
	Args: cobra.ExactArgs(1),
	RunE: runMatchGet,
}

func init() {
	matchCmd.AddCommand(matchGetCmd)
}

func runMatchGet(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	match, err := apiClient.GetMatch(matchID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	rows := []output.MatchRow{
		{
			MatchID:     match.MatchID,
			NodeID:      match.NodeID,
			Status:      match.Status,
			PlayerCount: match.PlayerCount,
			Modules:     strings.Join(match.ModuleNames, ", "),
		},
	}

	out.PrintMatches(rows)
	return nil
}

// Join subcommand
var matchJoinCmd = &cobra.Command{
	Use:   "join <match-id>",
	Short: "Join a match and get a match token",
	Long: `Join a match as a player and receive a match token for WebSocket connections.

The join command:
1. Validates the match can accept more players
2. Issues a match token for authentication
3. Stores the token and WebSocket URLs for ws commands

Examples:
  lightning match join node-1-42-7 --player-name "Player1" --player-id "player-123"
  lightning match join node-1-42-7 -n "Alice" -p "alice-001" -o json`,
	Args: cobra.ExactArgs(1),
	RunE: runMatchJoin,
}

var (
	joinPlayerName string
	joinPlayerID   string
)

func init() {
	matchJoinCmd.Flags().StringVarP(&joinPlayerName, "player-name", "n", "", "Player display name (required)")
	matchJoinCmd.Flags().StringVarP(&joinPlayerID, "player-id", "p", "", "Player unique ID (required)")
	matchJoinCmd.MarkFlagRequired("player-name")
	matchJoinCmd.MarkFlagRequired("player-id")
	matchCmd.AddCommand(matchJoinCmd)
}

func runMatchJoin(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	resp, err := apiClient.JoinMatch(matchID, joinPlayerID, joinPlayerName)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Store the match token and WebSocket URLs in config
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}
	cfg.CurrentMatchToken = resp.MatchToken
	cfg.CurrentCommandWsURL = resp.CommandWebSocketURL
	cfg.CurrentSnapshotWsURL = resp.SnapshotWebSocketURL
	cfg.CurrentMatchID = matchID
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	// For JSON output, show full response
	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(resp)
		return nil
	}

	out.PrintSuccess(fmt.Sprintf("Joined match %s as %s (%s)", matchID, resp.PlayerName, resp.PlayerID))
	out.PrintMessage(fmt.Sprintf("Match token stored. Expires: %s", resp.TokenExpiresAt.Format("2006-01-02 15:04:05")))
	out.PrintMessage(fmt.Sprintf("Command WebSocket: %s", resp.CommandWebSocketURL))
	out.PrintMessage(fmt.Sprintf("Snapshot WebSocket: %s", resp.SnapshotWebSocketURL))
	out.PrintMessage("\nUse 'lightning ws connect command' or 'lightning ws connect snapshot' to connect.")
	return nil
}

// Finish subcommand
var matchFinishCmd = &cobra.Command{
	Use:   "finish <match-id>",
	Short: "Mark a match as finished",
	Long: `Mark a match as finished. Finished matches remain in the registry but are no longer active.

Examples:
  lightning match finish node-1-42-7`,
	Args: cobra.ExactArgs(1),
	RunE: runMatchFinish,
}

func init() {
	matchCmd.AddCommand(matchFinishCmd)
}

func runMatchFinish(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	match, err := apiClient.FinishMatch(matchID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Match %s finished (status: %s)", match.MatchID, match.Status))
	return nil
}

// Delete subcommand
var matchDeleteCmd = &cobra.Command{
	Use:   "delete <match-id>",
	Short: "Delete a match from the cluster",
	Long: `Delete a match from the cluster. This also removes the match from the hosting node.

Examples:
  lightning match delete node-1-42-7`,
	Args: cobra.ExactArgs(1),
	RunE: runMatchDelete,
}

func init() {
	matchCmd.AddCommand(matchDeleteCmd)
}

func runMatchDelete(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	if err := apiClient.DeleteMatch(matchID); err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Match %s deleted", matchID))
	return nil
}

// context set subcommand (deprecated - use 'lightning node context set')
var matchContextSetCmd = &cobra.Command{
	Use:        "set <match-id>",
	Short:      "Set the current match context (deprecated: use 'lightning node context set')",
	Deprecated: "use 'lightning node context set <node-id>' and 'lightning node context match <match-id>' instead",
	Args:       cobra.ExactArgs(1),
	RunE:       runMatchContextSet,
}

func init() {
	matchContextCmd.AddCommand(matchContextSetCmd)
}

func runMatchContextSet(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	// Get deployment info for this match
	deploy, err := apiClient.GetDeployment(matchID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Extract engine base URL from HTTP endpoint
	engineURL := extractBaseURL(deploy.Endpoints.HTTP)

	// Save match context to config
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.CurrentNodeID = deploy.NodeID
	cfg.CurrentEngineURL = engineURL
	cfg.CurrentMatchID = matchID
	cfg.CurrentContainerID = deploy.ContainerID
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Context set: %s (container %d at %s)", matchID, deploy.ContainerID, engineURL))
	out.PrintMessage("Note: 'lightning match context set' is deprecated. Use 'lightning node context set <node-id>' instead.")
	return nil
}

// extractBaseURL extracts the base URL from a full endpoint URL.
// e.g., "http://backend:8080/api/containers/1" -> "http://backend:8080"
func extractBaseURL(fullURL string) string {
	// Match protocol://host:port
	re := regexp.MustCompile(`^(https?://[^/]+)`)
	matches := re.FindStringSubmatch(fullURL)
	if len(matches) > 1 {
		return matches[1]
	}
	return fullURL
}

// context show subcommand (deprecated - use 'lightning node context show')
var matchContextShowCmd = &cobra.Command{
	Use:        "show",
	Short:      "Show the current match context (deprecated: use 'lightning node context show')",
	Deprecated: "use 'lightning node context show' instead",
	RunE:       runMatchContextShow,
}

func init() {
	matchContextCmd.AddCommand(matchContextShowCmd)
}

func runMatchContextShow(cmd *cobra.Command, args []string) error {
	nodeID := config.GetCurrentNodeID()
	matchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	engineURL := config.GetCurrentEngineURL()

	if nodeID == "" && matchID == "" {
		out.PrintMessage("No context set. Use 'lightning node context set <node-id>' to set one.")
		return nil
	}

	// For JSON output, include parsed match ID components
	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		data := map[string]interface{}{
			"nodeId":      nodeID,
			"engineUrl":   engineURL,
			"matchId":     matchID,
			"containerId": containerID,
		}
		out.Print(data)
		return nil
	}

	out.PrintMessage(fmt.Sprintf("Node ID:      %s", nodeID))
	out.PrintMessage(fmt.Sprintf("Engine URL:   %s", engineURL))
	if matchID != "" {
		out.PrintMessage(fmt.Sprintf("Match ID:     %s", matchID))
		out.PrintMessage(fmt.Sprintf("Container ID: %d", containerID))
	}
	return nil
}

// parseMatchID parses a match ID like "node-1-1-1" into components.
func parseMatchID(matchID string) map[string]interface{} {
	// Format: nodeId-containerId-matchId (e.g., "node-1-1-1")
	re := regexp.MustCompile(`^(.+)-(\d+)-(\d+)$`)
	matches := re.FindStringSubmatch(matchID)
	if len(matches) != 4 {
		return nil
	}
	containerID, _ := strconv.ParseInt(matches[2], 10, 64)
	internalMatchID, _ := strconv.ParseInt(matches[3], 10, 64)
	return map[string]interface{}{
		"nodeId":      matches[1],
		"containerId": containerID,
		"matchId":     internalMatchID,
	}
}
