package commands

import (
	"fmt"

	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/output"
	"github.com/spf13/cobra"
)

// moduleCmd is for control plane module registry operations
var moduleCmd = &cobra.Command{
	Use:   "module",
	Short: "Module registry commands (control plane)",
	Long: `Manage modules in the Lightning Control Plane registry.

For engine node module operations, use 'thunder node module'.

Commands:
  list       - List all modules in the registry
  versions   - List versions of a module
  upload     - Upload a new module to the registry
  distribute - Distribute a module to all nodes`,
}

// List subcommand
var moduleListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all modules in the registry",
	Long: `List all modules registered with the control plane.

Examples:
  thunder module list
  thunder module list -o json`,
	RunE: runModuleList,
}

func init() {
	moduleCmd.AddCommand(moduleListCmd)
}

func runModuleList(cmd *cobra.Command, args []string) error {
	modules, err := apiClient.ListModules()
	if err != nil {
		out.PrintError(err)
		return err
	}

	rows := make([]output.ModuleRow, 0, len(modules))
	for _, m := range modules {
		rows = append(rows, output.ModuleRow{
			Name:        m.Name,
			Version:     m.Version,
			Description: m.Description,
		})
	}

	out.PrintModules(rows)
	return nil
}

// Versions subcommand
var moduleVersionsCmd = &cobra.Command{
	Use:   "versions <module-name>",
	Short: "List all versions of a module",
	Long: `List all available versions of a specific module.

Examples:
  thunder module versions EntityModule
  thunder module versions EntityModule -o json`,
	Args: cobra.ExactArgs(1),
	RunE: runModuleVersions,
}

func init() {
	moduleCmd.AddCommand(moduleVersionsCmd)
}

func runModuleVersions(cmd *cobra.Command, args []string) error {
	moduleName := args[0]

	module, err := apiClient.GetModuleVersions(moduleName)
	if err != nil {
		out.PrintError(err)
		return err
	}

	rows := make([]output.ModuleRow, 0, len(module.Versions))
	for _, v := range module.Versions {
		rows = append(rows, output.ModuleRow{
			Name:        module.Name,
			Version:     v,
			Description: module.Description,
		})
	}

	out.PrintModules(rows)
	return nil
}

// Upload subcommand
var moduleUploadCmd = &cobra.Command{
	Use:   "upload <module-name> <version> <jar-file>",
	Short: "Upload a new module to the registry",
	Long: `Upload a new module JAR file to the control plane registry.

Examples:
  thunder module upload EntityModule 1.0.0 ./entity-module.jar
  thunder module upload RigidBodyModule 1.0.0 ./rigid-body-module-1.0.0.jar --description "Physics simulation"`,
	Args: cobra.ExactArgs(3),
	RunE: runModuleUpload,
}

var uploadDescription string

func init() {
	moduleUploadCmd.Flags().StringVarP(&uploadDescription, "description", "d", "", "Module description")
	moduleCmd.AddCommand(moduleUploadCmd)
}

func runModuleUpload(cmd *cobra.Command, args []string) error {
	moduleName := args[0]
	version := args[1]
	filePath := args[2]

	out.PrintMessage(fmt.Sprintf("Uploading %s@%s from %s...", moduleName, version, filePath))

	module, err := apiClient.UploadModule(moduleName, version, uploadDescription, filePath)
	if err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Module %s@%s uploaded successfully", module.Name, module.Version))
	return nil
}

// Distribute subcommand
var moduleDistributeCmd = &cobra.Command{
	Use:   "distribute <module-name> <version>",
	Short: "Distribute a module to all nodes",
	Long: `Distribute a module version to all healthy nodes in the cluster.

Examples:
  thunder module distribute EntityModule 1.0.0`,
	Args: cobra.ExactArgs(2),
	RunE: runModuleDistribute,
}

func init() {
	moduleCmd.AddCommand(moduleDistributeCmd)
}

func runModuleDistribute(cmd *cobra.Command, args []string) error {
	moduleName := args[0]
	version := args[1]

	result, err := apiClient.DistributeModule(moduleName, version)
	if err != nil {
		out.PrintError(err)
		return err
	}

	out.PrintSuccess(fmt.Sprintf("Module %s@%s distributed to %d nodes", result.ModuleName, result.ModuleVersion, result.NodesUpdated))
	return nil
}
