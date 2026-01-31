package commands

import (
	"fmt"
	"regexp"

	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/api"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/config"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/output"
	"github.com/spf13/cobra"
)

var nodeCmd = &cobra.Command{
	Use:   "node",
	Short: "Engine node commands",
	Long: `Direct commands to Lightning Engine nodes.

Commands:
  list       - List all nodes in the cluster
  context    - Set/show node context for subsequent commands
  tick       - Tick control (advance, get)
  simulation - Simulation control (play, stop, status)
  metrics    - Get metrics (container, node)
  module     - Module management (list, uninstall, reload)
  proxy      - Proxy settings (enable, disable, status)`,
}

// getEngineClient returns an EngineClient configured based on current settings.
// If use_node_proxy is true, it returns a proxied client that routes through the control plane.
// Otherwise, it returns a direct client using the engine URL.
func getEngineClient() *api.EngineClient {
	if config.GetUseNodeProxy() {
		nodeID := config.GetCurrentNodeID()
		return api.NewProxiedEngineClient(config.GetControlPlaneURL(), nodeID, config.GetAuthToken())
	}
	return api.NewEngineClient(config.GetCurrentEngineURL(), config.GetAuthToken())
}

// =============================================================================
// List Command
// =============================================================================

var nodeListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all nodes in the cluster",
	Long: `List all nodes registered with the control plane.

Examples:
  thunder node list
  thunder node list -o json
  thunder node list -o quiet`,
	RunE: runNodeList,
}

func init() {
	nodeCmd.AddCommand(nodeListCmd)
}

func runNodeList(cmd *cobra.Command, args []string) error {
	nodes, err := apiClient.ListNodes()
	if err != nil {
		out.PrintError(err)
		return err
	}

	rows := make([]output.NodeRow, 0, len(nodes))
	for _, n := range nodes {
		memory := "-"
		var containers, matches int
		var cpuPercent float64

		if n.Metrics != nil {
			containers = n.Metrics.ContainerCount
			matches = n.Metrics.MatchCount
			cpuPercent = n.Metrics.CPUUsage
			if n.Metrics.MemoryMaxMB > 0 {
				memory = fmt.Sprintf("%dMB/%dMB", n.Metrics.MemoryUsedMB, n.Metrics.MemoryMaxMB)
			}
		}

		rows = append(rows, output.NodeRow{
			NodeID:     n.NodeID,
			Status:     n.Status,
			Address:    n.AdvertiseAddress,
			Containers: containers,
			Matches:    matches,
			CPUPercent: cpuPercent,
			Memory:     memory,
		})
	}

	out.PrintNodes(rows)
	return nil
}

// =============================================================================
// Context Commands
// =============================================================================

var nodeContextCmd = &cobra.Command{
	Use:   "context",
	Short: "Context management commands",
	Long: `Set or show the current node context for subsequent commands.

The context stores the node ID and engine URL, which are used by other
node commands. Use 'thunder node context match' to set the match context
for match-specific operations like tick control and simulation.

Commands:
  set   - Set context from node ID (resolves URL from control plane)
  match - Set match context for match-specific operations
  show  - Show current context`,
}

func init() {
	nodeCmd.AddCommand(nodeContextCmd)
}

// context set
var nodeContextSetCmd = &cobra.Command{
	Use:   "set <node-id>",
	Short: "Set the current node context from a node ID",
	Long: `Set the current node context. This resolves the node's advertise address
from the control plane and stores it for use with all 'thunder node' commands.

Use --container-id and --match-id for commands that operate on specific containers.

Examples:
  thunder node context set engine-node-1
  thunder node context set engine-node-1 --container-id 1 --match-id node-1-1-1`,
	Args: cobra.ExactArgs(1),
	RunE: runNodeContextSet,
}

var (
	nodeContextSetContainerID int64
	nodeContextSetMatchID     string
)

