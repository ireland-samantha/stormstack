//! WASM Sandbox implementation.
//!
//! SECURITY CRITICAL: This module executes untrusted code.
//!
//! ## Security Model
//!
//! - All WASM execution is fuel-limited
//! - Epoch interruption provides wall-clock timeout backup
//! - Memory limits prevent exhaustion attacks
//! - Zero capabilities by default
//! - All inputs validated

use crate::limits::WasmResourceLimits;
use stormstack_core::WasmError;
use tracing::debug;
use wasmtime::{Config, Engine, Linker, Module, Store, StoreLimits, StoreLimitsBuilder, Val};

/// Compiled WASM module ready for instantiation.
pub struct WasmModule {
    module: Module,
    name: String,
}

impl WasmModule {
    /// Get the module name.
    #[must_use]
    pub fn name(&self) -> &str {
        &self.name
    }
}

/// State stored in the wasmtime Store.
pub struct StoreState {
    /// Resource limiter for memory/tables.
    pub limits: StoreLimits,
}

/// Instantiated WASM module with execution state.
pub struct WasmInstance {
    store: Store<StoreState>,
    instance: wasmtime::Instance,
    initial_fuel: u64,
}

impl WasmInstance {
    /// Get the amount of fuel consumed so far.
    #[must_use]
    pub fn fuel_consumed(&self) -> u64 {
        self.initial_fuel.saturating_sub(self.store.get_fuel().unwrap_or(0))
    }

    /// Get remaining fuel.
    #[must_use]
    pub fn fuel_remaining(&self) -> u64 {
        self.store.get_fuel().unwrap_or(0)
    }
}

/// Configuration for the WASM sandbox.
#[derive(Debug, Clone)]
pub struct SandboxConfig {
    /// Enable fuel metering.
    pub fuel_enabled: bool,
    /// Enable epoch interruption.
    pub epoch_enabled: bool,
    /// Enable WASM multi-memory proposal.
    pub multi_memory: bool,
    /// Enable WASM SIMD.
    pub simd: bool,
}

impl Default for SandboxConfig {
    fn default() -> Self {
        Self {
            fuel_enabled: true,
            epoch_enabled: true,
            multi_memory: false,
            simd: true,
        }
    }
}

/// WASM sandbox for secure execution of untrusted modules.
///
/// # Security Model
///
/// The sandbox provides:
/// - Complete memory isolation via WASM linear memory
/// - Capability-based security (zero capabilities by default)
/// - Resource limits (fuel, memory, time)
/// - Host function input validation
///
/// # Example
///
/// ```ignore
/// let sandbox = WasmSandbox::new()?;
/// let module = sandbox.load_module(wasm_bytes)?;
/// let mut instance = sandbox.instantiate(&module, WasmResourceLimits::default())?;
/// let result = sandbox.execute(&mut instance, "main", &[])?;
/// ```
pub struct WasmSandbox {
    engine: Engine,
    linker: Linker<StoreState>,
    config: SandboxConfig,
    /// Background thread handle for epoch incrementer.
    _epoch_thread: Option<std::thread::JoinHandle<()>>,
}

impl WasmSandbox {
    /// Create a new WASM sandbox with default configuration.
    ///
    /// # Errors
    ///
    /// Returns an error if the WASM engine fails to initialize.
    pub fn new() -> Result<Self, WasmError> {
        Self::with_config(SandboxConfig::default())
    }

    /// Create a new WASM sandbox with custom configuration.
    ///
    /// # Errors
    ///
    /// Returns an error if the WASM engine fails to initialize.
    pub fn with_config(sandbox_config: SandboxConfig) -> Result<Self, WasmError> {
        let mut config = Config::new();

        // SECURITY: Enable fuel metering for instruction limiting
        if sandbox_config.fuel_enabled {
            config.consume_fuel(true);
        }

        // SECURITY: Enable epoch interruption for wall-clock timeout
        if sandbox_config.epoch_enabled {
            config.epoch_interruption(true);
        }

        // Feature configuration
        config.wasm_simd(sandbox_config.simd);
        config.wasm_multi_memory(sandbox_config.multi_memory);

        // Disable features that could be security risks
        config.wasm_threads(false); // No shared memory/atomics

        let engine = Engine::new(&config).map_err(|e| {
            WasmError::CompilationError(format!("Failed to create engine: {e}"))
        })?;

        // Start epoch incrementer thread if enabled
        let epoch_thread = if sandbox_config.epoch_enabled {
            let engine_clone = engine.clone();
            Some(std::thread::spawn(move || {
                loop {
                    std::thread::sleep(std::time::Duration::from_millis(10));
                    engine_clone.increment_epoch();
                }
            }))
        } else {
            None
        };

        let linker = Linker::new(&engine);

        Ok(Self {
            engine,
            linker,
            config: sandbox_config,
            _epoch_thread: epoch_thread,
        })
    }

