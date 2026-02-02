//! Command system for game logic execution.
//!
//! The command pattern provides a way to encapsulate game actions as objects,
//! allowing them to be queued, logged, and executed in a controlled manner.
//!
//! # Architecture
//!
//! - [`Command`]: Trait for executable game commands
//! - [`CommandQueue`]: Queue of pending commands awaiting execution
//! - [`CommandContext`]: Context available during command execution
//! - [`CommandResult`]: Result of executing a command
//!
//! # Built-in Commands
//!
//! - [`SpawnEntityCommand`]: Spawn a new entity in the world
//! - [`DespawnEntityCommand`]: Remove an entity from the world
//!
//! # Example
//!
//! ```ignore
//! use stormstack_core::command::{Command, CommandContext, CommandQueue, CommandResult};
//!
//! struct MoveCommand {
//!     entity: EntityId,
//!     dx: f32,
//!     dy: f32,
//! }
//!
//! impl Command for MoveCommand {
//!     fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
//!         // Apply movement logic
//!         Ok(CommandResult::success())
//!     }
//!
//!     fn name(&self) -> &'static str {
//!         "MoveCommand"
//!     }
//! }
//! ```

use crate::id::{EntityId, MatchId, UserId};
use crate::Result;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::time::Instant;
use tracing::{debug, trace, warn};

/// Result of executing a command.
///
/// Contains success/failure status along with optional message and data.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandResult {
    /// Whether the command executed successfully.
    pub success: bool,

    /// Optional human-readable message about the result.
    pub message: Option<String>,

    /// Optional structured data returned by the command.
    pub data: Option<serde_json::Value>,
}

impl CommandResult {
    /// Create a successful result with no additional data.
    #[must_use]
    pub fn success() -> Self {
        Self {
            success: true,
            message: None,
            data: None,
        }
    }

    /// Create a successful result with a message.
    #[must_use]
    pub fn success_with_message(message: impl Into<String>) -> Self {
        Self {
            success: true,
            message: Some(message.into()),
            data: None,
        }
    }

    /// Create a successful result with data.
    #[must_use]
    pub fn success_with_data(data: serde_json::Value) -> Self {
        Self {
            success: true,
            message: None,
            data: Some(data),
        }
    }

    /// Create a failure result with a message.
    #[must_use]
    pub fn failure(message: impl Into<String>) -> Self {
        Self {
            success: false,
            message: Some(message.into()),
            data: None,
        }
    }

    /// Create a failure result with message and data.
    #[must_use]
    pub fn failure_with_data(message: impl Into<String>, data: serde_json::Value) -> Self {
        Self {
            success: false,
            message: Some(message.into()),
            data: Some(data),
        }
    }

    /// Check if the command succeeded.
    #[must_use]
    pub fn is_success(&self) -> bool {
        self.success
    }

    /// Check if the command failed.
    #[must_use]
    pub fn is_failure(&self) -> bool {
        !self.success
    }
}

impl Default for CommandResult {
    fn default() -> Self {
        Self::success()
    }
}

/// Minimal world interface for command execution.
///
/// This trait defines the minimal set of operations that commands need
/// to interact with the ECS world. It is implemented by `StormWorld`
/// in the `stormstack-ecs` crate.
///
/// # Design Note
///
/// This trait lives in `stormstack-core` to avoid circular dependencies,
/// while the full `EcsWorld` trait with additional functionality lives
/// in `stormstack-ecs`.
pub trait CommandWorld: Send + Sync {
    /// Spawn a new entity and return its ID.
    fn spawn_entity(&mut self) -> EntityId;

    /// Despawn an entity, removing it from the world.
    ///
    /// # Errors
    ///
    /// Returns an error if the entity does not exist.
    fn despawn_entity(&mut self, entity: EntityId) -> Result<()>;

    /// Check if an entity exists in the world.
    fn entity_exists(&self, entity: EntityId) -> bool;

    /// Get the current tick number.
    fn current_tick(&self) -> u64;
}