func init() {
	nodeContextSetCmd.Flags().Int64Var(&nodeContextSetContainerID, "container-id", 0, "Container ID for container-specific operations")
	nodeContextSetCmd.Flags().StringVar(&nodeContextSetMatchID, "match-id", "", "Match ID for match-specific operations")
	nodeContextCmd.AddCommand(nodeContextSetCmd)
}

func runNodeContextSet(cmd *cobra.Command, args []string) error {
	nodeID := args[0]

	// Get node info from control plane
	node, err := apiClient.GetNode(nodeID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Save context to config
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.CurrentNodeID = nodeID

	// Determine engine URL based on proxy setting
	var engineURL string
	if cfg.UseNodeProxy {
		// Route through control plane proxy
		engineURL = cfg.ControlPlaneURL + "/api/nodes/" + nodeID + "/proxy"
	} else {
		// Direct connection to node
		engineURL = nodeExtractBaseURL(node.AdvertiseAddress)
	}
	cfg.CurrentEngineURL = engineURL

	// Set container context if provided
	if nodeContextSetContainerID > 0 {
		cfg.CurrentContainerID = nodeContextSetContainerID
	} else {
		cfg.CurrentContainerID = 0
	}
	if nodeContextSetMatchID != "" {
		cfg.CurrentMatchID = nodeContextSetMatchID
	} else {
		cfg.CurrentMatchID = ""
	}

	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	msg := fmt.Sprintf("Context set: node %s at %s", nodeID, engineURL)
	if cfg.UseNodeProxy {
		msg += " (via proxy)"
	}
	if nodeContextSetContainerID > 0 {
		msg += fmt.Sprintf(" (container %d", nodeContextSetContainerID)
		if nodeContextSetMatchID != "" {
			msg += fmt.Sprintf(", match %s", nodeContextSetMatchID)
		}
		msg += ")"
	}
	out.PrintSuccess(msg)
	return nil
}

// nodeExtractBaseURL extracts the base URL from a full endpoint URL.
func nodeExtractBaseURL(fullURL string) string {
	// If already a valid URL, return as-is
	if regexp.MustCompile(`^https?://`).MatchString(fullURL) {
		re := regexp.MustCompile(`^(https?://[^/]+)`)
		matches := re.FindStringSubmatch(fullURL)
		if len(matches) > 1 {
			return matches[1]
		}
		return fullURL
	}
	// If just host:port, add http://
	return "http://" + fullURL
}

// context show
var nodeContextShowCmd = &cobra.Command{
	Use:   "show",
	Short: "Show the current node context",
	Long: `Show the currently set node context.

Examples:
  thunder node context show`,
	RunE: runNodeContextShow,
}

func init() {
	nodeContextCmd.AddCommand(nodeContextShowCmd)
}

func runNodeContextShow(cmd *cobra.Command, args []string) error {
	nodeID := config.GetCurrentNodeID()
	engineURL := config.GetCurrentEngineURL()
	matchID := config.GetCurrentMatchID()
	containerID := config.GetCurrentContainerID()
	useProxy := config.GetUseNodeProxy()

	if nodeID == "" && engineURL == "" {
		out.PrintMessage("No context set. Use 'thunder node context set <node-id>' to set one.")
		return nil
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		result := map[string]interface{}{
			"nodeId":       nodeID,
			"engineUrl":    engineURL,
			"useNodeProxy": useProxy,
		}
		if matchID != "" {
			result["matchId"] = matchID
			result["containerId"] = containerID
		}
		out.Print(result)
		return nil
	}

	out.PrintMessage(fmt.Sprintf("Node ID:      %s", nodeID))
	out.PrintMessage(fmt.Sprintf("Engine URL:   %s", engineURL))
	out.PrintMessage(fmt.Sprintf("Use Proxy:    %v", useProxy))
	if matchID != "" {
		out.PrintMessage(fmt.Sprintf("Match ID:     %s", matchID))
		out.PrintMessage(fmt.Sprintf("Container ID: %d", containerID))
	}
	return nil
}

// context match - set match context for match-specific operations
var nodeContextMatchCmd = &cobra.Command{
	Use:   "match <match-id>",
	Short: "Set the match context for match-specific operations",
	Long: `Set the match context for operations that require a specific match/container.

This command looks up the match from the control plane and stores the container ID
for use with commands like 'thunder node tick', 'thunder command', 'thunder snapshot'.

Examples:
  thunder node context match node-1-1-1`,
	Args: cobra.ExactArgs(1),
	RunE: runNodeContextMatch,
}

func init() {
	nodeContextCmd.AddCommand(nodeContextMatchCmd)
}

func runNodeContextMatch(cmd *cobra.Command, args []string) error {
	matchID := args[0]

	// Get deployment info for this match
	deploy, err := apiClient.GetDeployment(matchID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	// Update config with match context
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.CurrentMatchID = matchID
	cfg.CurrentContainerID = deploy.ContainerID

	// If no node context is set, also set the engine URL from the deployment
	if cfg.CurrentEngineURL == "" {
		if cfg.UseNodeProxy {
			// Route through control plane proxy
			cfg.CurrentEngineURL = cfg.ControlPlaneURL + "/api/nodes/" + deploy.NodeID + "/proxy"
		} else {
			// Direct connection to node
			cfg.CurrentEngineURL = nodeExtractBaseURL(deploy.Endpoints.HTTP)
		}
		cfg.CurrentNodeID = deploy.NodeID
	}

	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Match context set: %s (container %d)", matchID, deploy.ContainerID))
	return nil
}

// =============================================================================
// Tick Commands
// =============================================================================

var nodeTickCmd = &cobra.Command{
	Use:   "tick",
	Short: "Tick control commands",
	Long: `Control and query ticks for the current match context.

Commands:
  advance - Advance simulation by one or more ticks
  get     - Get current tick number`,
}

func init() {
	nodeCmd.AddCommand(nodeTickCmd)
}

// tick advance
var nodeTickAdvanceCmd = &cobra.Command{
	Use:   "advance",
	Short: "Advance simulation by one or more ticks",
	Long: `Advance the simulation by one or more ticks.

Examples:
  thunder node tick advance           # Advance by 1 tick
  thunder node tick advance -n 10     # Advance by 10 ticks`,
	RunE: runNodeTickAdvance,
}

var nodeTickAdvanceCount int

func init() {
	nodeTickAdvanceCmd.Flags().IntVarP(&nodeTickAdvanceCount, "count", "n", 1, "Number of ticks to advance")
	nodeTickCmd.AddCommand(nodeTickAdvanceCmd)
}

func runNodeTickAdvance(cmd *cobra.Command, args []string) error {
	containerID := config.GetCurrentContainerID()

	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()

	var lastTick int64
	for i := 0; i < nodeTickAdvanceCount; i++ {
		resp, err := client.AdvanceTick(containerID)
		if err != nil {
			out.PrintError(err)
			return err
		}
		lastTick = resp.Tick
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(map[string]interface{}{
			"tick":     lastTick,
			"advanced": nodeTickAdvanceCount,
		})
		return nil
	}

	if nodeTickAdvanceCount == 1 {
		out.PrintSuccess(fmt.Sprintf("Advanced to tick %d", lastTick))
	} else {
		out.PrintSuccess(fmt.Sprintf("Advanced %d ticks to tick %d", nodeTickAdvanceCount, lastTick))
	}
	return nil
}

// tick get
var nodeTickGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Get current tick number",
	Long: `Get the current tick number for the simulation.

Examples:
  thunder node tick get
  thunder node tick get -o json`,
	RunE: runNodeTickGet,
}

func init() {
	nodeTickCmd.AddCommand(nodeTickGetCmd)
}

func runNodeTickGet(cmd *cobra.Command, args []string) error {
	containerID := config.GetCurrentContainerID()

	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()
	resp, err := client.GetTick(containerID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(resp)
		return nil
	}

	out.PrintMessage(fmt.Sprintf("Current tick: %d", resp.Tick))
	return nil
}

// =============================================================================
// Simulation Commands
// =============================================================================

var nodeSimulationCmd = &cobra.Command{
	Use:     "simulation",
	Aliases: []string{"sim"},
	Short:   "Simulation control commands",
	Long: `Control the simulation loop for the current match context.

Commands:
  play   - Start auto-advancing ticks
  stop   - Stop auto-advancing ticks`,
}

func init() {
	nodeCmd.AddCommand(nodeSimulationCmd)
}

// simulation play
var nodeSimPlayCmd = &cobra.Command{
	Use:   "play",
	Short: "Start auto-advancing ticks",
	Long: `Start auto-advancing ticks at the specified interval.

Examples:
  thunder node simulation play                    # Default 16ms (~60 FPS)
  thunder node simulation play --interval-ms 33   # ~30 FPS
  thunder node sim play -i 8                      # ~120 FPS`,
	RunE: runNodeSimPlay,
}

var nodeSimPlayIntervalMs int64

func init() {
	nodeSimPlayCmd.Flags().Int64VarP(&nodeSimPlayIntervalMs, "interval-ms", "i", 16, "Tick interval in milliseconds")
	nodeSimulationCmd.AddCommand(nodeSimPlayCmd)
}

func runNodeSimPlay(cmd *cobra.Command, args []string) error {
	containerID := config.GetCurrentContainerID()

	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()
	if err := client.StartSimulation(containerID, nodeSimPlayIntervalMs); err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Simulation started at %dms interval (~%d FPS)", nodeSimPlayIntervalMs, 1000/nodeSimPlayIntervalMs))
	return nil
}

// simulation stop
var nodeSimStopCmd = &cobra.Command{
	Use:   "stop",
	Short: "Stop auto-advancing ticks",
	Long: `Stop auto-advancing ticks.

Examples:
  thunder node simulation stop
  thunder node sim stop`,
	RunE: runNodeSimStop,
}

func init() {
	nodeSimulationCmd.AddCommand(nodeSimStopCmd)
}

func runNodeSimStop(cmd *cobra.Command, args []string) error {
	containerID := config.GetCurrentContainerID()

	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()
	if err := client.StopSimulation(containerID); err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess("Simulation stopped")
	return nil
}

// =============================================================================
// Metrics Commands
// =============================================================================

var nodeMetricsCmd = &cobra.Command{
	Use:   "metrics",
	Short: "Metrics commands",
	Long: `Get metrics for containers and nodes.

Commands:
  container - Get metrics for the current container
  get       - Get metrics for the engine node`,
}

func init() {
	nodeCmd.AddCommand(nodeMetricsCmd)
}

// metrics container
var nodeMetricsContainerCmd = &cobra.Command{
	Use:   "container",
	Short: "Get metrics for the current container",
	Long: `Get detailed metrics for the current match container.

Examples:
  thunder node metrics container
  thunder node metrics container -o json`,
	RunE: runNodeMetricsContainer,
}

func init() {
	nodeMetricsCmd.AddCommand(nodeMetricsContainerCmd)
}

func runNodeMetricsContainer(cmd *cobra.Command, args []string) error {
	containerID := config.GetCurrentContainerID()

	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("no context set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()
	metrics, err := client.GetContainerMetrics(containerID)
	if err != nil {
		out.PrintError(err)
		return err
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(metrics)
		return nil
	}

	out.PrintMessage("Tick Stats:")
	out.PrintMessage(fmt.Sprintf("  Current Tick:     %d", metrics.CurrentTick))
	out.PrintMessage(fmt.Sprintf("  Total Ticks:      %d", metrics.TotalTicks))
	out.PrintMessage(fmt.Sprintf("  Tick Time:        %.3f ms (avg: %.3f, min: %.3f, max: %.3f)",
		metrics.LastTickMs, metrics.AvgTickMs, metrics.MinTickMs, metrics.MaxTickMs))

	out.PrintMessage("")
	out.PrintMessage("Entity Stats:")
	out.PrintMessage(fmt.Sprintf("  Total Entities:   %d", metrics.TotalEntities))
	out.PrintMessage(fmt.Sprintf("  Component Types:  %d", metrics.TotalComponentTypes))
	out.PrintMessage(fmt.Sprintf("  Command Queue:    %d", metrics.CommandQueueSize))

	if metrics.SnapshotMetrics != nil {
		out.PrintMessage("")
		out.PrintMessage("Snapshot Stats:")
		out.PrintMessage(fmt.Sprintf("  Cache Hit Rate:   %.1f%%", metrics.SnapshotMetrics.CacheHitRate*100))
		out.PrintMessage(fmt.Sprintf("  Avg Generation:   %.3f ms", metrics.SnapshotMetrics.AvgGenerationMs))
		out.PrintMessage(fmt.Sprintf("  Total Snapshots:  %d", metrics.SnapshotMetrics.TotalGenerations))
	}

	if len(metrics.LastTickSystems) > 0 {
		out.PrintMessage("")
		out.PrintMessage("Last Tick Systems:")
		for _, s := range metrics.LastTickSystems {
			out.PrintMessage(fmt.Sprintf("  %s: %.3f ms", s.SystemName, s.ExecutionTimeMs))
		}
	}

	return nil
}

// metrics get (node metrics)
var nodeMetricsGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Get metrics for the engine node",
	Long: `Get node-level metrics including CPU, memory, and container counts.

Examples:
  thunder node metrics get
  thunder node metrics get -o json
  thunder node metrics get --engine-url http://localhost:8080`,
	RunE: runNodeMetricsGet,
}

var nodeMetricsEngineURL string

func init() {
	nodeMetricsGetCmd.Flags().StringVar(&nodeMetricsEngineURL, "engine-url", "", "Engine URL (overrides match context)")
	nodeMetricsCmd.AddCommand(nodeMetricsGetCmd)
}

func runNodeMetricsGet(cmd *cobra.Command, args []string) error {
	if nodeMetricsEngineURL != "" {
		// Direct URL override - use direct client
		client := api.NewEngineClient(nodeMetricsEngineURL, config.GetAuthToken())
		return runNodeMetricsGetWithClient(client)
	}
	if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("engine URL not set. Use 'thunder node context set <node-id>' first")
	}

	client := getEngineClient()
	return runNodeMetricsGetWithClient(client)
}

