//! Module trait for defining the module lifecycle interface.
//!
//! This module defines the core `Module` trait that all native modules
//! must implement to be loadable by the module system.

use stormstack_core::Result;
use stormstack_ecs::StormWorld;

use crate::ModuleDescriptor;

/// Context provided to modules during lifecycle callbacks.
///
/// This struct provides access to shared resources that modules
/// may need during initialization and tick processing.
pub struct ModuleContext<'a> {
    /// Access to the ECS world for entity/component operations.
    pub world: &'a mut StormWorld,
    /// Current tick number.
    pub tick: u64,
    /// Delta time since last tick in seconds.
    pub delta_time: f64,
}

impl<'a> ModuleContext<'a> {
    /// Create a new module context.
    pub fn new(world: &'a mut StormWorld, tick: u64, delta_time: f64) -> Self {
        Self {
            world,
            tick,
            delta_time,
        }
    }
}

/// Trait that all native modules must implement.
///
/// # Safety
///
/// This trait is for **TRUSTED** code only. Native modules have full
/// system access and can execute arbitrary code. For untrusted code,
/// use the WASM sandbox in `stormstack-wasm`.
///
/// # ABI Requirements
///
/// Modules must be compiled with the same Rust version and compiler
/// settings as the host application. The `#[repr(C)]` attribute should
/// be used for any types passed across the module boundary.
///
/// # Example
///
/// ```ignore
/// use stormstack_modules::{Module, ModuleContext, ModuleDescriptor};
///
/// pub struct MyModule;
///
/// impl Module for MyModule {
///     fn descriptor(&self) -> &'static ModuleDescriptor {
///         static DESC: ModuleDescriptor = ModuleDescriptor::new(
///             "my-module",
///             "1.0.0",
///             "My game module",
///         );
///         &DESC
///     }
///
///     fn on_load(&mut self, ctx: &mut ModuleContext) -> stormstack_core::Result<()> {
///         println!("Module loaded!");
///         Ok(())
///     }
///
///     fn on_tick(&mut self, ctx: &mut ModuleContext) -> stormstack_core::Result<()> {
///         // Process game logic
///         Ok(())
///     }
///
///     fn on_unload(&mut self) -> stormstack_core::Result<()> {
///         println!("Module unloaded!");
///         Ok(())
///     }
/// }
///
/// // Export the module creation function
/// #[no_mangle]
/// pub extern "C" fn _stormstack_module_create() -> *mut dyn Module {
///     Box::into_raw(Box::new(MyModule))
/// }
/// ```
pub trait Module: Send + Sync {
    /// Get the module's descriptor.
    ///
    /// This provides metadata about the module including name, version,
    /// and dependencies.
    fn descriptor(&self) -> &'static ModuleDescriptor;

    /// Called when the module is loaded.
    ///
    /// Use this to initialize module state, register components,
    /// or set up resources. The context provides access to the ECS world.
    ///
    /// # Errors
    ///
    /// Return an error if initialization fails. The module will not
    /// be considered loaded if this returns an error.
    fn on_load(&mut self, ctx: &mut ModuleContext) -> Result<()>;

    /// Called on each game tick.
    ///
    /// Implement game logic here. This is called once per tick
    /// after all entity processing.
    ///
    /// # Errors
    ///
    /// Return an error if tick processing fails. The module may
    /// be unloaded if this returns too many errors.
    fn on_tick(&mut self, ctx: &mut ModuleContext) -> Result<()>;

    /// Called when the module is being unloaded.
    ///
    /// Clean up resources, save state, and prepare for unload.
    /// After this returns, the module will be dropped.
    ///
    /// # Errors
    ///
    /// Return an error if cleanup fails. The module will still
    /// be unloaded even if this returns an error.
    fn on_unload(&mut self) -> Result<()>;

    /// Called when another module is loaded.
    ///
    /// Modules can use this to integrate with dependencies.
    /// The default implementation does nothing.
    fn on_dependency_loaded(&mut self, _name: &str) -> Result<()> {
        Ok(())
    }

    /// Called when another module is about to be unloaded.
    ///
    /// Modules can use this to clean up integration with dependencies.
    /// The default implementation does nothing.
    fn on_dependency_unloading(&mut self, _name: &str) -> Result<()> {
        Ok(())
    }
}

/// Type signature for the module creation function.
///
/// All modules must export a function with this signature named
/// `_stormstack_module_create`. This function should allocate and
/// return a boxed module instance.
///
/// # Note on FFI Safety
///
/// This type uses a trait object which is not strictly FFI-safe.
/// This is intentional - modules MUST be compiled with the same
/// Rust version and compiler settings as the host. This is a
/// limitation of using dynamic dispatch across module boundaries.
#[allow(improper_ctypes_definitions)]
pub type ModuleCreateFn = unsafe extern "C" fn() -> *mut dyn Module;

/// Type signature for the module destruction function.
///
/// Modules may optionally export a function with this signature named
/// `_stormstack_module_destroy`. If not provided, the host will use
/// `Box::from_raw` to drop the module.
///
/// # Note on FFI Safety
///
/// This type uses a trait object which is not strictly FFI-safe.
/// This is intentional - modules MUST be compiled with the same
/// Rust version and compiler settings as the host.
#[allow(improper_ctypes_definitions)]
pub type ModuleDestroyFn = unsafe extern "C" fn(*mut dyn Module);

/// The expected symbol name for module creation.
pub const MODULE_CREATE_SYMBOL: &[u8] = b"_stormstack_module_create\0";

/// The expected symbol name for module destruction (optional).
pub const MODULE_DESTROY_SYMBOL: &[u8] = b"_stormstack_module_destroy\0";

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_ecs::StormWorld;

    struct TestModule {
        load_called: bool,
        tick_count: u64,
        unload_called: bool,
    }

    impl TestModule {
        fn new() -> Self {
            Self {
                load_called: false,
                tick_count: 0,
                unload_called: false,
            }
        }
    }

    impl Module for TestModule {
        fn descriptor(&self) -> &'static ModuleDescriptor {
            static DESC: ModuleDescriptor = ModuleDescriptor::new("test-module", "1.0.0", "Test");
            &DESC
        }

        fn on_load(&mut self, _ctx: &mut ModuleContext) -> Result<()> {
            self.load_called = true;
            Ok(())
        }

        fn on_tick(&mut self, _ctx: &mut ModuleContext) -> Result<()> {
            self.tick_count += 1;
            Ok(())
        }

        fn on_unload(&mut self) -> Result<()> {
            self.unload_called = true;
            Ok(())
        }
    }

    #[test]
    fn module_lifecycle() {
        let mut module = TestModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);

        assert!(!module.load_called);
        module.on_load(&mut ctx).unwrap();
        assert!(module.load_called);

        assert_eq!(module.tick_count, 0);
        module.on_tick(&mut ctx).unwrap();
        module.on_tick(&mut ctx).unwrap();
        assert_eq!(module.tick_count, 2);

        assert!(!module.unload_called);
        module.on_unload().unwrap();
        assert!(module.unload_called);
    }

    #[test]
    fn module_context_fields() {
        let mut world = StormWorld::new();
        let ctx = ModuleContext::new(&mut world, 42, 0.033);

        assert_eq!(ctx.tick, 42);
        assert!((ctx.delta_time - 0.033).abs() < f64::EPSILON);
    }

    #[test]
    fn module_descriptor_access() {
        let module = TestModule::new();
        let desc = module.descriptor();
        assert_eq!(desc.name, "test-module");
        assert_eq!(desc.version, "1.0.0");
    }
}
