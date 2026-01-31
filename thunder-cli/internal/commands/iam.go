// Package commands implements CLI commands for the Thunder CLI.
package commands

import (
	"fmt"
	"strings"
	"syscall"

	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/api"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/config"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var iamClient *api.IAMClient

// iamCmd is the root IAM command
var iamCmd = &cobra.Command{
	Use:   "iam",
	Short: "Identity and Access Management",
	Long: `Manage users, roles, and API tokens in the Lightning Auth service.

Commands:
  user   - Manage users
  role   - Manage roles and scopes
  token  - Manage API tokens`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Call parent's PersistentPreRunE
		if err := RootCmd.PersistentPreRunE(cmd, args); err != nil {
			return err
		}
		// Initialize IAM client pointing to auth service
		authURL := config.GetAuthURL()
		if authURL == "" {
			authURL = "http://localhost:8082"
		}
		iamClient = api.NewIAMClient(authURL, config.GetAuthToken())
		return nil
	},
}

var iamAuthURL string

func init() {
	iamCmd.PersistentFlags().StringVar(&iamAuthURL, "auth-url", "", "Auth service URL (default: from config or http://localhost:8082)")
	RootCmd.AddCommand(iamCmd)

	// Add subcommands
	iamCmd.AddCommand(userCmd)
	iamCmd.AddCommand(roleCmd)
	iamCmd.AddCommand(tokenCmd)
}

// ============================================================================
// User Commands
// ============================================================================

var userCmd = &cobra.Command{
	Use:   "user",
	Short: "Manage users",
	Long: `Manage users in the Lightning Auth service.

Commands:
  list   - List all users
  get    - Get a user by ID
  create - Create a new user
  update - Update a user
  delete - Delete a user`,
}

var userListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all users",
	RunE:  runUserList,
}

func runUserList(cmd *cobra.Command, args []string) error {
	users, err := iamClient.ListUsers()
	if err != nil {
		return fmt.Errorf("failed to list users: %w", err)
	}

	if out.IsQuiet() {
		for _, u := range users {
			fmt.Println(u.ID)
		}
		return nil
	}

	if out.IsJSON() || out.IsYAML() {
		return out.Print(users)
	}

	// Table format
	headers := []string{"ID", "USERNAME", "ENABLED", "ROLES", "SCOPES"}
	var rows [][]string
	for _, u := range users {
		roles := strings.Join(u.RoleIDs, ", ")
		if len(roles) > 30 {
			roles = roles[:27] + "..."
		}
		scopes := strings.Join(u.Scopes, ", ")
		if len(scopes) > 30 {
			scopes = scopes[:27] + "..."
		}
		rows = append(rows, []string{
			u.ID,
			u.Username,
			fmt.Sprintf("%v", u.Enabled),
			roles,
			scopes,
		})
	}
	out.PrintTable(headers, rows)
	return nil
}

var userGetCmd = &cobra.Command{
	Use:   "get <user-id>",
	Short: "Get a user by ID",
	Args:  cobra.ExactArgs(1),
	RunE:  runUserGet,
}

func runUserGet(cmd *cobra.Command, args []string) error {
	user, err := iamClient.GetUser(args[0])
	if err != nil {
		return fmt.Errorf("failed to get user: %w", err)
	}
	return out.Print(user)
}

var (
	userCreateRoles  []string
	userCreateScopes []string
)

var userCreateCmd = &cobra.Command{
	Use:   "create <username>",
	Short: "Create a new user",
	Long: `Create a new user with the specified username.
You will be prompted for a password.

Examples:
  thunder iam user create alice
  thunder iam user create bob --role admin --scope auth.user.read`,
	Args: cobra.ExactArgs(1),
	RunE: runUserCreate,
}

func runUserCreate(cmd *cobra.Command, args []string) error {
	username := args[0]

	// Prompt for password
	fmt.Print("Password: ")
	passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		return fmt.Errorf("failed to read password: %w", err)
	}
	fmt.Println()

	fmt.Print("Confirm password: ")
	confirmBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		return fmt.Errorf("failed to read password confirmation: %w", err)
	}
	fmt.Println()

	if string(passwordBytes) != string(confirmBytes) {
		return fmt.Errorf("passwords do not match")
	}

	req := &api.CreateUserRequest{
		Username: username,
		Password: string(passwordBytes),
		RoleIDs:  userCreateRoles,
		Scopes:   userCreateScopes,
	}

	user, err := iamClient.CreateUser(req)
	if err != nil {
		return fmt.Errorf("failed to create user: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Created user: %s (ID: %s)", user.Username, user.ID))
	return out.Print(user)
}