func runNodeMetricsGetWithClient(client *api.EngineClient) error {
	metrics, err := client.GetNodeMetrics()
	if err != nil {
		out.PrintError(err)
		return err
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(metrics)
		return nil
	}

	out.PrintMessage("Node Metrics:")
	out.PrintMessage(fmt.Sprintf("  CPU Usage:         %.2f%%", metrics.CPUUsage))
	if metrics.MemoryUsed > 0 {
		out.PrintMessage(fmt.Sprintf("  Memory Used:       %d bytes", metrics.MemoryUsed))
	}
	if metrics.MemoryTotal > 0 {
		out.PrintMessage(fmt.Sprintf("  Memory Total:      %d bytes", metrics.MemoryTotal))
	}
	if metrics.ActiveContainers > 0 {
		out.PrintMessage(fmt.Sprintf("  Active Containers: %d", metrics.ActiveContainers))
	}
	if metrics.ActiveMatches > 0 {
		out.PrintMessage(fmt.Sprintf("  Active Matches:    %d", metrics.ActiveMatches))
	}

	return nil
}

// =============================================================================
// Module Commands (moved from module.go - engine-specific)
// =============================================================================

var nodeModuleCmd = &cobra.Command{
	Use:   "module",
	Short: "Module management commands",
	Long: `Manage modules on the engine node.

Commands:
  list      - List all modules on the engine
  uninstall - Uninstall a module from the engine
  reload    - Reload all modules from disk`,
}