    /// Get a reference to the engine.
    #[must_use]
    pub fn engine(&self) -> &Engine {
        &self.engine
    }

    /// Load a WASM module from bytes.
    ///
    /// # Errors
    ///
    /// Returns `WasmError::CompilationError` if the module is invalid.
    pub fn load_module(&self, wasm_bytes: &[u8]) -> Result<WasmModule, WasmError> {
        self.load_module_named(wasm_bytes, "unnamed")
    }

    /// Load a WASM module from bytes with a name.
    ///
    /// # Errors
    ///
    /// Returns `WasmError::CompilationError` if the module is invalid.
    pub fn load_module_named(
        &self,
        wasm_bytes: &[u8],
        name: &str,
    ) -> Result<WasmModule, WasmError> {
        debug!(name, bytes_len = wasm_bytes.len(), "Loading WASM module");

        let module = Module::new(&self.engine, wasm_bytes).map_err(|e| {
            WasmError::CompilationError(format!("Failed to compile module: {e}"))
        })?;

        Ok(WasmModule {
            module,
            name: name.to_string(),
        })
    }

    /// Instantiate a module with resource limits.
    ///
    /// # Errors
    ///
    /// Returns `WasmError::InstantiationError` if instantiation fails.
    pub fn instantiate(
        &self,
        module: &WasmModule,
        limits: WasmResourceLimits,
    ) -> Result<WasmInstance, WasmError> {
        debug!(
            module = %module.name,
            max_fuel = limits.max_fuel,
            max_memory = limits.max_memory_bytes,
            "Instantiating WASM module"
        );

        // Create store limits
        let store_limits = StoreLimitsBuilder::new()
            .memory_size(limits.max_memory_bytes)
            .table_elements(limits.max_table_elements as usize)
            .instances(limits.max_instances as usize)
            .tables(limits.max_tables as usize)
            .memories(limits.max_memories as usize)
            .build();

        let state = StoreState {
            limits: store_limits,
        };

        let mut store = Store::new(&self.engine, state);

        // SECURITY: Set fuel limit
        if self.config.fuel_enabled {
            store.set_fuel(limits.max_fuel).map_err(|e| {
                WasmError::InstantiationError(format!("Failed to set fuel: {e}"))
            })?;
        }

        // SECURITY: Set epoch deadline
        if self.config.epoch_enabled {
            store.epoch_deadline_trap();
            store.set_epoch_deadline(limits.epoch_deadline);
        }

        // SECURITY: Apply store limits
        store.limiter(|state| &mut state.limits);

        // Instantiate the module
        let instance =
            self.linker
                .instantiate(&mut store, &module.module)
                .map_err(|e| {
                    WasmError::InstantiationError(format!("Failed to instantiate: {e}"))
                })?;

        Ok(WasmInstance {
            store,
            instance,
            initial_fuel: limits.max_fuel,
        })
    }

    /// Execute a function in the WASM instance.
    ///
    /// # Errors
    ///
    /// Returns various `WasmError` variants:
    /// - `FuelExhausted` if instruction limit exceeded
    /// - `EpochDeadlineExceeded` if time limit exceeded
    /// - `Trap` if WASM execution traps
    /// - `FunctionNotFound` if function doesn't exist
    pub fn execute(
        &self,
        instance: &mut WasmInstance,
        func_name: &str,
        args: &[WasmValue],
    ) -> Result<Vec<WasmValue>, WasmError> {
        debug!(func_name, args_len = args.len(), "Executing WASM function");

        // Get the function
        let func = instance
            .instance
            .get_func(&mut instance.store, func_name)
            .ok_or_else(|| WasmError::FunctionNotFound(func_name.to_string()))?;

        // Convert arguments
        let params: Vec<Val> = args.iter().map(wasm_value_to_val).collect();

        // Prepare results buffer based on function type
        let func_type = func.ty(&instance.store);
        let mut results = vec![Val::I32(0); func_type.results().len()];

        // Execute the function
        func.call(&mut instance.store, &params, &mut results)
            .map_err(|e| self.trap_to_error(e, instance))?;

        // Convert results
        let output = results.into_iter().map(val_to_wasm_value).collect();

        Ok(output)
    }