var (
	userUpdatePassword string
	userUpdateRoles    []string
	userUpdateScopes   []string
	userUpdateEnabled  string
)

var userUpdateCmd = &cobra.Command{
	Use:   "update <user-id>",
	Short: "Update a user",
	Long: `Update a user's properties.

Examples:
  thunder iam user update <id> --password
  thunder iam user update <id> --role admin --role viewer
  thunder iam user update <id> --enabled=false`,
	Args: cobra.ExactArgs(1),
	RunE: runUserUpdate,
}

func runUserUpdate(cmd *cobra.Command, args []string) error {
	req := &api.UpdateUserRequest{}

	if cmd.Flags().Changed("password") {
		fmt.Print("New password: ")
		passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
		if err != nil {
			return fmt.Errorf("failed to read password: %w", err)
		}
		fmt.Println()
		pw := string(passwordBytes)
		req.Password = &pw
	}

	if cmd.Flags().Changed("role") {
		req.RoleIDs = userUpdateRoles
	}

	if cmd.Flags().Changed("scope") {
		req.Scopes = userUpdateScopes
	}

	if cmd.Flags().Changed("enabled") {
		enabled := userUpdateEnabled == "true"
		req.Enabled = &enabled
	}

	user, err := iamClient.UpdateUser(args[0], req)
	if err != nil {
		return fmt.Errorf("failed to update user: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Updated user: %s", user.Username))
	return out.Print(user)
}

var userDeleteCmd = &cobra.Command{
	Use:   "delete <user-id>",
	Short: "Delete a user",
	Args:  cobra.ExactArgs(1),
	RunE:  runUserDelete,
}

func runUserDelete(cmd *cobra.Command, args []string) error {
	if err := iamClient.DeleteUser(args[0]); err != nil {
		return fmt.Errorf("failed to delete user: %w", err)
	}
	out.PrintSuccess(fmt.Sprintf("Deleted user: %s", args[0]))
	return nil
}

func init() {
	userCmd.AddCommand(userListCmd)
	userCmd.AddCommand(userGetCmd)

	userCreateCmd.Flags().StringArrayVar(&userCreateRoles, "role", nil, "Role ID to assign (can be repeated)")
	userCreateCmd.Flags().StringArrayVar(&userCreateScopes, "scope", nil, "Scope to assign (can be repeated)")
	userCmd.AddCommand(userCreateCmd)

	userUpdateCmd.Flags().StringVar(&userUpdatePassword, "password", "", "Set new password (prompts for input)")
	userUpdateCmd.Flags().StringArrayVar(&userUpdateRoles, "role", nil, "Role ID to assign (can be repeated, replaces all)")
	userUpdateCmd.Flags().StringArrayVar(&userUpdateScopes, "scope", nil, "Scope to assign (can be repeated, replaces all)")
	userUpdateCmd.Flags().StringVar(&userUpdateEnabled, "enabled", "", "Enable or disable user (true/false)")
	userCmd.AddCommand(userUpdateCmd)

	userCmd.AddCommand(userDeleteCmd)
}

// ============================================================================
// Role Commands
// ============================================================================

var roleCmd = &cobra.Command{
	Use:   "role",
	Short: "Manage roles",
	Long: `Manage roles and scopes in the Lightning Auth service.

Commands:
  list    - List all roles
  get     - Get a role by ID
  create  - Create a new role
  update  - Update a role
  delete  - Delete a role
  scope   - Manage role scopes`,
}

var roleListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all roles",
	RunE:  runRoleList,
}

