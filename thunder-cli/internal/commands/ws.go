package commands

import (
	"encoding/base64"
	"fmt"
	"strings"
	"time"

	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/config"
	"github.com/ireland-samantha/lightning-engine/thunder-cli/internal/ws"
	"github.com/spf13/cobra"
)

var (
	// Global WebSocket client (persists across commands)
	wsClient *ws.Client

	// Command flags
	wsTimeout     time.Duration
	wsMessageType string
	wsCount       int
)

var wsCmd = &cobra.Command{
	Use:   "ws",
	Short: "WebSocket client commands",
	Long: `Manage WebSocket connections to Lightning Engine.

Before using ws commands, you must join a match using 'thunder match join'.
This stores the match token and WebSocket URLs needed for connection.

Commands:
  connect    - Connect to command or snapshot WebSocket
  send       - Send a message to the connected WebSocket
  receive    - Receive messages from the connected WebSocket
  disconnect - Disconnect from the WebSocket
  status     - Show current WebSocket connection status`,
}

func init() {
	RootCmd.AddCommand(wsCmd)
}

// =============================================================================
// Connect Command
// =============================================================================

var wsConnectCmd = &cobra.Command{
	Use:   "connect <command|snapshot>",
	Short: "Connect to a WebSocket endpoint",
	Long: `Connect to the command or snapshot WebSocket endpoint.

The WebSocket URL and match token are taken from the current match context,
which is set when you join a match using 'thunder match join'.

Examples:
  thunder ws connect command    # Connect to command WebSocket
  thunder ws connect snapshot   # Connect to snapshot WebSocket`,
	Args:      cobra.ExactArgs(1),
	ValidArgs: []string{"command", "snapshot"},
	RunE:      runWsConnect,
}

func init() {
	wsCmd.AddCommand(wsConnectCmd)
}

func runWsConnect(cmd *cobra.Command, args []string) error {
	wsType := strings.ToLower(args[0])
	if wsType != "command" && wsType != "snapshot" {
		return fmt.Errorf("invalid WebSocket type: %s (must be 'command' or 'snapshot')", wsType)
	}

	// Check if already connected
	if wsClient != nil && wsClient.IsConnected() {
		return fmt.Errorf("already connected to %s. Use 'thunder ws disconnect' first", wsClient.URL())
	}

	// Get WebSocket URL and token from config
	var wsURL string
	if wsType == "command" {
		wsURL = config.GetCurrentCommandWsURL()
	} else {
		wsURL = config.GetCurrentSnapshotWsURL()
	}

	if wsURL == "" {
		return fmt.Errorf("no %s WebSocket URL configured. Join a match first with 'thunder match join'", wsType)
	}

	token := config.GetCurrentMatchToken()
	if token == "" {
		return fmt.Errorf("no match token configured. Join a match first with 'thunder match join'")
	}

	// Create and connect the client
	wsClient = ws.NewClient(wsURL, token)
	if err := wsClient.Connect(); err != nil {
		wsClient = nil
		return fmt.Errorf("failed to connect: %w", err)
	}

	out.PrintSuccess(fmt.Sprintf("Connected to %s WebSocket: %s", wsType, wsURL))
	return nil
}

// =============================================================================
// Send Command
// =============================================================================

var wsSendCmd = &cobra.Command{
	Use:   "send <message>",
	Short: "Send a message to the connected WebSocket",
	Long: `Send a message to the currently connected WebSocket.

By default, sends the message as text. Use --binary to send as binary data
(message will be base64-decoded before sending).

Examples:
  thunder ws send '{"command":"move","x":10,"y":20}'
  thunder ws send --binary "SGVsbG8gV29ybGQ="`,
	Args: cobra.ExactArgs(1),
	RunE: runWsSend,
}

func init() {
	wsSendCmd.Flags().StringVarP(&wsMessageType, "type", "t", "text", "Message type: text or binary")
	wsCmd.AddCommand(wsSendCmd)
}

func runWsSend(cmd *cobra.Command, args []string) error {
	if wsClient == nil || !wsClient.IsConnected() {
		return fmt.Errorf("not connected. Use 'thunder ws connect' first")
	}

	message := args[0]

	if wsMessageType == "binary" {
		// Decode base64 and send as binary
		data, err := base64.StdEncoding.DecodeString(message)
		if err != nil {
			return fmt.Errorf("invalid base64: %w", err)
		}
		if err := wsClient.SendBinary(data); err != nil {
			return fmt.Errorf("failed to send: %w", err)
		}
		out.PrintSuccess(fmt.Sprintf("Sent %d bytes (binary)", len(data)))
	} else {
		if err := wsClient.Send(message); err != nil {
			return fmt.Errorf("failed to send: %w", err)
		}
		out.PrintSuccess(fmt.Sprintf("Sent: %s", message))
	}

	return nil
}

// =============================================================================
// Receive Command
// =============================================================================

