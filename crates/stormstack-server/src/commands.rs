//! Command registry and execution system.
//!
//! Provides a registry for mapping command names to command factories,
//! allowing commands to be created dynamically from API requests.
//!
//! # Architecture
//!
//! The command system consists of:
//! - [`CommandRegistry`]: Maps command names to factory functions
//! - [`CommandProvider`]: Trait for modules to register commands
//! - Built-in commands: `spawn_entity`, `despawn_entity`
//!
//! # Example
//!
//! ```ignore
//! use stormstack_server::CommandRegistry;
//!
//! let mut registry = CommandRegistry::default();
//!
//! // Register a custom command
//! registry.register("move_entity", |payload| {
//!     let entity_id = serde_json::from_value(payload["entity_id"].clone())?;
//!     let dx = payload["dx"].as_f64().unwrap_or(0.0);
//!     let dy = payload["dy"].as_f64().unwrap_or(0.0);
//!     Ok(Box::new(MoveCommand { entity_id, dx, dy }))
//! });
//!
//! // Create a command from a request
//! let cmd = registry.create("move_entity", serde_json::json!({
//!     "entity_id": 42,
//!     "dx": 1.0,
//!     "dy": -1.0
//! }))?;
//! ```

use std::collections::HashMap;
use std::sync::Arc;

use parking_lot::RwLock;
use stormstack_core::{
    Command, DespawnEntityCommand, EntityId, Result, SpawnEntityCommand, StormError,
};
use tracing::{debug, trace};

/// Factory function type for creating commands from JSON payloads.
///
/// Takes a JSON value and returns a boxed command or an error.
pub type CommandFactory =
    Box<dyn Fn(serde_json::Value) -> Result<Box<dyn Command>> + Send + Sync>;

/// Registry mapping command names to factory functions.
///
/// Thread-safe and can be shared across handlers.
///
/// # Built-in Commands
///
/// The default registry includes:
/// - `spawn_entity`: Creates a new entity (no payload required)
/// - `despawn_entity`: Removes an entity (requires `entity_id`)
pub struct CommandRegistry {
    commands: HashMap<String, CommandFactory>,
}

impl CommandRegistry {
    /// Create a new empty command registry.
    #[must_use]
    pub fn new() -> Self {
        debug!("Creating new CommandRegistry");
        Self {
            commands: HashMap::new(),
        }
    }

    /// Register a command factory.
    ///
    /// If a command with the same name already exists, it will be replaced.
    ///
    /// # Arguments
    ///
    /// * `name` - The command name (used in API requests)
    /// * `factory` - Factory function that creates command instances
    pub fn register<F>(&mut self, name: &str, factory: F)
    where
        F: Fn(serde_json::Value) -> Result<Box<dyn Command>> + Send + Sync + 'static,
    {
        debug!("Registering command: {}", name);
        self.commands.insert(name.to_string(), Box::new(factory));
    }

    /// Create a command instance from a name and payload.
    ///
    /// # Arguments
    ///
    /// * `name` - The command name
    /// * `payload` - JSON payload for the command
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - The command name is not registered
    /// - The factory fails to create the command
    pub fn create(&self, name: &str, payload: serde_json::Value) -> Result<Box<dyn Command>> {
        trace!("Creating command '{}' with payload: {:?}", name, payload);

        let factory = self.commands.get(name).ok_or_else(|| {
            StormError::InvalidState(format!("Unknown command type: {}", name))
        })?;

        factory(payload)
    }

    /// Get a list of all registered command names.
    #[must_use]
    pub fn available_commands(&self) -> Vec<&str> {
        self.commands.keys().map(|s| s.as_str()).collect()
    }

    /// Check if a command is registered.
    #[must_use]
    pub fn has_command(&self, name: &str) -> bool {
        self.commands.contains_key(name)
    }

    /// Get the number of registered commands.
    #[must_use]
    pub fn count(&self) -> usize {
        self.commands.len()
    }
}