func runRoleList(cmd *cobra.Command, args []string) error {
	roles, err := iamClient.ListRoles()
	if err != nil {
		return fmt.Errorf("failed to list roles: %w", err)
	}

	if out.IsQuiet() {
		for _, r := range roles {
			fmt.Println(r.ID)
		}
		return nil
	}

	if out.IsJSON() || out.IsYAML() {
		return out.Print(roles)
	}

	// Table format
	headers := []string{"ID", "NAME", "DESCRIPTION", "SCOPES"}
	var rows [][]string
	for _, r := range roles {
		scopes := strings.Join(r.Scopes, ", ")
		if len(scopes) > 40 {
			scopes = scopes[:37] + "..."
		}
		desc := r.Description
		if len(desc) > 30 {
			desc = desc[:27] + "..."
		}
		rows = append(rows, []string{
			r.ID,
			r.Name,
			desc,
			scopes,
		})
	}
	out.PrintTable(headers, rows)
	return nil
}

var roleGetCmd = &cobra.Command{
	Use:   "get <role-id>",
	Short: "Get a role by ID",
	Args:  cobra.ExactArgs(1),
	RunE:  runRoleGet,
}

func runRoleGet(cmd *cobra.Command, args []string) error {
	role, err := iamClient.GetRole(args[0])
	if err != nil {
		return fmt.Errorf("failed to get role: %w", err)
	}
	return out.Print(role)
}

var (
	roleCreateDescription string
	roleCreateIncludes    []string
	roleCreateScopes      []string
)

var roleCreateCmd = &cobra.Command{
	Use:   "create <name>",
	Short: "Create a new role",
	Long: `Create a new role with the specified name.

Examples:
  thunder iam role create editor --description "Can edit content"
  thunder iam role create admin --include viewer --scope auth.user.read`,
	Args: cobra.ExactArgs(1),
	RunE: runRoleCreate,
}

func runRoleCreate(cmd *cobra.Command, args []string) error {
	req := &api.CreateRoleRequest{
		Name:            args[0],
		Description:     roleCreateDescription,
		IncludedRoleIDs: roleCreateIncludes,
		Scopes:          roleCreateScopes,
	}

	role, err := iamClient.CreateRole(req)
	if err != nil {
		return fmt.Errorf("failed to create role: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Created role: %s (ID: %s)", role.Name, role.ID))
	return out.Print(role)
}

var (
	roleUpdateName        string
	roleUpdateDescription string
	roleUpdateIncludes    []string
	roleUpdateScopes      []string
)

var roleUpdateCmd = &cobra.Command{
	Use:   "update <role-id>",
	Short: "Update a role",
	Long: `Update a role's properties.

Examples:
  thunder iam role update <id> --description "New description"
  thunder iam role update <id> --scope auth.user.read --scope auth.role.read`,
	Args: cobra.ExactArgs(1),
	RunE: runRoleUpdate,
}

func runRoleUpdate(cmd *cobra.Command, args []string) error {
	req := &api.UpdateRoleRequest{}

	if cmd.Flags().Changed("name") {
		req.Name = roleUpdateName
	}
	if cmd.Flags().Changed("description") {
		req.Description = roleUpdateDescription
	}
	if cmd.Flags().Changed("include") {
		req.IncludedRoleIDs = roleUpdateIncludes
	}
	if cmd.Flags().Changed("scope") {
		req.Scopes = roleUpdateScopes
	}

	role, err := iamClient.UpdateRole(args[0], req)
	if err != nil {
		return fmt.Errorf("failed to update role: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Updated role: %s", role.Name))
	return out.Print(role)
}

var roleDeleteCmd = &cobra.Command{
	Use:   "delete <role-id>",
	Short: "Delete a role",
	Args:  cobra.ExactArgs(1),
	RunE:  runRoleDelete,
}

func runRoleDelete(cmd *cobra.Command, args []string) error {
	if err := iamClient.DeleteRole(args[0]); err != nil {
		return fmt.Errorf("failed to delete role: %w", err)
	}
	out.PrintSuccess(fmt.Sprintf("Deleted role: %s", args[0]))
	return nil
}

// Scope management subcommands
var roleScopeCmd = &cobra.Command{
	Use:   "scope",
	Short: "Manage role scopes",
	Long: `Manage scopes assigned to a role.

Commands:
  list     - List scopes for a role
  add      - Add a scope to a role
  remove   - Remove a scope from a role
  resolved - Show all resolved scopes (including inherited)`,
}

