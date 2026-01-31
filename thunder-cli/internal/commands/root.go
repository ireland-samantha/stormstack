// Package commands implements CLI commands for the Thunder CLI.
package commands

import (
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/api"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/config"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	// Global flags
	outputFormat string
	controlPlane string

	// Shared instances
	apiClient *api.Client
	out       *output.Writer
)

// RootCmd is the root command for the CLI.
var RootCmd = &cobra.Command{
	Use:   "thunder",
	Short: "Thunder CLI for Lightning Engine",
	Long: `Thunder is a command-line interface for the Lightning Engine Control Plane.

Deploy games, manage clusters, and monitor matches from the command line.

Configuration:
  The CLI reads configuration from ~/.thunder.yaml or environment variables.
  Use 'thunder config set' to configure settings.

Environment variables:
  THUNDER_CONTROL_PLANE_URL  Control Plane URL (default: http://localhost:8081)
  THUNDER_AUTH_TOKEN         Authentication token
  THUNDER_OUTPUT_FORMAT      Output format (table, json, yaml, quiet)`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Load config
		if _, err := config.Load(); err != nil {
			return err
		}

		// Override with flags if provided
		if controlPlane != "" {
			config.SetControlPlaneURL(controlPlane)
		}
		if outputFormat != "" {
			config.SetOutputFormat(outputFormat)
		}

		// Initialize shared instances
		apiClient = api.NewClient(config.GetControlPlaneURL(), config.GetAuthToken())
		out = output.NewWriter(config.GetOutputFormat())

		return nil
	},
}

func init() {
	RootCmd.PersistentFlags().StringVarP(&outputFormat, "output", "o", "", "Output format (table, json, yaml, quiet)")
	RootCmd.PersistentFlags().StringVar(&controlPlane, "control-plane", "", "Control Plane URL")

	// Add subcommands
	RootCmd.AddCommand(authCmd)
	RootCmd.AddCommand(deployCmd)
	RootCmd.AddCommand(clusterCmd)
	RootCmd.AddCommand(matchCmd)
	RootCmd.AddCommand(moduleCmd)
	RootCmd.AddCommand(configCmd)
	RootCmd.AddCommand(versionCmd)
	RootCmd.AddCommand(commandCmd)
	RootCmd.AddCommand(snapshotCmd)
	RootCmd.AddCommand(nodeCmd)
}
