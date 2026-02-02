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

criterion_group!(
    benches,
    bench_tick_10k_entities,
    bench_spawn_entities,
    bench_snapshot_10k
);
criterion_main!(benches);