func init() {
	nodeCmd.AddCommand(nodeModuleCmd)
}

// module list
var nodeModuleListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all modules on the engine",
	Long: `List all modules installed on the current engine node.

Examples:
  thunder node module list
  thunder node module list -o json`,
	RunE: runNodeModuleList,
}

var nodeModuleListEngineURL string

func init() {
	nodeModuleListCmd.Flags().StringVar(&nodeModuleListEngineURL, "engine-url", "", "Engine URL (overrides match context)")
	nodeModuleCmd.AddCommand(nodeModuleListCmd)
}

func runNodeModuleList(cmd *cobra.Command, args []string) error {
	var client *api.EngineClient
	if nodeModuleListEngineURL != "" {
		// Direct URL override - use direct client
		client = api.NewEngineClient(nodeModuleListEngineURL, config.GetAuthToken())
	} else if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("engine URL not set. Use 'thunder node context set <node-id>' first")
	} else {
		client = getEngineClient()
	}
	modules, err := client.ListEngineModules()
	if err != nil {
		out.PrintError(err)
		return err
	}

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(modules)
		return nil
	}

	if len(modules) == 0 {
		out.PrintMessage("No modules installed")
		return nil
	}

	out.PrintMessage("NAME\t\t\tFLAG COMPONENT\tENABLED MATCHES")
	for _, m := range modules {
		flagComp := m.FlagComponent
		if flagComp == "" {
			flagComp = "-"
		}
		out.PrintMessage(fmt.Sprintf("%s\t\t%s\t\t%d", m.Name, flagComp, m.EnabledMatches))
	}

	return nil
}

