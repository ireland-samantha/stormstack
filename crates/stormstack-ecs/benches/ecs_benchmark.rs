//! ECS performance benchmarks.
//!
//! Target: ≥746 ticks/sec with 10k entities

use criterion::{black_box, criterion_group, criterion_main, Criterion};
use stormstack_ecs::{EcsWorld, StormWorld};

fn bench_tick_10k_entities(c: &mut Criterion) {
    let mut world = StormWorld::new();

    // Spawn 10k entities
    for _ in 0..10_000 {
        world.spawn();
    }

    c.bench_function("tick_10k_entities", |b| {
        b.iter(|| {
            world.advance(black_box(0.016)).unwrap();
        })
    });
}

fn bench_spawn_entities(c: &mut Criterion) {
    c.bench_function("spawn_1000_entities", |b| {
        b.iter(|| {
            let mut world = StormWorld::new();
            for _ in 0..1000 {
                black_box(world.spawn());
            }
        })
    });
}

fn bench_snapshot_10k(c: &mut Criterion) {
    let mut world = StormWorld::new();

    for _ in 0..10_000 {
        world.spawn();
    }

    c.bench_function("snapshot_10k_entities", |b| {
        b.iter(|| {
            black_box(world.snapshot().unwrap());
        })
    });
}

/// Benchmark delta generation with 10k entities - critical for WebSocket streaming performance.
///
/// This tests the ChangeTracker's ability to efficiently track and report changes
/// across many entities, which directly impacts the performance of real-time game state
/// synchronization.
fn bench_delta_10k_entities(c: &mut Criterion) {
    let mut world = StormWorld::new();

    // Spawn 10k entities at tick 0
    for _ in 0..10_000 {
        world.spawn();
    }

    // Advance to tick 1 so all spawns are recorded
    world.advance(0.016).unwrap();

    c.bench_function("delta_10k_entities", |b| {
        b.iter(|| {
            // Generate delta from tick 0 to current
            black_box(world.delta_since(0).unwrap());
        })
    });
}

/// Benchmark delta generation after mixed operations (spawns, despawns, updates).
///
/// This simulates a more realistic scenario where entities are being created
/// and destroyed during gameplay.
fn bench_delta_mixed_operations(c: &mut Criterion) {
    let mut world = StormWorld::new();

    // Spawn initial entities
    let mut entities = Vec::new();
    for _ in 0..5_000 {
        entities.push(world.spawn());
    }
    world.advance(0.016).unwrap();

    // Despawn half and spawn new ones
    for entity in entities.iter().take(2_500) {
        world.despawn(*entity).unwrap();
    }
    for _ in 0..2_500 {
        world.spawn();
    }
    world.advance(0.016).unwrap();

    c.bench_function("delta_mixed_5k_ops", |b| {
        b.iter(|| {
            // Generate delta from tick 0 to current
            black_box(world.delta_since(0).unwrap());
        })
    });
}

/// Benchmark cleanup_history performance with 10k entities across many ticks.
///
/// This is critical for long-running servers where change history must be periodically
/// cleaned to prevent unbounded memory growth (a DoS vector). Tests how long it takes
/// to prune old change records.
fn bench_cleanup_10k_entities(c: &mut Criterion) {
    c.bench_function("cleanup_10k_entities_100_ticks", |b| {
        b.iter_batched(
            || {
                // Setup: create world with 10k entities spawned across 100 ticks
                let mut world = StormWorld::new();
                for _tick in 0..100 {
                    for _ in 0..100 {
                        world.spawn();
                    }
                    world.advance(0.016).unwrap();
                }
                world
            },
            |mut world| {
                // Benchmark: cleanup all history before current tick
                let current_tick = world.tick();
                black_box(world.cleanup_history(current_tick));
            },
            criterion::BatchSize::SmallInput,
        );
    });
}

criterion_group!(
    benches,
    bench_tick_10k_entities,
    bench_spawn_entities,
    bench_snapshot_10k,
    bench_delta_10k_entities,
    bench_delta_mixed_operations,
    bench_cleanup_10k_entities
);
criterion_main!(benches);
