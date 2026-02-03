===========================
Tutorial: Your First Module
===========================

In this tutorial, you'll create a HealthModule that adds HP tracking to entities.

Time Required: 30 minutes

Prerequisites
-------------

* Completed :doc:`hello-stormstack`
* Java 25 installed
* Maven 3.9+ installed

What You'll Learn
-----------------

* Module structure (Factory, Module, Components)
* Creating custom components
* Implementing systems that run every tick
* Defining commands for external interaction

Module Overview
---------------

Modules in StormStack define:

* **Components** - Data attached to entities (floats stored in the ECS)
* **Systems** - Logic that runs every tick
* **Commands** - External API for modifying game state

We'll create a HealthModule with:

* ``MAX_HP`` component - Maximum health
* ``CURRENT_HP`` component - Current health
* A system that removes entities when HP reaches 0
* A ``damage`` command to reduce HP

Part 1: Project Setup
---------------------

Create Module Project
~~~~~~~~~~~~~~~~~~~~~

Create a new Maven project:

.. code-block:: bash

   mkdir health-module
   cd health-module

Create ``pom.xml``:

.. code-block:: xml

   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
            http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>

       <groupId>com.example</groupId>
       <artifactId>health-module</artifactId>
       <version>1.0.0</version>
       <packaging>jar</packaging>

       <properties>
           <maven.compiler.source>25</maven.compiler.source>
           <maven.compiler.target>25</maven.compiler.target>
       </properties>

       <dependencies>
           <dependency>
               <groupId>ca.samanthaireland.stormstack</groupId>
               <artifactId>thunder-engine-core</artifactId>
               <version>1.0.0</version>
               <scope>provided</scope>
           </dependency>
       </dependencies>
   </project>

.. note::

   The ``provided`` scope means the engine provides this dependency at runtime.
   Your JAR won't include it.

Part 2: Create Components
-------------------------

Components are float values attached to entities. Each component needs a unique ID.

Create ``src/main/java/com/example/health/HealthModuleFactory.java``:

.. code-block:: java

   package com.example.health;

   import ca.samanthaireland.stormstack.thunder.engine.core.ext.module.*;

   public class HealthModuleFactory implements ModuleFactory {

       // Component definitions with unique IDs
       public static final BaseComponent MAX_HP =
           new BaseComponent(IdGeneratorV2.newId(), "MAX_HP") {};

       public static final BaseComponent CURRENT_HP =
           new BaseComponent(IdGeneratorV2.newId(), "CURRENT_HP") {};

       @Override
       public EngineModule create(ModuleContext context) {
           return new HealthModule(context);
       }
   }

Part 3: Create the Module
-------------------------

Create ``src/main/java/com/example/health/HealthModule.java``:

.. code-block:: java

   package com.example.health;

   import ca.samanthaireland.stormstack.thunder.engine.core.ext.module.*;
   import ca.samanthaireland.stormstack.thunder.engine.core.command.*;
   import ca.samanthaireland.stormstack.thunder.engine.core.store.*;
   import java.util.List;
   import java.util.Map;

   import static com.example.health.HealthModuleFactory.*;

   public class HealthModule implements EngineModule {

       private final ModuleContext context;

       public HealthModule(ModuleContext context) {
           this.context = context;
       }

       @Override
       public String getName() {
           return "HealthModule";
       }

       @Override
       public String getVersion() {
           return "1.0.0";
       }

       @Override
       public List<BaseComponent> createComponents() {
           return List.of(MAX_HP, CURRENT_HP);
       }

       @Override
       public List<EngineSystem> createSystems() {
           return List.of(this::deathSystem);
       }

       @Override
       public List<EngineCommand> createCommands() {
           return List.of(createDamageCommand());
       }

       // System: Remove entities with HP <= 0
       private void deathSystem() {
           EntityComponentStore store = context.getEntityComponentStore();

           for (long entity : store.getEntitiesWithComponents(CURRENT_HP)) {
               float hp = store.getComponent(entity, CURRENT_HP);
               if (hp <= 0) {
                   store.deleteEntity(entity);
               }
           }
       }

       // Command: Apply damage to an entity
       private EngineCommand createDamageCommand() {
           return CommandBuilder.newCommand()
               .withName("damage")
               .withSchema(Map.of(
                   "entityId", Long.class,
                   "amount", Float.class
               ))
               .withExecution(payload -> {
                   long entityId = ((Number) payload.getPayload()
                       .get("entityId")).longValue();
                   float amount = ((Number) payload.getPayload()
                       .get("amount")).floatValue();

                   EntityComponentStore store = context.getEntityComponentStore();

                   if (store.hasComponent(entityId, CURRENT_HP)) {
                       float current = store.getComponent(entityId, CURRENT_HP);
                       store.attachComponent(entityId, CURRENT_HP, current - amount);
                   }
               })
               .build();
       }
   }