// module uninstall
var nodeModuleUninstallCmd = &cobra.Command{
	Use:   "uninstall <module-name>",
	Short: "Uninstall a module from the engine",
	Long: `Uninstall a module from the current engine node.

The module will be removed from the active modules list.
Use 'thunder node module reload' to reinstall from disk.

Examples:
  thunder node module uninstall RigidBodyModule`,
	Args: cobra.ExactArgs(1),
	RunE: runNodeModuleUninstall,
}

var nodeModuleUninstallEngineURL string

func init() {
	nodeModuleUninstallCmd.Flags().StringVar(&nodeModuleUninstallEngineURL, "engine-url", "", "Engine URL (overrides match context)")
	nodeModuleCmd.AddCommand(nodeModuleUninstallCmd)
}

func runNodeModuleUninstall(cmd *cobra.Command, args []string) error {
	moduleName := args[0]

	var client *api.EngineClient
	if nodeModuleUninstallEngineURL != "" {
		client = api.NewEngineClient(nodeModuleUninstallEngineURL, config.GetAuthToken())
	} else if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("engine URL not set. Use 'thunder node context set <node-id>' first")
	} else {
		client = getEngineClient()
	}
	if err := client.UninstallModule(moduleName); err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Module '%s' uninstalled successfully", moduleName))
	return nil
}