impl Default for CommandRegistry {
    fn default() -> Self {
        let mut registry = Self::new();

        // Register built-in spawn_entity command
        registry.register("spawn_entity", |_payload| {
            Ok(Box::new(SpawnEntityCommand::new()))
        });

        // Register built-in despawn_entity command
        registry.register("despawn_entity", |payload| {
            let entity_id_value = payload
                .get("entity_id")
                .ok_or_else(|| StormError::InvalidState("Missing entity_id".to_string()))?;

            let entity_id = if let Some(id) = entity_id_value.as_u64() {
                EntityId(id)
            } else {
                return Err(StormError::InvalidState(
                    "entity_id must be a number".to_string(),
                ));
            };

            Ok(Box::new(DespawnEntityCommand::new(entity_id)))
        });

        debug!(
            "CommandRegistry created with {} built-in commands",
            registry.count()
        );
        registry
    }
}

impl std::fmt::Debug for CommandRegistry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CommandRegistry")
            .field("command_count", &self.commands.len())
            .field("commands", &self.available_commands())
            .finish()
    }
}

/// Trait for modules to register their commands.
///
/// Modules that provide custom commands should implement this trait
/// and be registered with the server during startup.
///
/// # Example
///
/// ```ignore
/// struct MyModule;
///
/// impl CommandProvider for MyModule {
///     fn register_commands(&self, registry: &mut CommandRegistry) {
///         registry.register("custom_action", |payload| {
///             // Create and return command
///             Ok(Box::new(CustomActionCommand::from_payload(payload)?))
///         });
///     }
/// }
/// ```
pub trait CommandProvider: Send + Sync {
    /// Register this provider's commands with the registry.
    fn register_commands(&self, registry: &mut CommandRegistry);
}

/// Thread-safe shared command registry.
pub type SharedCommandRegistry = Arc<RwLock<CommandRegistry>>;

