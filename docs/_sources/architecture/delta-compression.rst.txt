=================
Delta Compression
=================

.. note::

   This page is under development.

StormStack uses delta compression to minimize bandwidth when streaming ECS snapshots.

How It Works
------------

1. **Dirty Tracking** - ``DirtyTrackingEntityComponentStore`` tracks which entities changed
2. **Set Operations** - Computes added, removed, and modified entities
3. **Columnar Diff** - Only changed component values are included

Delta Snapshot Format
---------------------

.. code-block:: json

   {
     "matchId": 1,
     "baseTick": 100,
     "currentTick": 101,
     "added": [{"id": 5, "components": {...}}],
     "removed": [3],
     "modified": [{"id": 1, "Position": {"x": 10.5}}]
   }

Key Source Files
----------------

- ``DeltaCompressionService.java`` - Delta algorithm
- ``DirtyTrackingEntityComponentStore.java`` - Change tracking
