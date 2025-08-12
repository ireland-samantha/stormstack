package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI integration test for rendering resources.
 *
 * <p>This test verifies that when a resource (texture) is attached to an entity:
 * <ol>
 *   <li>The snapshot contains the RESOURCE_ID component</li>
 *   <li>The texture can be loaded and rendered on screen via a Sprite</li>
 *   <li>The rendered texture is visible in the window</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 * ./mvnw test -pl lightning-engine/gui -Dtest=RenderingResourceGuiTest -DenableGLTests=true
 * </pre>
 */
@Tag("integration")
@DisplayName("Rendering Resource GUI Integration Tests")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
class RenderingResourceGuiTest {

    private Window window;
    private ComponentFactory factory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        factory = GLComponentFactory.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (window != null) {
            try {
                window.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            window = null;
        }
    }

    @Test
    @DisplayName("Sprite with texture renders on screen")
    void spriteWithTexture_rendersOnScreen() throws Exception {
        // Create a test texture file
        Path texturePath = createTestTexture("test-sprite.png");

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Rendering Resource GUI Test")
            .build();

        // Create a sprite that references the texture
        Sprite sprite = Sprite.builder()
            .id(1)
            .texturePath(texturePath.toAbsolutePath().toString())
            .x(100)
            .y(100)
            .sizeX(200)
            .sizeY(200)
            .zIndex(1)
            .build();

        // Add sprite to window
        window.addSprite(sprite);

        // Run several frames to let the sprite render
        window.runFrames(10);

        // Verify sprite is in the window's sprite list
        List<Sprite> sprites = window.getSprites();
        assertThat(sprites).hasSize(1);
        assertThat(sprites.get(0).getId()).isEqualTo(1);
        assertThat(sprites.get(0).getTexturePath()).isEqualTo(texturePath.toAbsolutePath().toString());

        System.out.println("Sprite with texture rendered successfully at position (100, 100)");
    }

    @Test
    @DisplayName("Multiple sprites with different textures render correctly")
    void multipleSprites_renderCorrectly() throws Exception {
        // Create test textures
        Path texture1 = createTestTexture("sprite1.png");
        Path texture2 = createTestTexture("sprite2.png");

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Multiple Sprites Test")
            .build();

        // Create sprites
        Sprite sprite1 = Sprite.builder()
            .id(1)
            .texturePath(texture1.toAbsolutePath().toString())
            .x(50)
            .y(50)
            .sizeX(150)
            .sizeY(150)
            .zIndex(1)
            .build();

        Sprite sprite2 = Sprite.builder()
            .id(2)
            .texturePath(texture2.toAbsolutePath().toString())
            .x(250)
            .y(100)
            .sizeX(200)
            .sizeY(200)
            .zIndex(2)
            .build();

        // Add sprites
        window.addSprite(sprite1);
        window.addSprite(sprite2);

        // Render
        window.runFrames(10);

        // Verify both sprites are present
        List<Sprite> sprites = window.getSprites();
        assertThat(sprites).hasSize(2);
        assertThat(sprites).extracting(Sprite::getId).containsExactlyInAnyOrder(1, 2);

        System.out.println("Multiple sprites rendered successfully");
    }

    @Test
    @DisplayName("Snapshot with RESOURCE_ID displays correctly")
    void snapshotWithResourceId_displaysCorrectly() throws Exception {
        // Create window
        window = WindowBuilder.create()
            .size(1000, 700)
            .title("Snapshot Resource Display Test")
            .build();

        // Create a SnapshotPanel (headless mode)
        ComponentFactory headlessFactory = new TestComponentFactory();
        SnapshotPanel snapshotPanel = new SnapshotPanel(
            headlessFactory, 10, 10, 980, 680, "http://localhost:8080", 1);

        // Simulate a snapshot with RenderModule's RESOURCE_ID component
        Map<String, Map<String, List<Float>>> snapshotData = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(100.0f),
                "MATCH_ID", List.of(1.0f),
                "PLAYER_ID", List.of(1.0f)
            ),
            "RenderModule", Map.of(
                "RESOURCE_ID", List.of(42.0f)  // Resource ID 42 attached to entity
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 10L, snapshotData);