// module reload
var nodeModuleReloadCmd = &cobra.Command{
	Use:   "reload",
	Short: "Reload all modules from disk",
	Long: `Reload all modules from disk on the current engine node.

This will reinstall any modules that were previously uninstalled.

Examples:
  thunder node module reload`,
	RunE: runNodeModuleReload,
}

var nodeModuleReloadEngineURL string

func init() {
	nodeModuleReloadCmd.Flags().StringVar(&nodeModuleReloadEngineURL, "engine-url", "", "Engine URL (overrides match context)")
	nodeModuleCmd.AddCommand(nodeModuleReloadCmd)
}

func runNodeModuleReload(cmd *cobra.Command, args []string) error {
	var client *api.EngineClient
	if nodeModuleReloadEngineURL != "" {
		client = api.NewEngineClient(nodeModuleReloadEngineURL, config.GetAuthToken())
	} else if config.GetCurrentNodeID() == "" {
		return fmt.Errorf("engine URL not set. Use 'thunder node context set <node-id>' first")
	} else {
		client = getEngineClient()
	}
	modules, err := client.ReloadModules()
	if err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Reloaded %d modules", len(modules)))

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		out.Print(modules)
		return nil
	}

	for _, m := range modules {
		out.PrintMessage(fmt.Sprintf("  - %s", m.Name))
	}

	return nil
}

