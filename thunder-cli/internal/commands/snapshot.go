package commands

import (
	"fmt"
	"regexp"
	"strconv"

	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/api"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/config"
	"github.com/spf13/cobra"
)

var snapshotCmd = &cobra.Command{
	Use:   "snapshot",
	Short: "Get game state snapshots",
	Long: `Retrieve game state snapshots from the current match context.

Before using these commands, set a node context with 'thunder node context set <node-id>'
and optionally a match context with 'thunder node context match <match-id>'.

Commands:
  get - Get the current snapshot`,
}


// Get subcommand
var snapshotGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Get the current snapshot for the match",
	Long: `Get the current game state snapshot for the current match context.

The snapshot contains all entity component data organized by module.

Examples:
  thunder snapshot get
  thunder snapshot get -o json`,
	RunE: runSnapshotGet,
}

func init() {
	snapshotCmd.AddCommand(snapshotGetCmd)
}

func runSnapshotGet(cmd *cobra.Command, args []string) error {
	// Check match context
	clusterMatchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	nodeID := config.GetCurrentNodeID()

	if clusterMatchID == "" || nodeID == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' and 'thunder node context match <match-id>' first")
	}

	// Parse the internal match ID from the cluster match ID
	// Format: nodeId-containerId-matchId (e.g., "node-1-1-1")
	internalMatchID, err := extractInternalMatchID(clusterMatchID)
	if err != nil {
		return fmt.Errorf("cannot parse match ID '%s': %w", clusterMatchID, err)
	}

	// Create engine client (uses proxy if enabled)
	var engineClient *api.EngineClient
	if config.GetUseNodeProxy() {
		engineClient = api.NewProxiedEngineClient(config.GetControlPlaneURL(), nodeID, config.GetAuthToken())
	} else {
		engineClient = api.NewEngineClient(config.GetCurrentEngineURL(), config.GetAuthToken())
	}

	// Get snapshot
	snapshot, err := engineClient.GetSnapshot(containerID, internalMatchID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Output
	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.PrintJSON(snapshot)
		return nil
	}

	// Table format - summary view
	out.PrintMessage(fmt.Sprintf("Snapshot for match %s (tick %d):", clusterMatchID, snapshot.Tick))

	if snapshot.Error != nil {
		out.PrintMessage(fmt.Sprintf("  Error: %s", *snapshot.Error))
		return nil
	}

	totalEntities := 0
	for _, module := range snapshot.Modules {
		if len(module.Components) > 0 && len(module.Components[0].Values) > 0 {
			entityCount := len(module.Components[0].Values)
			if entityCount > totalEntities {
				totalEntities = entityCount
			}
		}
	}

	out.PrintMessage(fmt.Sprintf("  Entities: %d", totalEntities))
	out.PrintMessage(fmt.Sprintf("  Modules: %d", len(snapshot.Modules)))

	for _, module := range snapshot.Modules {
		out.PrintMessage(fmt.Sprintf("\n  %s (v%s):", module.Name, module.Version))
		out.PrintMessage(fmt.Sprintf("    Components: %d", len(module.Components)))

		// Show first few component names
		for i, comp := range module.Components {
			if i >= 5 {
				out.PrintMessage(fmt.Sprintf("    ... and %d more", len(module.Components)-5))
				break
			}
			values := "[]"
			if len(comp.Values) > 0 {
				if len(comp.Values) <= 3 {
					values = fmt.Sprintf("%v", comp.Values)
				} else {
					values = fmt.Sprintf("[%v, %v, %v, ... (%d total)]",
						comp.Values[0], comp.Values[1], comp.Values[2], len(comp.Values))
				}
			}
			out.PrintMessage(fmt.Sprintf("      %s: %s", comp.Name, values))
		}
	}

	return nil
}

// extractInternalMatchID extracts the internal match ID from a cluster match ID.
// Format: nodeId-containerId-matchId (e.g., "node-1-1-1" -> 1)
func extractInternalMatchID(clusterMatchID string) (int64, error) {
	re := regexp.MustCompile(`-(\d+)$`)
	matches := re.FindStringSubmatch(clusterMatchID)
	if len(matches) != 2 {
		return 0, fmt.Errorf("invalid match ID format")
	}
	return strconv.ParseInt(matches[1], 10, 64)
}
