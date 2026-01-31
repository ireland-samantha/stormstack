// Package ws provides WebSocket client functionality for the Thunder CLI.
package ws

import (
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// Client manages a WebSocket connection.
type Client struct {
	conn      *websocket.Conn
	url       string
	token     string
	connected bool
	mu        sync.Mutex
}

// NewClient creates a new WebSocket client.
func NewClient(url, token string) *Client {
	return &Client{
		url:   url,
		token: token,
	}
}

// Connect establishes the WebSocket connection.
func (c *Client) Connect() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.connected {
		return errors.New("already connected")
	}

	// Build URL with token as query parameter
	// The server expects token in query param for WebSocket auth (during HTTP upgrade)
	connectURL := c.url
	if c.token != "" {
		separator := "?"
		if strings.Contains(c.url, "?") {
			separator = "&"
		}
		connectURL = c.url + separator + "token=" + c.token
	}

	dialer := websocket.Dialer{
		HandshakeTimeout: 10 * time.Second,
	}

	conn, resp, err := dialer.Dial(connectURL, nil)
	if err != nil {
		if resp != nil {
			return fmt.Errorf("websocket dial failed: %w (status: %d)", err, resp.StatusCode)
		}
		return fmt.Errorf("websocket dial failed: %w", err)
	}

	c.conn = conn
	c.connected = true
	return nil
}

// IsConnected returns whether the client is connected.
func (c *Client) IsConnected() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.connected
}

// Send sends a text message.
func (c *Client) Send(message string) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.connected {
		return errors.New("not connected")
	}

	return c.conn.WriteMessage(websocket.TextMessage, []byte(message))
}

// SendBinary sends a binary message.
func (c *Client) SendBinary(data []byte) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.connected {
		return errors.New("not connected")
	}

	return c.conn.WriteMessage(websocket.BinaryMessage, data)
}

// Receive receives a message with an optional timeout.
// If timeout is 0, it blocks until a message is received.
func (c *Client) Receive(timeout time.Duration) (string, bool, error) {
	c.mu.Lock()
	if !c.connected {
		c.mu.Unlock()
		return "", false, errors.New("not connected")
	}
	conn := c.conn
	c.mu.Unlock()

	if timeout > 0 {
		conn.SetReadDeadline(time.Now().Add(timeout))
		defer conn.SetReadDeadline(time.Time{})
	}

	msgType, data, err := conn.ReadMessage()
	if err != nil {
		return "", false, err
	}

	isBinary := msgType == websocket.BinaryMessage
	return string(data), isBinary, nil
}

// ReceiveRaw receives a message and returns it as raw bytes.
func (c *Client) ReceiveRaw(timeout time.Duration) ([]byte, bool, error) {
	c.mu.Lock()
	if !c.connected {
		c.mu.Unlock()
		return nil, false, errors.New("not connected")
	}
	conn := c.conn
	c.mu.Unlock()

	if timeout > 0 {
		conn.SetReadDeadline(time.Now().Add(timeout))
		defer conn.SetReadDeadline(time.Time{})
	}

	msgType, data, err := conn.ReadMessage()
	if err != nil {
		return nil, false, err
	}

	isBinary := msgType == websocket.BinaryMessage
	return data, isBinary, nil
}

// Close closes the WebSocket connection.
func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.connected {
		return nil
	}

	err := c.conn.WriteMessage(websocket.CloseMessage,
		websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
	if err != nil {
		// Best effort close message, continue with connection close
	}

	c.connected = false
	return c.conn.Close()
}

// URL returns the WebSocket URL.
func (c *Client) URL() string {
	return c.url
}