/// Context available during command execution.
///
/// Provides access to the ECS world and contextual information
/// about who is executing the command and when.
pub struct CommandContext<'a> {
    /// Mutable reference to the ECS world.
    pub world: &'a mut dyn CommandWorld,

    /// ID of the match this command is executed in.
    pub match_id: MatchId,

    /// ID of the user who issued the command.
    pub user_id: UserId,

    /// Current game tick when the command executes.
    pub tick: u64,
}

impl<'a> CommandContext<'a> {
    /// Create a new command context.
    #[must_use]
    pub fn new(
        world: &'a mut dyn CommandWorld,
        match_id: MatchId,
        user_id: UserId,
        tick: u64,
    ) -> Self {
        Self {
            world,
            match_id,
            user_id,
            tick,
        }
    }
}

/// Trait for executable game commands.
///
/// Commands encapsulate game actions that can be queued and executed.
/// Each command should be a self-contained unit of work that modifies
/// the game state through the provided [`CommandContext`].
///
/// # Thread Safety
///
/// Commands must be `Send + Sync` to allow queueing from multiple threads.
///
/// # Example
///
/// ```ignore
/// struct AttackCommand {
///     attacker: EntityId,
///     target: EntityId,
///     damage: u32,
/// }
///
/// impl Command for AttackCommand {
///     fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
///         // Validate entities exist
///         if !ctx.world.entity_exists(self.attacker) {
///             return Ok(CommandResult::failure("Attacker does not exist"));
///         }
///         if !ctx.world.entity_exists(self.target) {
///             return Ok(CommandResult::failure("Target does not exist"));
///         }
///         // Apply damage logic...
///         Ok(CommandResult::success_with_message("Attack landed"))
///     }
///
///     fn name(&self) -> &'static str {
///         "AttackCommand"
///     }
/// }
/// ```
pub trait Command: Send + Sync {
    /// Execute the command against the world.
    ///
    /// Returns a [`CommandResult`] indicating success or failure.
    /// Errors should be returned via `Result::Err` for unexpected failures,
    /// while expected failures (e.g., invalid target) should return
    /// `Ok(CommandResult::failure(...))`.
    ///
    /// # Errors
    ///
    /// Returns an error for unexpected failures during command execution.
    /// Expected failures (e.g., target not found) should return
    /// `Ok(CommandResult::failure(...))` instead.
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult>;

    /// Command name for logging and debugging.
    ///
    /// Should return a static string identifying the command type.
    fn name(&self) -> &'static str;
}

/// A command queued for later execution.
pub struct QueuedCommand {
    /// The command to execute.
    pub command: Box<dyn Command>,

    /// User who queued this command.
    pub user_id: UserId,

    /// When the command was queued.
    pub queued_at: Instant,
}

impl QueuedCommand {
    /// Create a new queued command.
    #[must_use]
    pub fn new(command: Box<dyn Command>, user_id: UserId) -> Self {
        Self {
            command,
            user_id,
            queued_at: Instant::now(),
        }
    }

    /// Get how long this command has been queued.
    #[must_use]
    pub fn time_in_queue(&self) -> std::time::Duration {
        self.queued_at.elapsed()
    }
}

impl std::fmt::Debug for QueuedCommand {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("QueuedCommand")
            .field("command_name", &self.command.name())
            .field("user_id", &self.user_id)
            .field("queued_at", &self.queued_at)
            .finish()
    }
}

/// Queue of pending commands awaiting execution.
///
/// Commands are executed in FIFO order. The queue is typically drained
/// once per game tick.
///
/// # Example
///
/// ```ignore
/// let mut queue = CommandQueue::new();
///
/// // Queue some commands
/// queue.push(Box::new(MoveCommand { ... }), user_id);
/// queue.push(Box::new(AttackCommand { ... }), user_id);
///
/// // Execute all queued commands
/// let results = queue.execute_all(&mut ctx);
/// for result in results {
///     if result.is_failure() {
///         warn!("Command failed: {:?}", result.message);
///     }
/// }
/// ```
#[derive(Default)]
pub struct CommandQueue {
    queue: VecDeque<QueuedCommand>,
}

