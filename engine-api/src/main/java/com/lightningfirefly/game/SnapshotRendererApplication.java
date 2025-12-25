package com.lightningfirefly.game;

import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import com.lightningfirefly.game.renderering.GameRenderer;
import com.lightningfirefly.game.renderering.GameRendererBuilder;

public class SnapshotRendererApplication {
    static void main() {
        GameRenderer renderer =
                GameRendererBuilder.create()
                     .windowSize(800, 600)
                     .title("Snapshot Renderer")
//                     .controlSystem()
                        .spriteMapper(new SpriteSnapshotMapperImpl())
                     .build();

        renderer.start(() -> {});
    }
}