// =============================================================================
// Proxy Commands
// =============================================================================

var nodeProxyCmd = &cobra.Command{
	Use:   "proxy",
	Short: "Node proxy settings",
	Long: `Configure proxy settings for routing requests through the control plane.

When proxy is enabled (default), Thunder CLI routes requests to engine nodes
through the control plane at /api/nodes/{nodeId}/proxy/{path}. This allows
access to nodes on Docker-internal networks that aren't directly reachable.

Commands:
  enable  - Enable proxy mode (route through control plane)
  disable - Disable proxy mode (direct connection to nodes)
  status  - Show current proxy settings`,
}

func init() {
	nodeCmd.AddCommand(nodeProxyCmd)
}

// proxy enable
var nodeProxyEnableCmd = &cobra.Command{
	Use:   "enable",
	Short: "Enable proxy mode",
	Long: `Enable proxy mode to route requests through the control plane.

When enabled, all node requests are routed through the control plane proxy:
  Thunder CLI -> Control Plane -> Node

This is useful when nodes are on Docker-internal networks.

Examples:
  thunder node proxy enable`,
	RunE: runNodeProxyEnable,
}

func init() {
	nodeProxyCmd.AddCommand(nodeProxyEnableCmd)
}

func runNodeProxyEnable(cmd *cobra.Command, args []string) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.UseNodeProxy = true
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	out.PrintSuccess("Proxy mode enabled. Requests will be routed through the control plane.")
	return nil
}

// proxy disable
var nodeProxyDisableCmd = &cobra.Command{
	Use:   "disable",
	Short: "Disable proxy mode",
	Long: `Disable proxy mode to connect directly to nodes.

When disabled, Thunder CLI connects directly to node URLs. This requires
the node's advertise address to be reachable from your machine.

Examples:
  thunder node proxy disable`,
	RunE: runNodeProxyDisable,
}

func init() {
	nodeProxyCmd.AddCommand(nodeProxyDisableCmd)
}

func runNodeProxyDisable(cmd *cobra.Command, args []string) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	cfg.UseNodeProxy = false
	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("failed to save config: %w", err)
	}

	out.PrintSuccess("Proxy mode disabled. Requests will connect directly to nodes.")
	return nil
}

// proxy status
var nodeProxyStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show proxy settings",
	Long: `Show current proxy mode settings.

Examples:
  thunder node proxy status
  thunder node proxy status -o json`,
	RunE: runNodeProxyStatus,
}

func init() {
	nodeProxyCmd.AddCommand(nodeProxyStatusCmd)
}

func runNodeProxyStatus(cmd *cobra.Command, args []string) error {
	useProxy := config.GetUseNodeProxy()
	nodeID := config.GetCurrentNodeID()
	controlPlaneURL := config.GetControlPlaneURL()

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		result := map[string]interface{}{
			"proxyEnabled":    useProxy,
			"nodeId":          nodeID,
			"controlPlaneUrl": controlPlaneURL,
		}
		out.Print(result)
		return nil
	}

	if useProxy {
		out.PrintMessage("Proxy mode:       ENABLED")
		out.PrintMessage(fmt.Sprintf("Control Plane:    %s", controlPlaneURL))
		if nodeID != "" {
			out.PrintMessage(fmt.Sprintf("Proxy URL:        %s/api/nodes/%s/proxy/...", controlPlaneURL, nodeID))
		}
	} else {
		out.PrintMessage("Proxy mode:       DISABLED")
		engineURL := config.GetCurrentEngineURL()
		if engineURL != "" {
			out.PrintMessage(fmt.Sprintf("Direct URL:       %s", engineURL))
		}
	}

	return nil
}