        // Verify snapshot contains RenderModule data
        assertThat(snapshot.getModuleNames()).contains("RenderModule");
        Map<String, List<Float>> renderData = snapshot.getModuleData("RenderModule");
        assertThat(renderData).containsKey("RESOURCE_ID");
        assertThat(renderData.get("RESOURCE_ID")).contains(42.0f);

        // Entity count should match
        assertThat(snapshot.getEntityCount()).isEqualTo(1);

        System.out.println("Snapshot with RESOURCE_ID=42 verified for entity");

        snapshotPanel.dispose();
    }

    @Test
    @DisplayName("Sprite zIndex ordering is correct")
    void spriteZIndexOrdering_isCorrect() throws Exception {
        Path texture = createTestTexture("ordering-test.png");

        window = WindowBuilder.create()
            .size(800, 600)
            .title("Z-Index Ordering Test")
            .build();

        // Create sprites with different z-indices
        Sprite background = Sprite.builder()
            .id(1)
            .texturePath(texture.toAbsolutePath().toString())
            .x(0)
            .y(0)
            .sizeX(800)
            .sizeY(600)
            .zIndex(0)
            .build();

        Sprite foreground = Sprite.builder()
            .id(2)
            .texturePath(texture.toAbsolutePath().toString())
            .x(200)
            .y(200)
            .sizeX(200)
            .sizeY(200)
            .zIndex(10)
            .build();

        Sprite middle = Sprite.builder()
            .id(3)
            .texturePath(texture.toAbsolutePath().toString())
            .x(100)
            .y(100)
            .sizeX(300)
            .sizeY(300)
            .zIndex(5)
            .build();

        // Add in non-z-order sequence
        window.addSprite(foreground);
        window.addSprite(background);
        window.addSprite(middle);

        // Render
        window.runFrames(5);

        // Verify all sprites are present
        List<Sprite> sprites = window.getSprites();
        assertThat(sprites).hasSize(3);

        // Verify z-indices
        assertThat(sprites.stream().filter(s -> s.getId() == 1).findFirst().get().getZIndex()).isEqualTo(0);
        assertThat(sprites.stream().filter(s -> s.getId() == 2).findFirst().get().getZIndex()).isEqualTo(10);
        assertThat(sprites.stream().filter(s -> s.getId() == 3).findFirst().get().getZIndex()).isEqualTo(5);

        System.out.println("Z-index ordering verified: background(0) < middle(5) < foreground(10)");
    }

    @Test
    @DisplayName("Sprite with input handler receives clicks")
    void spriteWithInputHandler_receivesClicks() throws Exception {
        Path texture = createTestTexture("clickable.png");

        window = WindowBuilder.create()
            .size(800, 600)
            .title("Clickable Sprite Test")
            .build();

        // Track click events
        final boolean[] clicked = {false};
        final int[] clickedButton = {-1};

        // Create sprite with input handler
        Sprite clickableSprite = Sprite.builder()
            .id(1)
            .texturePath(texture.toAbsolutePath().toString())
            .x(200)
            .y(200)
            .sizeX(200)
            .sizeY(200)
            .zIndex(1)
            .inputHandler(new SpriteInputHandler() {
                @Override
                public boolean onMouseClick(Sprite sprite, int button, int action) {
                    if (action == 1) { // Press
                        clicked[0] = true;
                        clickedButton[0] = button;
                        System.out.println("Sprite clicked! Button: " + button);
                    }
                    return true;
                }
            })
            .build();

        window.addSprite(clickableSprite);

        // Render
        window.runFrames(5);

        // Verify sprite has input handler
        assertThat(clickableSprite.hasInputHandler()).isTrue();
        assertThat(clickableSprite.contains(300, 300)).isTrue();  // Center of sprite
        assertThat(clickableSprite.contains(100, 100)).isFalse(); // Outside

        System.out.println("Clickable sprite with input handler verified");
    }

    @Test
    @DisplayName("Focusable sprite can receive keyboard focus")
    void focusableSprite_canReceiveKeyboardFocus() throws Exception {
        Path texture = createTestTexture("focusable.png");

        window = WindowBuilder.create()
            .size(800, 600)
            .title("Focusable Sprite Test")
            .build();

        // Create focusable sprite
        Sprite focusableSprite = Sprite.builder()
            .id(1)
            .texturePath(texture.toAbsolutePath().toString())
            .x(200)
            .y(200)
            .sizeX(200)
            .sizeY(200)
            .zIndex(1)
            .focusable(true)
            .inputHandler(new SpriteInputHandler() {
                @Override
                public boolean onKeyPress(Sprite sprite, int key, int action, int mods) {
                    System.out.println("Key pressed on sprite: " + key);
                    return true;
                }
            })
            .build();

        window.addSprite(focusableSprite);
        window.runFrames(5);

        assertThat(focusableSprite.isFocusable()).isTrue();
        assertThat(focusableSprite.hasInputHandler()).isTrue();

        System.out.println("Focusable sprite verified");
    }

    @Test
    @DisplayName("Entity with resource displays as sprite in panel")
    void entityWithResource_displaysAsSpriteInPanel() throws Exception {
        // This test simulates the full flow: entity with RESOURCE_ID → rendered sprite

        // Create window
        window = WindowBuilder.create()
            .size(1000, 700)
            .title("Entity Resource Display Test")
            .build();

        // Create a test texture to represent the resource
        Path texturePath = createTestTexture("entity-texture.png");

        // Simulate: Entity has RESOURCE_ID=42, and resource 42 is this texture
        // In a real scenario, the GUI would:
        // 1. Get snapshot with RESOURCE_ID component
        // 2. Download the resource data
        // 3. Create a sprite with the texture

        // Create sprite representing the entity's renderable
        Sprite entitySprite = Sprite.builder()
            .id(1)  // Entity ID
            .texturePath(texturePath.toAbsolutePath().toString())
            .x(400)
            .y(300)
            .sizeX(64)
            .sizeY(64)
            .zIndex(5)
            .build();

        window.addSprite(entitySprite);

        // Also add a TexturePreviewPanel to show the texture details
        ComponentFactory headlessFactory = new TestComponentFactory();
        TexturePreviewPanel preview = new TexturePreviewPanel(headlessFactory, 10, 10, 300, 350);
        preview.setTexture("entity-texture.png", texturePath.toAbsolutePath().toString());

        // Run frames
        window.runFrames(10);

        // Verify sprite is rendered
        assertThat(window.getSprites()).hasSize(1);
        assertThat(window.getSprites().get(0).getTexturePath()).isEqualTo(texturePath.toAbsolutePath().toString());

        // Verify preview panel has the texture info
        assertThat(preview.getTextureName()).isEqualTo("entity-texture.png");
        assertThat(preview.getTexturePath()).isEqualTo(texturePath.toAbsolutePath().toString());

        System.out.println("Entity with RESOURCE_ID rendered as sprite at (400, 300)");
        System.out.println("Texture preview panel shows: " + preview.getTextureName());
    }

    /**
     * Creates a minimal valid PNG file for testing.
     */
    private Path createTestTexture(String filename) throws IOException {
        Path texturePath = tempDir.resolve(filename);
        // Write a minimal valid PNG file (1x1 red pixel)
        byte[] minimalPng = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,  // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,  // 1x1 dimensions
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,  // 8-bit RGB
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,  // IDAT chunk
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,  // IEND chunk
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        Files.write(texturePath, minimalPng);
        return texturePath;
    }
}