Part 4: Register the Module
---------------------------

Create the service provider file for Java's ServiceLoader:

``src/main/resources/META-INF/services/ca.samanthaireland.stormstack.thunder.engine.core.ext.module.ModuleFactory``

.. code-block:: text

   com.example.health.HealthModuleFactory

Part 5: Build the Module
------------------------

.. code-block:: bash

   mvn clean package

This creates ``target/health-module-1.0.0.jar``.

Part 6: Deploy the Module
-------------------------

Upload to Control Plane
~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   lightning module upload HealthModule 1.0.0 ./target/health-module-1.0.0.jar

Distribute to Nodes
~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   lightning module distribute HealthModule 1.0.0

Part 7: Test the Module
-----------------------

Create a Match with Your Module
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   lightning deploy --modules EntityModule,HealthModule

Set Context
~~~~~~~~~~~

.. code-block:: bash

   lightning node context match <your-match-id>

Spawn an Entity with Health
~~~~~~~~~~~~~~~~~~~~~~~~~~~

First, we need to spawn an entity and give it health. Let's extend the spawn command:

.. code-block:: bash

   # Spawn entity
   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

   # Advance tick
   lightning node simulation tick

   # Check snapshot to get entity ID
   lightning snapshot get -o json

Initialize Health
~~~~~~~~~~~~~~~~~

After spawning, set the entity's health (assuming entity ID is 1):

.. code-block:: bash

   # This would require a setHealth command - for now, we'll test damage
   # In a real game, spawn would initialize health

Test the Damage Command
~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Apply 25 damage to entity 1
   lightning command send damage '{"entityId":1,"amount":25.0}'

   # Advance tick
   lightning node simulation tick

   # Check HP (should be reduced)
   lightning snapshot get -o json

Test Death System
~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Apply lethal damage
   lightning command send damage '{"entityId":1,"amount":1000.0}'

   # Advance tick (death system runs)
   lightning node simulation tick

   # Entity should be gone
   lightning snapshot get -o json

Part 8: Enhance the Module
--------------------------

Here are some ideas to extend your module:

Add a Heal Command
~~~~~~~~~~~~~~~~~~

.. code-block:: java

   private EngineCommand createHealCommand() {
       return CommandBuilder.newCommand()
           .withName("heal")
           .withSchema(Map.of(
               "entityId", Long.class,
               "amount", Float.class
           ))
           .withExecution(payload -> {
               // Similar to damage, but add HP
               // Don't exceed MAX_HP
           })
           .build();
   }

Add a Regeneration System
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: java

   private void regenSystem() {
       EntityComponentStore store = context.getEntityComponentStore();

       for (long entity : store.getEntitiesWithComponents(CURRENT_HP, MAX_HP)) {
           float current = store.getComponent(entity, CURRENT_HP);
           float max = store.getComponent(entity, MAX_HP);

           // Regenerate 1 HP per tick, up to max
           if (current < max) {
               store.attachComponent(entity, CURRENT_HP,
                   Math.min(current + 1, max));
           }
       }
   }

Key Concepts Reviewed
---------------------

* **ModuleFactory** - Creates module instances, defines components
* **EngineModule** - The module implementation with systems and commands
* **BaseComponent** - Float data attached to entities
* **EngineSystem** - Logic that runs every tick
* **EngineCommand** - External API with schema validation
* **ModuleContext** - Provides access to ECS store and other dependencies

Next Steps
----------

You've created your first module! Here's where to go next:

* :doc:`/concepts/modules` - Module system deep dive
* :doc:`/concepts/ecs-basics` - ECS fundamentals
* :doc:`/thunder/engine/commands` - Advanced command patterns
* :doc:`/architecture/ecs-internals` - How the ECS store works under the hood