/// Create a new shared command registry with default commands.
#[must_use]
pub fn shared_command_registry() -> SharedCommandRegistry {
    Arc::new(RwLock::new(CommandRegistry::default()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_core::{CommandContext, CommandResult, CommandWorld, MatchId, UserId};
    use std::collections::HashMap as StdHashMap;

    /// Mock world for testing.
    struct MockWorld {
        entities: StdHashMap<EntityId, ()>,
        next_id: u64,
        tick: u64,
    }

    impl MockWorld {
        fn new() -> Self {
            Self {
                entities: StdHashMap::new(),
                next_id: 1,
                tick: 0,
            }
        }
    }

    impl CommandWorld for MockWorld {
        fn spawn_entity(&mut self) -> EntityId {
            let id = EntityId(self.next_id);
            self.next_id += 1;
            self.entities.insert(id, ());
            id
        }

        fn despawn_entity(&mut self, entity: EntityId) -> Result<()> {
            if self.entities.remove(&entity).is_some() {
                Ok(())
            } else {
                Err(StormError::EntityNotFound(entity))
            }
        }

        fn entity_exists(&self, entity: EntityId) -> bool {
            self.entities.contains_key(&entity)
        }

        fn current_tick(&self) -> u64 {
            self.tick
        }
    }

    /// Custom test command.
    struct TestCommand {
        value: i32,
    }

    impl Command for TestCommand {
        fn execute(
            &self,
            _ctx: &mut CommandContext,
        ) -> stormstack_core::Result<CommandResult> {
            Ok(CommandResult::success_with_data(serde_json::json!({
                "value": self.value
            })))
        }

        fn name(&self) -> &'static str {
            "TestCommand"
        }
    }

    #[test]
    fn registry_default_has_builtin_commands() {
        let registry = CommandRegistry::default();

        assert!(registry.has_command("spawn_entity"));
        assert!(registry.has_command("despawn_entity"));
        assert_eq!(registry.count(), 2);
    }

    #[test]
    fn registry_register_custom_command() {
        let mut registry = CommandRegistry::new();

        registry.register("test_cmd", |payload| {
            let value = payload["value"].as_i64().unwrap_or(0) as i32;
            Ok(Box::new(TestCommand { value }))
        });

        assert!(registry.has_command("test_cmd"));
        assert_eq!(registry.count(), 1);
    }

    #[test]
    fn registry_create_command() {
        let mut registry = CommandRegistry::new();

        registry.register("test_cmd", |payload| {
            let value = payload["value"].as_i64().unwrap_or(0) as i32;
            Ok(Box::new(TestCommand { value }))
        });

        let cmd = registry
            .create("test_cmd", serde_json::json!({"value": 42}))
            .expect("create command");

        assert_eq!(cmd.name(), "TestCommand");
    }

    #[test]
    fn registry_create_unknown_command_fails() {
        let registry = CommandRegistry::new();

        let result = registry.create("unknown", serde_json::json!({}));

        assert!(result.is_err());
    }

    #[test]
    fn registry_available_commands() {
        let registry = CommandRegistry::default();

        let commands = registry.available_commands();

        assert!(commands.contains(&"spawn_entity"));
        assert!(commands.contains(&"despawn_entity"));
    }

    #[test]
    fn spawn_entity_via_registry() {
        let registry = CommandRegistry::default();
        let mut world = MockWorld::new();

        // Create spawn command
        let cmd = registry
            .create("spawn_entity", serde_json::json!({}))
            .expect("create spawn");

        // Execute it
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);
        let result = cmd.execute(&mut ctx).expect("execute");

        assert!(result.is_success());
        assert!(result.data.is_some());
        let entity_id = result.data.unwrap()["entity_id"].as_u64().unwrap();
        assert!(world.entity_exists(EntityId(entity_id)));
    }

    #[test]
    fn despawn_entity_via_registry() {
        let registry = CommandRegistry::default();
        let mut world = MockWorld::new();

        // Spawn an entity first
        let entity_id = world.spawn_entity();
        assert!(world.entity_exists(entity_id));

        // Create despawn command
        let cmd = registry
            .create(
                "despawn_entity",
                serde_json::json!({"entity_id": entity_id.0}),
            )
            .expect("create despawn");

        // Execute it
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);
        let result = cmd.execute(&mut ctx).expect("execute");

        assert!(result.is_success());
        assert!(!world.entity_exists(entity_id));
    }

    #[test]
    fn despawn_entity_missing_id_fails() {
        let registry = CommandRegistry::default();

        let result = registry.create("despawn_entity", serde_json::json!({}));

        assert!(result.is_err());
    }

    #[test]
    fn despawn_entity_invalid_id_fails() {
        let registry = CommandRegistry::default();

        let result = registry.create("despawn_entity", serde_json::json!({"entity_id": "not_a_number"}));

        assert!(result.is_err());
    }

    #[test]
    fn unknown_command_returns_error() {
        let registry = CommandRegistry::default();

        let result = registry.create("nonexistent_command", serde_json::json!({}));

        assert!(result.is_err());
        match result {
            Err(e) => assert!(e.to_string().contains("Unknown command type")),
            Ok(_) => panic!("Expected error for unknown command"),
        }
    }

    #[test]
    fn shared_registry_works() {
        let registry = shared_command_registry();

        {
            let r = registry.read();
            assert!(r.has_command("spawn_entity"));
        }

        {
            let mut r = registry.write();
            r.register("custom", |_| Ok(Box::new(TestCommand { value: 1 })));
        }

        {
            let r = registry.read();
            assert!(r.has_command("custom"));
        }
    }

    #[test]
    fn registry_debug_format() {
        let registry = CommandRegistry::default();
        let debug_str = format!("{:?}", registry);

        assert!(debug_str.contains("CommandRegistry"));
        assert!(debug_str.contains("command_count"));
    }

    /// Test implementation of CommandProvider
    struct TestProvider;

    impl CommandProvider for TestProvider {
        fn register_commands(&self, registry: &mut CommandRegistry) {
            registry.register("provider_cmd", |_| {
                Ok(Box::new(TestCommand { value: 99 }))
            });
        }
    }

    #[test]
    fn command_provider_registers_commands() {
        let mut registry = CommandRegistry::new();
        let provider = TestProvider;

        provider.register_commands(&mut registry);

        assert!(registry.has_command("provider_cmd"));

        let cmd = registry
            .create("provider_cmd", serde_json::json!({}))
            .expect("create");
        assert_eq!(cmd.name(), "TestCommand");
    }
}
