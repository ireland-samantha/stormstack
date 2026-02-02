//! WebSocket upgrade handler for axum.
//!
//! This module wires the `stormstack-ws` infrastructure to the axum server,
//! providing WebSocket upgrade handling for real-time match streaming.

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, State,
    },
    response::IntoResponse,
};
use futures::{SinkExt, StreamExt};
use stormstack_core::{ConnectionId, MatchId};
use stormstack_ws::{ClientMessage, ConnectionState, ServerMessage};
use tokio::sync::mpsc;
use tracing::{debug, error, info, warn};

use crate::state::SharedAppState;

/// WebSocket upgrade handler.
///
/// Upgrades an HTTP connection to WebSocket for match streaming.
///
/// # Arguments
///
/// * `ws` - WebSocket upgrade request from axum
/// * `state` - Shared application state
/// * `match_id` - Target match ID from URL path
///
/// # Returns
///
/// Returns an axum response that completes the WebSocket upgrade.
pub async fn ws_upgrade(
    ws: WebSocketUpgrade,
    State(state): State<SharedAppState>,
    Path(match_id): Path<MatchId>,
) -> impl IntoResponse {
    info!("WebSocket upgrade requested for match {:?}", match_id);
    ws.on_upgrade(move |socket| handle_socket(socket, state, match_id))
}

/// Handle an established WebSocket connection.
///
/// This function manages the lifecycle of a WebSocket connection:
/// 1. Creates a unique connection ID
/// 2. Registers the connection with the connection manager
/// 3. Spawns a task to send outbound messages
/// 4. Processes incoming messages in a loop
/// 5. Cleans up on disconnect
async fn handle_socket(socket: WebSocket, state: SharedAppState, match_id: MatchId) {
    let conn_id = ConnectionId::new();
    info!(
        "New WebSocket connection {:?} for match {:?}",
        conn_id, match_id
    );

    let (mut ws_sender, mut ws_receiver) = socket.split();

    // Create channel for outbound messages
    let (tx, mut rx) = mpsc::unbounded_channel::<ServerMessage>();

    // Register connection with the connection manager
    let conn_state = ConnectionState::new(conn_id, tx.clone());
    state.connections().add_connection(conn_state);

    // Subscribe to the requested match
    if let Err(e) = state.connections().subscribe(conn_id, match_id) {
        error!(
            "Failed to subscribe connection {:?} to match {:?}: {}",
            conn_id, match_id, e
        );
        // Send error and close
        let error_msg = ServerMessage::Error {
            code: "SUBSCRIBE_FAILED".to_string(),
            message: format!("Failed to subscribe to match: {e}"),
        };
        if let Ok(json) = serde_json::to_string(&error_msg) {
            let _ = ws_sender.send(Message::Text(json.into())).await;
        }
        state.connections().remove_connection(conn_id);
        return;
    }

    debug!(
        "Connection {:?} subscribed to match {:?}",
        conn_id, match_id
    );

    // Spawn task to forward outbound messages from the channel to the WebSocket
    let send_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            match serde_json::to_string(&msg) {
                Ok(json) => {
                    if ws_sender.send(Message::Text(json.into())).await.is_err() {
                        debug!("WebSocket send failed, connection likely closed");
                        break;
                    }
                }
                Err(e) => {
                    error!("Failed to serialize ServerMessage: {}", e);
                }
            }
        }
        debug!("Send task exiting");
    });

    // Process incoming messages
    while let Some(result) = ws_receiver.next().await {
        match result {
            Ok(msg) => {
                if !handle_message(msg, conn_id, match_id, &state).await {
                    // Client requested close or error occurred
                    break;
                }
            }
            Err(e) => {
                warn!("WebSocket receive error for {:?}: {}", conn_id, e);
                break;
            }
        }
    }

    // Cleanup
    info!("WebSocket connection {:?} closing", conn_id);
    state.connections().remove_connection(conn_id);
    send_task.abort();
}