impl CommandQueue {
    /// Create a new empty command queue.
    #[must_use]
    pub fn new() -> Self {
        Self {
            queue: VecDeque::new(),
        }
    }

    /// Create a command queue with pre-allocated capacity.
    #[must_use]
    pub fn with_capacity(capacity: usize) -> Self {
        Self {
            queue: VecDeque::with_capacity(capacity),
        }
    }

    /// Push a command onto the queue.
    pub fn push(&mut self, command: Box<dyn Command>, user_id: UserId) {
        let queued = QueuedCommand::new(command, user_id);
        trace!(
            "Queuing command '{}' from user {}",
            queued.command.name(),
            user_id
        );
        self.queue.push_back(queued);
    }

    /// Pop the next command from the queue.
    #[must_use]
    pub fn pop(&mut self) -> Option<QueuedCommand> {
        self.queue.pop_front()
    }

    /// Execute all queued commands and return their results.
    ///
    /// Commands are executed in FIFO order. Each command receives a
    /// fresh [`CommandContext`] with the user who queued it.
    ///
    /// The queue is empty after this call.
    pub fn execute_all(
        &mut self,
        world: &mut dyn CommandWorld,
        match_id: MatchId,
    ) -> Vec<CommandResult> {
        let tick = world.current_tick();
        let count = self.queue.len();

        if count > 0 {
            debug!("Executing {} queued commands at tick {}", count, tick);
        }

        let mut results = Vec::with_capacity(count);

        while let Some(queued) = self.queue.pop_front() {
            let mut ctx = CommandContext::new(world, match_id, queued.user_id, tick);
            let command_name = queued.command.name();
            let time_queued = queued.time_in_queue();

            trace!(
                "Executing command '{}' from user {} (queued {:?} ago)",
                command_name,
                queued.user_id,
                time_queued
            );

            match queued.command.execute(&mut ctx) {
                Ok(result) => {
                    if result.is_failure() {
                        warn!(
                            "Command '{}' failed: {:?}",
                            command_name, result.message
                        );
                    }
                    results.push(result);
                }
                Err(e) => {
                    warn!("Command '{}' errored: {}", command_name, e);
                    results.push(CommandResult::failure(format!("Internal error: {e}")));
                }
            }
        }

        results
    }

    /// Get the number of commands in the queue.
    #[must_use]
    pub fn len(&self) -> usize {
        self.queue.len()
    }

    /// Check if the queue is empty.
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.queue.is_empty()
    }

    /// Clear all commands from the queue without executing them.
    pub fn clear(&mut self) {
        let count = self.queue.len();
        if count > 0 {
            debug!("Clearing {} commands from queue", count);
        }
        self.queue.clear();
    }

    /// Iterate over queued commands without removing them.
    pub fn iter(&self) -> impl Iterator<Item = &QueuedCommand> {
        self.queue.iter()
    }
}

impl std::fmt::Debug for CommandQueue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CommandQueue")
            .field("len", &self.queue.len())
            .finish()
    }
}

// =============================================================================
// Built-in Commands
// =============================================================================

/// Command to spawn a new entity in the world.
///
/// Creates an empty entity and returns its ID in the result data.
///
/// # Result Data
///
/// On success, the result data contains:
/// ```json
/// { "entity_id": <u64> }
/// ```
#[derive(Debug, Clone)]
pub struct SpawnEntityCommand;

impl SpawnEntityCommand {
    /// Create a new spawn entity command.
    #[must_use]
    pub fn new() -> Self {
        Self
    }
}

impl Default for SpawnEntityCommand {
    fn default() -> Self {
        Self::new()
    }
}

impl Command for SpawnEntityCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        let entity_id = ctx.world.spawn_entity();
        debug!(
            "SpawnEntityCommand: spawned entity {} at tick {}",
            entity_id, ctx.tick
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": entity_id.0
        })))
    }

    fn name(&self) -> &'static str {
        "SpawnEntityCommand"
    }
}

