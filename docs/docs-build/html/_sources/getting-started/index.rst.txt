===============
Getting Started
===============

Welcome to StormStack! This guide will help you get up and running quickly.

StormStack is a production-grade multi-match game server framework. It consists of:

* **Thunder** - Backend services (Engine, Auth, Control Plane)
* **Lightning** - Client tools (CLI, Web Panel, Rendering Engine)

What You'll Learn
-----------------

In this section, you'll learn how to:

1. Install prerequisites and set up your environment
2. Start the StormStack services using Docker Compose
3. Deploy your first game match using the Lightning CLI

Time Required
-------------

* **Installation**: 5-10 minutes
* **Quickstart**: 5 minutes

Prerequisites Overview
----------------------

Before you begin, you'll need:

* Docker and Docker Compose (for running services)
* Go 1.24+ (for building the Lightning CLI)

See :doc:`installation` for detailed requirements.

.. toctree::
   :maxdepth: 2
   :caption: Contents:

   installation
   quickstart

Next Steps
----------

After completing the quickstart, continue to:

* :doc:`/tutorials/index` - Step-by-step tutorials for building games
* :doc:`/concepts/index` - Core concepts like ECS, containers, and modules
* :doc:`/lightning/cli/index` - Full CLI command reference
