package commands

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/ireland-samantha/stormstack/lightning-cli/internal/api"
	"github.com/ireland-samantha/stormstack/lightning-cli/internal/config"
	"github.com/spf13/cobra"
)

var commandCmd = &cobra.Command{
	Use:   "command",
	Short: "Send commands to a match",
	Long: `Send game commands to the current match context.

Before using these commands, set a node context with 'lightning node context set <node-id>'
and optionally a match context with 'lightning node context match <match-id>'.

Commands:
  send       - Send a single command to the match
  send-bulk  - Send multiple commands from a JSON file
  list       - List available commands`,
}


// Send subcommand
var commandSendCmd = &cobra.Command{
	Use:   "send <command-name> [parameters-json]",
	Short: "Send a command to the current match",
	Long: `Send a game command to the current match context.

The command name is required. Parameters can be provided as a JSON object.
Use 'lightning command list' to see available commands and their parameters.

Examples:
  lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'
  lightning command send move '{"entityId":1,"x":10,"y":20}'
  lightning command send tick`,
	Args: cobra.RangeArgs(1, 2),
	RunE: runCommandSend,
}

func init() {
	commandCmd.AddCommand(commandSendCmd)
}

func runCommandSend(cmd *cobra.Command, args []string) error {
	// Check match context
	matchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	nodeID := config.GetCurrentNodeID()

	if matchID == "" || nodeID == "" {
		return fmt.Errorf("no context set. Use 'lightning node context set <node-id>' and 'lightning node context match <match-id>' first")
	}

	commandName := args[0]
	var parameters map[string]interface{}

	// Parse parameters if provided
	if len(args) > 1 {
		if err := json.Unmarshal([]byte(args[1]), &parameters); err != nil {
			return fmt.Errorf("invalid JSON parameters: %w", err)
		}
	}

	// Create engine client (uses proxy if enabled)
	var engineClient *api.EngineClient
	if config.GetUseNodeProxy() {
		engineClient = api.NewProxiedEngineClient(config.GetControlPlaneURL(), nodeID, config.GetAuthToken())
	} else {
		engineClient = api.NewEngineClient(config.GetCurrentEngineURL(), config.GetAuthToken())
	}

	// Send command
	req := &api.CommandRequest{
		CommandName: commandName,
		Parameters:  parameters,
	}

	if err := engineClient.SendCommand(containerID, req); err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Command '%s' sent to match %s", commandName, matchID))
	return nil
}

// Send-bulk subcommand
var commandSendBulkCmd = &cobra.Command{
	Use:   "send-bulk <commands-file>",
	Short: "Send multiple commands from a JSON file",
	Long: `Send multiple game commands from a JSON file to the current match context.

The file should contain a JSON array of command objects:
[
  {"command": "spawn", "params": {"matchId":1,"playerId":1,"entityType":100}},
  {"command": "move", "params": {"entityId":1,"x":10,"y":20}},
  {"command": "attack", "params": {"attackerId":1,"targetId":2}}
]

Examples:
  lightning command send-bulk commands.json
  lightning command send-bulk commands.json --advance-tick`,
	Args: cobra.ExactArgs(1),
	RunE: runCommandSendBulk,
}

var advanceTick bool

func init() {
	commandSendBulkCmd.Flags().BoolVar(&advanceTick, "advance-tick", false, "Advance tick after each command")
	commandCmd.AddCommand(commandSendBulkCmd)
}

type BulkCommand struct {
	Command string                 `json:"command"`
	Params  map[string]interface{} `json:"params"`
}

func runCommandSendBulk(cmd *cobra.Command, args []string) error {
	// Check match context
	matchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	nodeID := config.GetCurrentNodeID()

	if matchID == "" || nodeID == "" {
		return fmt.Errorf("no context set. Use 'lightning node context set <node-id>' and 'lightning node context match <match-id>' first")
	}

	// Read commands file
	filePath := args[0]
	fileData, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("reading commands file: %w", err)
	}

	// Parse commands
	var commands []BulkCommand
	if err := json.Unmarshal(fileData, &commands); err != nil {
		return fmt.Errorf("parsing commands file: %w", err)
	}

	if len(commands) == 0 {
		return fmt.Errorf("no commands found in file")
	}

	out.PrintMessage(fmt.Sprintf("Sending %d commands to match %s...", len(commands), matchID))

	// Create engine client (uses proxy if enabled)
	var engineClient *api.EngineClient
	if config.GetUseNodeProxy() {
		engineClient = api.NewProxiedEngineClient(config.GetControlPlaneURL(), nodeID, config.GetAuthToken())
	} else {
		engineClient = api.NewEngineClient(config.GetCurrentEngineURL(), config.GetAuthToken())
	}

	// Send each command
	successCount := 0
	for i, bulkCmd := range commands {
		req := &api.CommandRequest{
			CommandName: bulkCmd.Command,
			Parameters:  bulkCmd.Params,
		}

		if err := engineClient.SendCommand(containerID, req); err != nil {
			out.PrintError(fmt.Errorf("command %d (%s) failed: %w", i+1, bulkCmd.Command, err))
			continue
		}

		successCount++

		// Advance tick if requested
		if advanceTick {
			if _, err := engineClient.AdvanceTick(containerID); err != nil {
				out.PrintError(fmt.Errorf("tick advance failed after command %d: %w", i+1, err))
			}
		}
	}

	if successCount == len(commands) {
		out.PrintSuccess(fmt.Sprintf("All %d commands sent successfully", successCount))
	} else {
		out.PrintMessage(fmt.Sprintf("%d of %d commands sent successfully", successCount, len(commands)))
	}

	return nil
}

// List subcommand
var commandListCmd = &cobra.Command{
	Use:   "list",
	Short: "List available commands for the current match",
	Long: `List all available game commands for the current match context.

Examples:
  lightning command list
  lightning command list -o json`,
	RunE: runCommandList,
}

func init() {
	commandCmd.AddCommand(commandListCmd)
}

func runCommandList(cmd *cobra.Command, args []string) error {
	// Check match context
	matchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	nodeID := config.GetCurrentNodeID()

	if matchID == "" || nodeID == "" {
		return fmt.Errorf("no context set. Use 'lightning node context set <node-id>' and 'lightning node context match <match-id>' first")
	}

	// Create engine client (uses proxy if enabled)
	var engineClient *api.EngineClient
	if config.GetUseNodeProxy() {
		engineClient = api.NewProxiedEngineClient(config.GetControlPlaneURL(), nodeID, config.GetAuthToken())
	} else {
		engineClient = api.NewEngineClient(config.GetCurrentEngineURL(), config.GetAuthToken())
	}

	// List commands
	commands, err := engineClient.ListCommands(containerID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Output
	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.PrintJSON(commands)
		return nil
	}

	// Table format
	if len(commands) == 0 {
		out.PrintMessage("No commands available")
		return nil
	}

	out.PrintMessage(fmt.Sprintf("Available commands for match %s:", matchID))
	for _, c := range commands {
		out.PrintMessage(fmt.Sprintf("\n  %s (%s)", c.Name, c.Module))
		if c.Description != "" {
			out.PrintMessage(fmt.Sprintf("    %s", c.Description))
		}
		if len(c.Parameters) > 0 {
			out.PrintMessage("    Parameters:")
			for _, p := range c.Parameters {
				required := ""
				if p.Required {
					required = " (required)"
				}
				out.PrintMessage(fmt.Sprintf("      - %s: %s%s", p.Name, p.Type, required))
			}
		}
	}

	return nil
}
