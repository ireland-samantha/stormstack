package com.lightningfirefly.game.renderering;

public class TestRendererApplication {
    static void main() {
        GameRenderer renderer =
                GameRendererBuilder.create()
                     .windowSize(800, 600)
                     .title("My Game")
//                     .controlSystem()
//                     .spriteMapper(snapshot -> convertToSprites(snapshot))
                     .build();

        renderer.start(() -> {});
    }
}
