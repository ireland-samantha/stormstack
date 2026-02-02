//! WebSocket message types.

use serde::{Deserialize, Serialize};
use stormstack_core::{MatchId, WorldDelta, WorldSnapshot};

/// Client to server message.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ClientMessage {
    /// Subscribe to match updates.
    Subscribe {
        /// Match to subscribe to.
        match_id: MatchId,
    },
    /// Unsubscribe from match updates.
    Unsubscribe {
        /// Match to unsubscribe from.
        match_id: MatchId,
    },
    /// Send command to match.
    Command {
        /// Target match.
        match_id: MatchId,
        /// Command to execute.
        command: Command,
    },
    /// Ping for keepalive.
    Ping {
        /// Client timestamp.
        timestamp: i64,
    },
}

/// Server to client message.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ServerMessage {
    /// Full world snapshot.
    Snapshot {
        /// Source match.
        match_id: MatchId,
        /// World state.
        snapshot: WorldSnapshot,
    },
    /// Delta update.
    Delta {
        /// Source match.
        match_id: MatchId,
        /// State changes.
        delta: WorldDelta,
    },
    /// Command result.
    CommandResult {
        /// Target match.
        match_id: MatchId,
        /// Execution result.
        result: CommandResult,
    },
    /// Error message.
    Error {
        /// Error code.
        code: String,
        /// Error description.
        message: String,
    },
    /// Pong response.
    Pong {
        /// Original client timestamp.
        timestamp: i64,
        /// Server timestamp.
        server_time: i64,
    },
}

/// Game command from client.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Command {
    /// Command name.
    pub name: String,
    /// Target entity (optional).
    pub entity_id: Option<u64>,
    /// Command payload.
    pub payload: serde_json::Value,
}

/// Command execution result.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    /// Whether command succeeded.
    pub success: bool,
    /// Command identifier.
    pub command_id: String,
    /// Tick when executed (if successful).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub executed_tick: Option<u64>,
    /// Error message (if failed).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn client_message_serialize() {
        let msg = ClientMessage::Subscribe {
            match_id: MatchId::new(),
        };
        let json = serde_json::to_string(&msg).expect("serialize");
        assert!(json.contains("\"type\":\"Subscribe\""));
    }

    #[test]
    fn server_message_serialize() {
        let msg = ServerMessage::Error {
            code: "NOT_FOUND".to_string(),
            message: "Match not found".to_string(),
        };
        let json = serde_json::to_string(&msg).expect("serialize");
        assert!(json.contains("\"type\":\"Error\""));
    }

    #[test]
    fn command_result_success() {
        let result = CommandResult {
            success: true,
            command_id: "cmd-1".to_string(),
            executed_tick: Some(100),
            error: None,
        };
        let json = serde_json::to_string(&result).expect("serialize");
        assert!(!json.contains("error"));
    }
}
