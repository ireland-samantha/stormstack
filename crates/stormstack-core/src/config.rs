//! Configuration types.

use serde::{Deserialize, Serialize};

/// Match configuration.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchConfig {
    /// Maximum players.
    pub max_players: u32,
    /// Tick rate (ticks per second).
    pub tick_rate: f64,
    /// Game mode.
    pub game_mode: String,
    /// Custom configuration.
    pub custom_config: serde_json::Value,
}

impl Default for MatchConfig {
    fn default() -> Self {
        Self {
            max_players: 10,
            tick_rate: 60.0,
            game_mode: "default".to_string(),
            custom_config: serde_json::Value::Null,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config() {
        let config = MatchConfig::default();
        assert_eq!(config.max_players, 10);
        assert!((config.tick_rate - 60.0).abs() < f64::EPSILON);
    }

    #[test]
    fn config_serialization() {
        let config = MatchConfig {
            max_players: 20,
            tick_rate: 30.0,
            game_mode: "battle".to_string(),
            custom_config: serde_json::json!({"map": "arena"}),
        };

        let json = serde_json::to_string(&config).expect("serialize");
        let parsed: MatchConfig = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(config.max_players, parsed.max_players);
        assert_eq!(config.game_mode, parsed.game_mode);
    }
}