var roleScopeListCmd = &cobra.Command{
	Use:   "list <role-id>",
	Short: "List scopes for a role",
	Args:  cobra.ExactArgs(1),
	RunE:  runRoleScopeList,
}

func runRoleScopeList(cmd *cobra.Command, args []string) error {
	role, err := iamClient.GetRole(args[0])
	if err != nil {
		return fmt.Errorf("failed to get role: %w", err)
	}

	if out.IsJSON() || out.IsYAML() {
		return out.Print(role.Scopes)
	}

	out.PrintMessage(fmt.Sprintf("Scopes for role %s:", role.Name))
	for _, s := range role.Scopes {
		fmt.Printf("  - %s\n", s)
	}
	return nil
}

var roleScopeAddCmd = &cobra.Command{
	Use:   "add <role-id> <scope>",
	Short: "Add a scope to a role",
	Args:  cobra.ExactArgs(2),
	RunE:  runRoleScopeAdd,
}

func runRoleScopeAdd(cmd *cobra.Command, args []string) error {
	role, err := iamClient.AddRoleScope(args[0], args[1])
	if err != nil {
		return fmt.Errorf("failed to add scope: %w", err)
	}
	out.PrintSuccess(fmt.Sprintf("Added scope %s to role %s", args[1], role.Name))
	return nil
}

var roleScopeRemoveCmd = &cobra.Command{
	Use:   "remove <role-id> <scope>",
	Short: "Remove a scope from a role",
	Args:  cobra.ExactArgs(2),
	RunE:  runRoleScopeRemove,
}

func runRoleScopeRemove(cmd *cobra.Command, args []string) error {
	role, err := iamClient.RemoveRoleScope(args[0], args[1])
	if err != nil {
		return fmt.Errorf("failed to remove scope: %w", err)
	}
	out.PrintSuccess(fmt.Sprintf("Removed scope %s from role %s", args[1], role.Name))
	return nil
}

var roleScopeResolvedCmd = &cobra.Command{
	Use:   "resolved <role-id>",
	Short: "Show all resolved scopes (including inherited)",
	Args:  cobra.ExactArgs(1),
	RunE:  runRoleScopeResolved,
}

func runRoleScopeResolved(cmd *cobra.Command, args []string) error {
	scopes, err := iamClient.GetResolvedScopes(args[0])
	if err != nil {
		return fmt.Errorf("failed to get resolved scopes: %w", err)
	}

	if out.IsJSON() || out.IsYAML() {
		return out.Print(scopes)
	}

	out.PrintMessage("Resolved scopes (including inherited):")
	for _, s := range scopes {
		fmt.Printf("  - %s\n", s)
	}
	return nil
}

func init() {
	roleCmd.AddCommand(roleListCmd)
	roleCmd.AddCommand(roleGetCmd)

	roleCreateCmd.Flags().StringVar(&roleCreateDescription, "description", "", "Role description")
	roleCreateCmd.Flags().StringArrayVar(&roleCreateIncludes, "include", nil, "Role ID to include (can be repeated)")
	roleCreateCmd.Flags().StringArrayVar(&roleCreateScopes, "scope", nil, "Scope to assign (can be repeated)")
	roleCmd.AddCommand(roleCreateCmd)

	roleUpdateCmd.Flags().StringVar(&roleUpdateName, "name", "", "New role name")
	roleUpdateCmd.Flags().StringVar(&roleUpdateDescription, "description", "", "New description")
	roleUpdateCmd.Flags().StringArrayVar(&roleUpdateIncludes, "include", nil, "Role IDs to include (replaces all)")
	roleUpdateCmd.Flags().StringArrayVar(&roleUpdateScopes, "scope", nil, "Scopes to assign (replaces all)")
	roleCmd.AddCommand(roleUpdateCmd)

	roleCmd.AddCommand(roleDeleteCmd)

	roleScopeCmd.AddCommand(roleScopeListCmd)
	roleScopeCmd.AddCommand(roleScopeAddCmd)
	roleScopeCmd.AddCommand(roleScopeRemoveCmd)
	roleScopeCmd.AddCommand(roleScopeResolvedCmd)
	roleCmd.AddCommand(roleScopeCmd)
}