/// Command to despawn (remove) an entity from the world.
///
/// Removes the entity and all its components from the world.
///
/// # Errors
///
/// Returns a failure result if the entity does not exist.
#[derive(Debug, Clone)]
pub struct DespawnEntityCommand {
    /// ID of the entity to despawn.
    pub entity_id: EntityId,
}

impl DespawnEntityCommand {
    /// Create a new despawn entity command.
    #[must_use]
    pub fn new(entity_id: EntityId) -> Self {
        Self { entity_id }
    }
}

impl Command for DespawnEntityCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        ctx.world.despawn_entity(self.entity_id)?;
        debug!(
            "DespawnEntityCommand: despawned entity {} at tick {}",
            self.entity_id, ctx.tick
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0
        })))
    }

    fn name(&self) -> &'static str {
        "DespawnEntityCommand"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::StormError;
    use std::collections::HashMap;

    /// Mock world for testing commands.
    struct MockWorld {
        entities: HashMap<EntityId, ()>,
        next_id: u64,
        tick: u64,
    }

    impl MockWorld {
        fn new() -> Self {
            Self {
                entities: HashMap::new(),
                next_id: 1,
                tick: 0,
            }
        }

        fn with_tick(tick: u64) -> Self {
            Self {
                entities: HashMap::new(),
                next_id: 1,
                tick,
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

    /// Test command that always succeeds.
    struct SuccessCommand;
    impl Command for SuccessCommand {
        fn execute(&self, _ctx: &mut CommandContext) -> Result<CommandResult> {
            Ok(CommandResult::success_with_message("Success!"))
        }
        fn name(&self) -> &'static str {
            "SuccessCommand"
        }
    }

    /// Test command that always fails.
    struct FailureCommand;
    impl Command for FailureCommand {
        fn execute(&self, _ctx: &mut CommandContext) -> Result<CommandResult> {
            Ok(CommandResult::failure("This command always fails"))
        }
        fn name(&self) -> &'static str {
            "FailureCommand"
        }
    }

    /// Test command that returns an error.
    struct ErrorCommand;
    impl Command for ErrorCommand {
        fn execute(&self, _ctx: &mut CommandContext) -> Result<CommandResult> {
            Err(StormError::InvalidState("Test error".to_string()))
        }
        fn name(&self) -> &'static str {
            "ErrorCommand"
        }
    }

    /// Command that records execution order.
    struct OrderedCommand {
        id: usize,
        order: std::sync::Arc<std::sync::Mutex<Vec<usize>>>,
    }

    impl Command for OrderedCommand {
        fn execute(&self, _ctx: &mut CommandContext) -> Result<CommandResult> {
            self.order.lock().unwrap().push(self.id);
            Ok(CommandResult::success())
        }
        fn name(&self) -> &'static str {
            "OrderedCommand"
        }
    }

    // =========================================================================
    // CommandResult tests
    // =========================================================================

    #[test]
    fn command_result_success() {
        let result = CommandResult::success();
        assert!(result.is_success());
        assert!(!result.is_failure());
        assert!(result.message.is_none());
        assert!(result.data.is_none());
    }

    #[test]
    fn command_result_success_with_message() {
        let result = CommandResult::success_with_message("Done!");
        assert!(result.is_success());
        assert_eq!(result.message, Some("Done!".to_string()));
    }

    #[test]
    fn command_result_success_with_data() {
        let result = CommandResult::success_with_data(serde_json::json!({"key": "value"}));
        assert!(result.is_success());
        assert_eq!(result.data, Some(serde_json::json!({"key": "value"})));
    }

    #[test]
    fn command_result_failure() {
        let result = CommandResult::failure("Something went wrong");
        assert!(result.is_failure());
        assert!(!result.is_success());
        assert_eq!(result.message, Some("Something went wrong".to_string()));
    }

    #[test]
    fn command_result_failure_with_data() {
        let result =
            CommandResult::failure_with_data("Error", serde_json::json!({"code": 42}));
        assert!(result.is_failure());
        assert_eq!(result.message, Some("Error".to_string()));
        assert_eq!(result.data, Some(serde_json::json!({"code": 42})));
    }

    #[test]
    fn command_result_serialization() {
        let result = CommandResult::success_with_data(serde_json::json!({"x": 1}));
        let json = serde_json::to_string(&result).expect("serialize");
        let parsed: CommandResult = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(result.success, parsed.success);
        assert_eq!(result.data, parsed.data);
    }

    // =========================================================================
    // CommandQueue tests
    // =========================================================================

    #[test]
    fn command_queue_push_and_pop() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        assert!(queue.is_empty());
        assert_eq!(queue.len(), 0);

        queue.push(Box::new(SuccessCommand), user_id);
        assert!(!queue.is_empty());
        assert_eq!(queue.len(), 1);

        queue.push(Box::new(FailureCommand), user_id);
        assert_eq!(queue.len(), 2);

        let cmd1 = queue.pop().expect("pop first");
        assert_eq!(cmd1.command.name(), "SuccessCommand");
        assert_eq!(cmd1.user_id, user_id);
        assert_eq!(queue.len(), 1);

        let cmd2 = queue.pop().expect("pop second");
        assert_eq!(cmd2.command.name(), "FailureCommand");
        assert!(queue.is_empty());

        assert!(queue.pop().is_none());
    }

    #[test]
    fn execute_all_runs_commands_in_order() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();
        let order = std::sync::Arc::new(std::sync::Mutex::new(Vec::new()));

        // Queue commands in order 1, 2, 3
        for i in 1..=3 {
            queue.push(
                Box::new(OrderedCommand {
                    id: i,
                    order: order.clone(),
                }),
                user_id,
            );
        }

        let mut world = MockWorld::new();
        let match_id = MatchId::new();

        let results = queue.execute_all(&mut world, match_id);

        assert_eq!(results.len(), 3);
        assert!(results.iter().all(|r| r.is_success()));

        // Verify execution order
        let executed = order.lock().unwrap();
        assert_eq!(*executed, vec![1, 2, 3]);

        // Queue should be empty
        assert!(queue.is_empty());
    }

    #[test]
    fn command_result_success_and_failure() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        queue.push(Box::new(SuccessCommand), user_id);
        queue.push(Box::new(FailureCommand), user_id);
        queue.push(Box::new(SuccessCommand), user_id);

        let mut world = MockWorld::new();
        let match_id = MatchId::new();

        let results = queue.execute_all(&mut world, match_id);

        assert_eq!(results.len(), 3);
        assert!(results[0].is_success());
        assert!(results[1].is_failure());
        assert!(results[2].is_success());
    }

    #[test]
    fn command_error_becomes_failure_result() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        queue.push(Box::new(ErrorCommand), user_id);

        let mut world = MockWorld::new();
        let match_id = MatchId::new();

        let results = queue.execute_all(&mut world, match_id);

        assert_eq!(results.len(), 1);
        assert!(results[0].is_failure());
        assert!(results[0].message.as_ref().unwrap().contains("Internal error"));
    }

    #[test]
    fn command_queue_clear() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        queue.push(Box::new(SuccessCommand), user_id);
        queue.push(Box::new(SuccessCommand), user_id);
        assert_eq!(queue.len(), 2);

        queue.clear();
        assert!(queue.is_empty());
    }

    #[test]
    fn command_queue_with_capacity() {
        let queue = CommandQueue::with_capacity(100);
        assert!(queue.is_empty());
    }

    #[test]
    fn queued_command_time_in_queue() {
        let cmd = QueuedCommand::new(Box::new(SuccessCommand), UserId::new());
        std::thread::sleep(std::time::Duration::from_millis(10));
        assert!(cmd.time_in_queue() >= std::time::Duration::from_millis(10));
    }

    // =========================================================================
    // SpawnEntityCommand tests
    // =========================================================================

    #[test]
    fn spawn_entity_command_works() {
        let cmd = SpawnEntityCommand::new();
        let mut world = MockWorld::with_tick(42);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 42);

        let result = cmd.execute(&mut ctx).expect("execute");

        assert!(result.is_success());
        assert!(result.data.is_some());

        // Verify entity was created
        let data = result.data.unwrap();
        let entity_id = data["entity_id"].as_u64().expect("entity_id");
        assert!(world.entity_exists(EntityId(entity_id)));
    }

    #[test]
    fn spawn_entity_command_increments_ids() {
        let cmd = SpawnEntityCommand::new();
        let mut world = MockWorld::new();
        let match_id = MatchId::new();
        let user_id = UserId::new();

        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);
        let result1 = cmd.execute(&mut ctx).expect("execute 1");

        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);
        let result2 = cmd.execute(&mut ctx).expect("execute 2");

        let id1 = result1.data.unwrap()["entity_id"].as_u64().unwrap();
        let id2 = result2.data.unwrap()["entity_id"].as_u64().unwrap();

        assert_ne!(id1, id2);
        assert_eq!(id2, id1 + 1);
    }

    // =========================================================================
    // DespawnEntityCommand tests
    // =========================================================================

    #[test]
    fn despawn_entity_command_works() {
        let mut world = MockWorld::new();
        let entity_id = world.spawn_entity();
        assert!(world.entity_exists(entity_id));

        let cmd = DespawnEntityCommand::new(entity_id);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");

        assert!(result.is_success());
        assert!(!world.entity_exists(entity_id));

        // Verify result data
        let data = result.data.expect("data");
        assert_eq!(data["entity_id"].as_u64().unwrap(), entity_id.0);
    }

    #[test]
    fn despawn_entity_command_fails_for_nonexistent() {
        let mut world = MockWorld::new();
        let fake_id = EntityId(9999);

        let cmd = DespawnEntityCommand::new(fake_id);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");

        assert!(result.is_failure());
        assert!(result.message.unwrap().contains("does not exist"));
    }

    #[test]
    fn spawn_and_despawn_integration() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        // Spawn an entity
        queue.push(Box::new(SpawnEntityCommand::new()), user_id);

        let mut world = MockWorld::new();
        let match_id = MatchId::new();

        let results = queue.execute_all(&mut world, match_id);
        assert_eq!(results.len(), 1);
        assert!(results[0].is_success());

        let entity_id = EntityId(
            results[0].data.as_ref().unwrap()["entity_id"]
                .as_u64()
                .unwrap(),
        );
        assert!(world.entity_exists(entity_id));

        // Despawn the entity
        queue.push(Box::new(DespawnEntityCommand::new(entity_id)), user_id);

        let results = queue.execute_all(&mut world, match_id);
        assert_eq!(results.len(), 1);
        assert!(results[0].is_success());
        assert!(!world.entity_exists(entity_id));
    }

    // =========================================================================
    // CommandContext tests
    // =========================================================================

    #[test]
    fn command_context_provides_correct_values() {
        let mut world = MockWorld::with_tick(100);
        let match_id = MatchId::new();
        let user_id = UserId::new();

        let ctx = CommandContext::new(&mut world, match_id, user_id, 100);

        assert_eq!(ctx.match_id, match_id);
        assert_eq!(ctx.user_id, user_id);
        assert_eq!(ctx.tick, 100);
        assert_eq!(ctx.world.current_tick(), 100);
    }

    // =========================================================================
    // Command trait tests
    // =========================================================================

    #[test]
    fn command_name_returns_correct_value() {
        let spawn = SpawnEntityCommand::new();
        assert_eq!(spawn.name(), "SpawnEntityCommand");

        let despawn = DespawnEntityCommand::new(EntityId(1));
        assert_eq!(despawn.name(), "DespawnEntityCommand");
    }
}
