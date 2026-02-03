==========
ECS Basics
==========

.. note::

   This page is under development.

The Entity-Component-System (ECS) architecture is at the heart of StormStack's game engine.

Overview
--------

ECS separates data (Components) from behavior (Systems), with Entities serving as identifiers that link components together.

**Entities**
   Unique identifiers (integers) representing game objects.

**Components**
   Pure data attached to entities (Position, Velocity, Health, etc.).

**Systems**
   Logic that operates on entities with specific component combinations.

For more details, see the :doc:`/tutorials/hello-stormstack` tutorial.