/// Handle an incoming WebSocket message.
///
/// Returns `true` to continue processing, `false` to close the connection.
async fn handle_message(
    msg: Message,
    conn_id: ConnectionId,
    initial_match_id: MatchId,
    state: &SharedAppState,
) -> bool {
    match msg {
        Message::Text(text) => {
            let text_str: &str = &text;
            match serde_json::from_str::<ClientMessage>(text_str) {
                Ok(client_msg) => {
                    handle_client_message(client_msg, conn_id, initial_match_id, state).await;
                }
                Err(e) => {
                    warn!(
                        "Failed to parse client message from {:?}: {}",
                        conn_id, e
                    );
                    let error_msg = ServerMessage::Error {
                        code: "INVALID_MESSAGE".to_string(),
                        message: format!("Failed to parse message: {e}"),
                    };
                    let _ = state.connections().send(conn_id, error_msg);
                }
            }
            true
        }
        Message::Binary(data) => {
            // Try to parse as JSON (some clients send binary JSON)
            match serde_json::from_slice::<ClientMessage>(&data) {
                Ok(client_msg) => {
                    handle_client_message(client_msg, conn_id, initial_match_id, state).await;
                }
                Err(e) => {
                    warn!("Failed to parse binary message from {:?}: {}", conn_id, e);
                }
            }
            true
        }
        Message::Ping(data) => {
            // axum-ws handles pong automatically, but we can log it
            debug!("Received ping from {:?}, {} bytes", conn_id, data.len());
            true
        }
        Message::Pong(_) => {
            // Client responded to our ping
            debug!("Received pong from {:?}", conn_id);
            true
        }
        Message::Close(_) => {
            debug!("Received close from {:?}", conn_id);
            false
        }
    }
}