    /// Convert a wasmtime trap/error to our error type.
    fn trap_to_error(&self, error: wasmtime::Error, instance: &WasmInstance) -> WasmError {
        let error_str = error.to_string();
        let error_lower = error_str.to_lowercase();

        // Check for fuel exhaustion (check string first, before downcast)
        if error_lower.contains("fuel") {
            return WasmError::FuelExhausted {
                consumed: instance.fuel_consumed(),
            };
        }

        // Check for epoch deadline
        if error_lower.contains("epoch") || error_lower.contains("interrupt") {
            return WasmError::EpochDeadlineExceeded;
        }

        // Check for memory issues
        if error_lower.contains("memory") && error_lower.contains("limit") {
            return WasmError::MemoryLimitExceeded {
                requested: 0,
                limit: 0,
            };
        }

        // Check for stack overflow
        if error_lower.contains("stack overflow") || error_lower.contains("call stack") {
            return WasmError::StackOverflow;
        }

        // Check for out of bounds
        if error_lower.contains("out of bounds") {
            return WasmError::Trap(format!("Memory access out of bounds: {error}"));
        }

        // Generic trap
        WasmError::Trap(error_str)
    }

    /// Get remaining fuel in an instance.
    #[must_use]
    pub fn fuel_remaining(&self, instance: &WasmInstance) -> u64 {
        instance.fuel_remaining()
    }

    /// Get memory usage of an instance in bytes.
    #[must_use]
    pub fn memory_usage(&self, instance: &mut WasmInstance) -> usize {
        instance
            .instance
            .get_memory(&mut instance.store, "memory")
            .map(|mem| mem.data_size(&instance.store))
            .unwrap_or(0)
    }
}

/// WASM value type for function arguments and returns.
#[derive(Debug, Clone, PartialEq)]
pub enum WasmValue {
    /// 32-bit integer
    I32(i32),
    /// 64-bit integer
    I64(i64),
    /// 32-bit float
    F32(f32),
    /// 64-bit float
    F64(f64),
}

fn wasm_value_to_val(v: &WasmValue) -> Val {
    match v {
        WasmValue::I32(x) => Val::I32(*x),
        WasmValue::I64(x) => Val::I64(*x),
        WasmValue::F32(x) => Val::F32(x.to_bits()),
        WasmValue::F64(x) => Val::F64(x.to_bits()),
    }
}