// ============================================================================
// API Token Commands
// ============================================================================

var tokenCmd = &cobra.Command{
	Use:   "token",
	Short: "Manage API tokens",
	Long: `Manage API tokens in the Lightning Auth service.

Commands:
  list   - List all API tokens
  get    - Get a token by ID
  create - Create a new API token
  revoke - Revoke an API token`,
}

var tokenListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all API tokens",
	RunE:  runTokenList,
}

func runTokenList(cmd *cobra.Command, args []string) error {
	tokens, err := iamClient.ListApiTokens()
	if err != nil {
		return fmt.Errorf("failed to list tokens: %w", err)
	}

	if out.IsQuiet() {
		for _, t := range tokens {
			fmt.Println(t.ID)
		}
		return nil
	}

	if out.IsJSON() || out.IsYAML() {
		return out.Print(tokens)
	}

	// Table format
	headers := []string{"ID", "NAME", "USER_ID", "REVOKED", "EXPIRES_AT"}
	var rows [][]string
	for _, t := range tokens {
		expires := t.ExpiresAt
		if expires == "" {
			expires = "never"
		}
		rows = append(rows, []string{
			t.ID,
			t.Name,
			t.UserID,
			fmt.Sprintf("%v", t.Revoked),
			expires,
		})
	}
	out.PrintTable(headers, rows)
	return nil
}

var tokenGetCmd = &cobra.Command{
	Use:   "get <token-id>",
	Short: "Get a token by ID",
	Args:  cobra.ExactArgs(1),
	RunE:  runTokenGet,
}

func runTokenGet(cmd *cobra.Command, args []string) error {
	token, err := iamClient.GetApiToken(args[0])
	if err != nil {
		return fmt.Errorf("failed to get token: %w", err)
	}
	return out.Print(token)
}

var (
	tokenCreateScopes    []string
	tokenCreateExpiresAt string
)

var tokenCreateCmd = &cobra.Command{
	Use:   "create <name>",
	Short: "Create a new API token",
	Long: `Create a new API token with the specified name.

The plaintext token is only shown once in the response.
Make sure to save it securely!

Examples:
  thunder iam token create ci-token
  thunder iam token create admin-token --scope auth.user.read --scope auth.role.read
  thunder iam token create temp-token --expires 2024-12-31T23:59:59Z`,
	Args: cobra.ExactArgs(1),
	RunE: runTokenCreate,
}

func runTokenCreate(cmd *cobra.Command, args []string) error {
	req := &api.CreateApiTokenRequest{
		Name:      args[0],
		Scopes:    tokenCreateScopes,
		ExpiresAt: tokenCreateExpiresAt,
	}

	resp, err := iamClient.CreateApiToken(req)
	if err != nil {
		return fmt.Errorf("failed to create token: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Created API token: %s (ID: %s)", resp.Name, resp.ID))
	out.PrintMessage("")
	out.PrintMessage("IMPORTANT: Save this token securely - it will not be shown again!")
	out.PrintMessage("")
	fmt.Printf("Token: %s\n", resp.PlaintextToken)
	return nil
}

var tokenRevokeCmd = &cobra.Command{
	Use:   "revoke <token-id>",
	Short: "Revoke an API token",
	Args:  cobra.ExactArgs(1),
	RunE:  runTokenRevoke,
}

func runTokenRevoke(cmd *cobra.Command, args []string) error {
	if err := iamClient.RevokeApiToken(args[0]); err != nil {
		return fmt.Errorf("failed to revoke token: %w", err)
	}
	out.PrintSuccess(fmt.Sprintf("Revoked token: %s", args[0]))
	return nil
}

func init() {
	tokenCmd.AddCommand(tokenListCmd)
	tokenCmd.AddCommand(tokenGetCmd)

	tokenCreateCmd.Flags().StringArrayVar(&tokenCreateScopes, "scope", nil, "Scope for the token (can be repeated)")
	tokenCreateCmd.Flags().StringVar(&tokenCreateExpiresAt, "expires", "", "Expiration time (ISO8601 format)")
	tokenCmd.AddCommand(tokenCreateCmd)

	tokenCmd.AddCommand(tokenRevokeCmd)
}