/// Handle a parsed client message.
async fn handle_client_message(
    msg: ClientMessage,
    conn_id: ConnectionId,
    _initial_match_id: MatchId,
    state: &SharedAppState,
) {
    match msg {
        ClientMessage::Subscribe { match_id } => {
            debug!(
                "Connection {:?} subscribing to match {:?}",
                conn_id, match_id
            );
            if let Err(e) = state.connections().subscribe(conn_id, match_id) {
                let _ = state.connections().send(
                    conn_id,
                    ServerMessage::Error {
                        code: "SUBSCRIBE_FAILED".to_string(),
                        message: format!("Failed to subscribe: {e}"),
                    },
                );
            }
        }
        ClientMessage::Unsubscribe { match_id } => {
            debug!(
                "Connection {:?} unsubscribing from match {:?}",
                conn_id, match_id
            );
            state.connections().unsubscribe(conn_id, match_id);
        }
        ClientMessage::Command { match_id, command } => {
            debug!(
                "Connection {:?} sent command to match {:?}: {}",
                conn_id, match_id, command.name
            );
            // TODO: Route command to match execution engine
            warn!("Command handling not yet implemented");
            let _ = state.connections().send(
                conn_id,
                ServerMessage::Error {
                    code: "NOT_IMPLEMENTED".to_string(),
                    message: "Command handling not yet implemented".to_string(),
                },
            );
        }
        ClientMessage::Ping { timestamp } => {
            let server_time = chrono::Utc::now().timestamp_millis();
            let _ = state.connections().send(
                conn_id,
                ServerMessage::Pong {
                    timestamp,
                    server_time,
                },
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
        Router,
    };
    use stormstack_auth::JwtService;
    use stormstack_ecs::shared_world;
    use stormstack_wasm::WasmSandbox;
    use stormstack_ws::{shared_connection_manager, shared_subscriptions};
    use std::sync::Arc;
    use tower::ServiceExt;

    use crate::state::AppState;

    fn create_test_state() -> SharedAppState {
        let jwt_service = Arc::new(JwtService::new(b"test-secret-key-32-bytes-long!!"));
        let world = shared_world();
        let sandbox = Arc::new(WasmSandbox::new().expect("sandbox"));
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);

        Arc::new(AppState::new(jwt_service, world, sandbox, connections))
    }

    fn create_ws_router(state: SharedAppState) -> Router {
        use axum::routing::get;

        Router::new()
            .route("/ws/matches/{match_id}", get(ws_upgrade))
            .with_state(state)
    }

    #[tokio::test]
    async fn ws_upgrade_requires_websocket_request() {
        let state = create_test_state();
        let app = create_ws_router(state);
        let match_id = MatchId::new();

        // Regular HTTP request should fail (not a WebSocket upgrade)
        let request = Request::builder()
            .uri(format!("/ws/matches/{}", match_id.0))
            .body(Body::empty())
            .expect("request");

        let response = app.oneshot(request).await.expect("response");

        // Without proper WebSocket upgrade headers, this should fail
        // The exact status depends on axum version, but it won't be 101 Switching Protocols
        assert_ne!(response.status(), StatusCode::SWITCHING_PROTOCOLS);
    }

    #[tokio::test]
    async fn ws_upgrade_creates_connection() {
        let state = create_test_state();
        assert_eq!(state.connections().connection_count(), 0);

        // Create a connection state manually to simulate what ws_upgrade does
        let (tx, _rx) = mpsc::unbounded_channel();
        let conn_state = ConnectionState::new(ConnectionId::new(), tx);
        let id = state.connections().add_connection(conn_state);

        assert_eq!(state.connections().connection_count(), 1);
        assert!(state.connections().has_connection(id));
    }

    #[tokio::test]
    async fn ws_receives_messages() {
        let state = create_test_state();
        let conn_id = ConnectionId::new();
        let match_id = MatchId::new();

        // Create connection
        let (tx, _rx) = mpsc::unbounded_channel();
        let conn_state = ConnectionState::new(conn_id, tx);
        state.connections().add_connection(conn_state);

        // Test handling subscribe message
        let subscribe_msg = ClientMessage::Subscribe { match_id };
        handle_client_message(subscribe_msg, conn_id, match_id, &state).await;

        // Connection should now be subscribed
        // Note: We can't directly check subscription state without accessing the subscription manager
    }

    #[tokio::test]
    async fn ws_sends_server_messages() {
        let state = create_test_state();
        let conn_id = ConnectionId::new();

        // Create connection with receiver
        let (tx, mut rx) = mpsc::unbounded_channel();
        let conn_state = ConnectionState::new(conn_id, tx);
        state.connections().add_connection(conn_state);

        // Send a message through the connection manager
        let message = ServerMessage::Pong {
            timestamp: 12345,
            server_time: 67890,
        };
        state
            .connections()
            .send(conn_id, message)
            .expect("send should succeed");

        // Receive the message
        let received = rx.recv().await.expect("should receive message");
        match received {
            ServerMessage::Pong {
                timestamp,
                server_time,
            } => {
                assert_eq!(timestamp, 12345);
                assert_eq!(server_time, 67890);
            }
            _ => panic!("expected Pong message"),
        }
    }

    #[tokio::test]
    async fn ws_cleanup_on_disconnect() {
        let state = create_test_state();
        let conn_id = ConnectionId::new();
        let match_id = MatchId::new();

        // Create and register connection
        let (tx, _rx) = mpsc::unbounded_channel();
        let conn_state = ConnectionState::new(conn_id, tx);
        state.connections().add_connection(conn_state);
        state
            .connections()
            .subscribe(conn_id, match_id)
            .expect("subscribe");

        assert!(state.connections().has_connection(conn_id));

        // Simulate disconnect cleanup
        state.connections().remove_connection(conn_id);

        assert!(!state.connections().has_connection(conn_id));
        assert_eq!(state.connections().connection_count(), 0);
    }

    #[tokio::test]
    async fn handle_ping_message() {
        let state = create_test_state();
        let conn_id = ConnectionId::new();
        let match_id = MatchId::new();

        // Create connection with receiver
        let (tx, mut rx) = mpsc::unbounded_channel();
        let conn_state = ConnectionState::new(conn_id, tx);
        state.connections().add_connection(conn_state);

        // Send ping
        let ping_msg = ClientMessage::Ping { timestamp: 99999 };
        handle_client_message(ping_msg, conn_id, match_id, &state).await;

        // Should receive pong
        let received = rx.recv().await.expect("should receive pong");
        match received {
            ServerMessage::Pong {
                timestamp,
                server_time,
            } => {
                assert_eq!(timestamp, 99999);
                assert!(server_time > 0);
            }
            _ => panic!("expected Pong message"),
        }
    }

    #[test]
    fn client_message_parse() {
        let json = r#"{"type":"Subscribe","match_id":"550e8400-e29b-41d4-a716-446655440000"}"#;
        let msg: ClientMessage = serde_json::from_str(json).expect("parse");
        match msg {
            ClientMessage::Subscribe { match_id } => {
                assert_eq!(
                    match_id.0.to_string(),
                    "550e8400-e29b-41d4-a716-446655440000"
                );
            }
            _ => panic!("expected Subscribe"),
        }
    }

    #[test]
    fn server_message_serialize() {
        let msg = ServerMessage::Error {
            code: "TEST".to_string(),
            message: "test error".to_string(),
        };
        let json = serde_json::to_string(&msg).expect("serialize");
        assert!(json.contains("\"type\":\"Error\""));
        assert!(json.contains("TEST"));
    }
}