var wsReceiveCmd = &cobra.Command{
	Use:   "receive",
	Short: "Receive messages from the connected WebSocket",
	Long: `Receive messages from the currently connected WebSocket.

By default, waits for a single message with a 5 second timeout.
Use --count to receive multiple messages and --timeout to change the wait time.

Examples:
  thunder ws receive                     # Receive one message
  thunder ws receive --timeout 10s       # Wait up to 10 seconds
  thunder ws receive --count 5           # Receive 5 messages
  thunder ws receive -c 0                # Receive continuously (Ctrl+C to stop)`,
	RunE: runWsReceive,
}

func init() {
	wsReceiveCmd.Flags().DurationVarP(&wsTimeout, "timeout", "T", 5*time.Second, "Timeout for receiving a message")
	wsReceiveCmd.Flags().IntVarP(&wsCount, "count", "c", 1, "Number of messages to receive (0 for continuous)")
	wsCmd.AddCommand(wsReceiveCmd)
}

func runWsReceive(cmd *cobra.Command, args []string) error {
	if wsClient == nil || !wsClient.IsConnected() {
		return fmt.Errorf("not connected. Use 'thunder ws connect' first")
	}

	if wsCount == 0 {
		// Continuous mode
		out.PrintMessage("Receiving messages (Ctrl+C to stop)...")
		for {
			message, isBinary, err := wsClient.Receive(wsTimeout)
			if err != nil {
				// On timeout, just try again
				if strings.Contains(err.Error(), "timeout") {
					continue
				}
				return fmt.Errorf("receive error: %w", err)
			}
			printReceivedMessage(message, isBinary)
		}
	} else {
		// Fixed count mode
		for i := 0; i < wsCount; i++ {
			message, isBinary, err := wsClient.Receive(wsTimeout)
			if err != nil {
				return fmt.Errorf("receive error: %w", err)
			}
			printReceivedMessage(message, isBinary)
		}
	}

	return nil
}

func printReceivedMessage(message string, isBinary bool) {
	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		data := map[string]interface{}{
			"message":  message,
			"isBinary": isBinary,
			"time":     time.Now().Format(time.RFC3339),
		}
		out.Print(data)
	} else {
		if isBinary {
			out.PrintMessage(fmt.Sprintf("[binary] %s", base64.StdEncoding.EncodeToString([]byte(message))))
		} else {
			out.PrintMessage(message)
		}
	}
}

// =============================================================================
// Disconnect Command
// =============================================================================

var wsDisconnectCmd = &cobra.Command{
	Use:   "disconnect",
	Short: "Disconnect from the WebSocket",
	Long: `Disconnect from the currently connected WebSocket.

Examples:
  thunder ws disconnect`,
	RunE: runWsDisconnect,
}

func init() {
	wsCmd.AddCommand(wsDisconnectCmd)
}

func runWsDisconnect(cmd *cobra.Command, args []string) error {
	if wsClient == nil || !wsClient.IsConnected() {
		out.PrintMessage("Not connected")
		return nil
	}

	url := wsClient.URL()
	if err := wsClient.Close(); err != nil {
		return fmt.Errorf("failed to disconnect: %w", err)
	}
	wsClient = nil

	out.PrintSuccess(fmt.Sprintf("Disconnected from %s", url))
	return nil
}

// =============================================================================
// Status Command
// =============================================================================

var wsStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show current WebSocket connection status",
	Long: `Show the current WebSocket connection status and configured URLs.

Examples:
  thunder ws status`,
	RunE: runWsStatus,
}

func init() {
	wsCmd.AddCommand(wsStatusCmd)
}

func runWsStatus(cmd *cobra.Command, args []string) error {
	connected := wsClient != nil && wsClient.IsConnected()
	var currentURL string
	if connected {
		currentURL = wsClient.URL()
	}

	commandURL := config.GetCurrentCommandWsURL()
	snapshotURL := config.GetCurrentSnapshotWsURL()
	hasToken := config.GetCurrentMatchToken() != ""

	if out.GetFormat() == "json" || out.GetFormat() == "yaml" {
		data := map[string]interface{}{
			"connected":    connected,
			"currentUrl":   currentURL,
			"hasToken":     hasToken,
			"commandUrl":   commandURL,
			"snapshotUrl":  snapshotURL,
		}
		out.Print(data)
		return nil
	}

	if connected {
		out.PrintSuccess(fmt.Sprintf("Connected to: %s", currentURL))
	} else {
		out.PrintMessage("Not connected")
	}

	out.PrintMessage("")
	out.PrintMessage("Configured URLs:")
	if commandURL != "" {
		out.PrintMessage(fmt.Sprintf("  Command:  %s", commandURL))
	} else {
		out.PrintMessage("  Command:  (not set)")
	}
	if snapshotURL != "" {
		out.PrintMessage(fmt.Sprintf("  Snapshot: %s", snapshotURL))
	} else {
		out.PrintMessage("  Snapshot: (not set)")
	}

	if hasToken {
		out.PrintMessage("\nMatch token: configured")
	} else {
		out.PrintMessage("\nMatch token: not set (join a match first)")
	}

	return nil
}