fn val_to_wasm_value(v: Val) -> WasmValue {
    match v {
        Val::I32(x) => WasmValue::I32(x),
        Val::I64(x) => WasmValue::I64(x),
        Val::F32(x) => WasmValue::F32(f32::from_bits(x)),
        Val::F64(x) => WasmValue::F64(f64::from_bits(x)),
        _ => WasmValue::I32(0), // Fallback for unsupported types
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // Helper to load WAT from file
    fn load_test_module(sandbox: &WasmSandbox, wat_content: &str) -> WasmModule {
        let wasm_bytes = wat::parse_str(wat_content).expect("Failed to parse WAT");
        sandbox
            .load_module(&wasm_bytes)
            .expect("Failed to load module")
    }

    const VALID_MODULE_WAT: &str = r#"
        (module
            (memory (export "memory") 1)
            (func $add (export "add") (param $a i32) (param $b i32) (result i32)
                (i32.add (local.get $a) (local.get $b))
            )
            (func $get_answer (export "get_answer") (result i32)
                (i32.const 42)
            )
            (func $factorial (export "factorial") (param $n i32) (result i32)
                (if (result i32) (i32.le_s (local.get $n) (i32.const 1))
                    (then (i32.const 1))
                    (else
                        (i32.mul
                            (local.get $n)
                            (call $factorial (i32.sub (local.get $n) (i32.const 1)))
                        )
                    )
                )
            )
        )
    "#;

    const INFINITE_LOOP_WAT: &str = r#"
        (module
            (func $loop_forever (export "loop_forever")
                (loop $infinite
                    (br $infinite)
                )
            )
        )
    "#;

    const MEMORY_BOMB_WAT: &str = r#"
        (module
            (memory (export "memory") 1)
            (func $allocate_max (export "allocate_max") (result i32)
                (memory.grow (i32.const 1000))
            )
        )
    "#;

    const STACK_OVERFLOW_WAT: &str = r#"
        (module
            (func $recurse (export "recurse") (param $depth i32) (result i32)
                (if (result i32) (i32.gt_s (local.get $depth) (i32.const 0))
                    (then
                        (call $recurse (i32.sub (local.get $depth) (i32.const 1)))
                    )
                    (else
                        (i32.const 0)
                    )
                )
            )
            (func $trigger_overflow (export "trigger_overflow") (result i32)
                (call $recurse (i32.const 1000000))
            )
        )
    "#;

    const FUEL_CONSUMER_WAT: &str = r#"
        (module
            (func $consume_fuel (export "consume_fuel") (param $iterations i32) (result i32)
                (local $counter i32)
                (local $result i32)
                (local.set $result (i32.const 0))
                (local.set $counter (i32.const 0))

                (block $break
                    (loop $loop
                        (br_if $break (i32.ge_u (local.get $counter) (local.get $iterations)))
                        (local.set $result (i32.add (local.get $result) (local.get $counter)))
                        (local.set $counter (i32.add (local.get $counter) (i32.const 1)))
                        (br $loop)
                    )
                )
                (local.get $result)
            )
            (func $minimal (export "minimal") (result i32)
                (i32.const 1)
            )
        )
    "#;

    // SECURITY TESTS - These MUST pass before any integration

    #[test]
    fn test_valid_module_executes() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, VALID_MODULE_WAT);
        let mut instance = sandbox
            .instantiate(&module, WasmResourceLimits::default())
            .expect("Failed to instantiate");

        // Test addition
        let result = sandbox
            .execute(&mut instance, "add", &[WasmValue::I32(2), WasmValue::I32(3)])
            .expect("Failed to execute");
        assert_eq!(result, vec![WasmValue::I32(5)]);

        // Test constant
        let result = sandbox
            .execute(&mut instance, "get_answer", &[])
            .expect("Failed to execute");
        assert_eq!(result, vec![WasmValue::I32(42)]);

        // Test factorial
        let result = sandbox
            .execute(&mut instance, "factorial", &[WasmValue::I32(5)])
            .expect("Failed to execute");
        assert_eq!(result, vec![WasmValue::I32(120)]); // 5! = 120
    }

    #[test]
    fn test_infinite_loop_terminated() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, INFINITE_LOOP_WAT);

        // Use minimal fuel to ensure quick termination
        let limits = WasmResourceLimits {
            max_fuel: 1000,
            epoch_deadline: 5,
            ..WasmResourceLimits::minimal()
        };

        let mut instance = sandbox
            .instantiate(&module, limits)
            .expect("Failed to instantiate");

        let result = sandbox.execute(&mut instance, "loop_forever", &[]);

        // Should fail - module should NOT be allowed to run forever
        // The specific error depends on which limit is hit first
        assert!(
            result.is_err(),
            "Infinite loop should have been terminated"
        );

        // Verify it's one of the expected security-related errors
        match result {
            Err(WasmError::FuelExhausted { .. }) => {}
            Err(WasmError::EpochDeadlineExceeded) => {}
            Err(WasmError::Trap(_)) => {
                // Also acceptable - traps from epoch interrupt or other limits
            }
            Err(e) => panic!("Unexpected error type: {e:?}"),
            Ok(_) => unreachable!(),
        }
    }

    #[test]
    fn test_fuel_exhausted() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, FUEL_CONSUMER_WAT);

        // Very limited fuel
        let limits = WasmResourceLimits {
            max_fuel: 100,
            epoch_deadline: 1000,
            ..WasmResourceLimits::default()
        };

        let mut instance = sandbox
            .instantiate(&module, limits)
            .expect("Failed to instantiate");

        // Try to run many iterations - should exhaust fuel
        let result = sandbox.execute(
            &mut instance,
            "consume_fuel",
            &[WasmValue::I32(10000)],
        );

        match result {
            Err(WasmError::FuelExhausted { consumed }) => {
                assert!(consumed > 0);
            }
            Ok(_) => panic!("Should have exhausted fuel"),
            Err(e) => panic!("Unexpected error: {e:?}"),
        }
    }

    #[test]
    fn test_memory_bomb_prevented() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, MEMORY_BOMB_WAT);

        // Strict memory limit - only 1MB
        let limits = WasmResourceLimits {
            max_memory_bytes: 1024 * 1024,
            ..WasmResourceLimits::default()
        };

        let mut instance = sandbox
            .instantiate(&module, limits)
            .expect("Failed to instantiate");

        // Try to allocate massive memory
        let result = sandbox.execute(&mut instance, "allocate_max", &[]);

        // memory.grow should return -1 when it fails
        match result {
            Ok(vals) => {
                if let Some(WasmValue::I32(v)) = vals.first() {
                    // -1 means allocation failed (which is what we want)
                    assert_eq!(*v, -1, "memory.grow should return -1 when limited");
                }
            }
            Err(WasmError::MemoryLimitExceeded { .. }) => {
                // Also acceptable - explicit limit error
            }
            Err(e) => panic!("Unexpected error: {e:?}"),
        }
    }

    #[test]
    fn test_stack_overflow_handled() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, STACK_OVERFLOW_WAT);

        let mut instance = sandbox
            .instantiate(&module, WasmResourceLimits::default())
            .expect("Failed to instantiate");

        let result = sandbox.execute(&mut instance, "trigger_overflow", &[]);

        // Should fail - deep recursion should NOT be allowed to succeed
        assert!(result.is_err(), "Stack overflow should have been caught");

        // Accept any error that indicates the deep recursion was blocked
        match result {
            Err(WasmError::StackOverflow) => {}
            Err(WasmError::FuelExhausted { .. }) => {
                // Deep recursion exhausts fuel before stack overflow
            }
            Err(WasmError::Trap(_)) => {
                // Generic trap from stack exhaustion or fuel
            }
            Err(e) => panic!("Unexpected error type: {e:?}"),
            Ok(_) => unreachable!(),
        }
    }

    #[test]
    fn test_function_not_found() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, VALID_MODULE_WAT);
        let mut instance = sandbox
            .instantiate(&module, WasmResourceLimits::default())
            .expect("Failed to instantiate");

        let result = sandbox.execute(&mut instance, "nonexistent", &[]);

        match result {
            Err(WasmError::FunctionNotFound(name)) => {
                assert_eq!(name, "nonexistent");
            }
            _ => panic!("Expected FunctionNotFound error"),
        }
    }

    #[test]
    fn test_fuel_tracking() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, FUEL_CONSUMER_WAT);

        let limits = WasmResourceLimits {
            max_fuel: 100000,
            ..WasmResourceLimits::default()
        };

        let mut instance = sandbox
            .instantiate(&module, limits)
            .expect("Failed to instantiate");

        // Run minimal function
        sandbox
            .execute(&mut instance, "minimal", &[])
            .expect("Failed to execute");

        let consumed = instance.fuel_consumed();
        assert!(consumed > 0, "Should have consumed some fuel");
        assert!(
            consumed < limits.max_fuel,
            "Should not have exhausted all fuel"
        );
    }

    #[test]
    fn test_memory_usage_tracking() {
        let sandbox = WasmSandbox::new().expect("Failed to create sandbox");
        let module = load_test_module(&sandbox, VALID_MODULE_WAT);

        let mut instance = sandbox
            .instantiate(&module, WasmResourceLimits::default())
            .expect("Failed to instantiate");

        let usage = sandbox.memory_usage(&mut instance);

        // Module exports 1 page of memory = 64KB
        assert!(usage >= 65536, "Should have at least 64KB of memory");
    }

    #[test]
    fn sandbox_creation_succeeds() {
        let result = WasmSandbox::new();
        assert!(result.is_ok());
    }

    #[test]
    fn sandbox_with_custom_config() {
        let config = SandboxConfig {
            fuel_enabled: true,
            epoch_enabled: false,
            multi_memory: false,
            simd: true,
        };

        let result = WasmSandbox::with_config(config);
        assert!(result.is_ok());
    }
}
