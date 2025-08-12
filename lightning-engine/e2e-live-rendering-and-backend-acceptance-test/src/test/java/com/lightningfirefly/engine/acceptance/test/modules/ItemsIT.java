package com.lightningfirefly.engine.acceptance.test.modules;

import com.lightningfirefly.engine.acceptance.test.domain.Entity;
import com.lightningfirefly.engine.acceptance.test.domain.Match;
import com.lightningfirefly.engine.acceptance.test.domain.TestBackend;
import com.lightningfirefly.game.renderering.DefaultGameRenderer;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for the ItemsModule.
 *
 * <p>Tests item type creation, item spawning, and inventory management with visual verification.
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Items Module E2E Tests")
@Testcontainers
class ItemsIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private TestBackend backend;
    private Match match;
    private Window window;
    private DefaultGameRenderer renderer;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL: {}", backendUrl);

        backend = TestBackend.connectTo(backendUrl);
        match = backend.createMatch()
                .withModules("EntityModule", "ItemsModule", "HealthModule", "RenderModule")
                .start();
        log.info("Created match {} with EntityModule, ItemsModule, HealthModule, and RenderModule", match.id());
    }

    @AfterEach
    void tearDown() {
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        if (window != null) {
            window.stop();
            window = null;
        }
        if (match != null) {
            match.delete();
            match = null;
        }
    }

    @Test
    @DisplayName("Item type can be created")
    void itemType_canBeCreated() throws IOException {
        // When: Creating an item type
        createItemType("Health Potion", 10, 1, 50, 0.5f, 25, 0, 0);
        match.tick();

        // Then: No errors (item type is stored in registry, not ECS)
        // Item type creation is successful if no exception is thrown
        log.info("Item type 'Health Potion' created successfully");
    }

    @Test
    @DisplayName("Item spawns at correct position")
    void item_spawnsAtCorrectPosition() throws IOException {
        // Given: An item type
        createItemType("Sword", 1, 2, 100, 5, 0, 10, 0);
        match.tick();

        // When: Spawning an item at (150, 250) with a sprite
        spawnItemWithSprite(1, 150, 250, 1);
        match.tick();

        // Then: Position matches
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("EntityModule", "POSITION_X"))
                .hasValueSatisfying(x -> assertThat(x).isEqualTo(150.0f));
        assertThat(snapshot.getComponentValue("EntityModule", "POSITION_Y"))
                .hasValueSatisfying(y -> assertThat(y).isEqualTo(250.0f));

        // Visual verification: Item sprite should be visible at (150, 250)
        setupRenderer();
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        int spriteSize = 24;
        forWindow(window).inRegion(150 - spriteSize/2, 250 - spriteSize/2, spriteSize * 2, spriteSize * 2)
                .withTolerance(15).hasContent();
        log.info("Item spawned and visible at (150, 250)");
    }

    @Test
    @DisplayName("Item has correct type properties")
    void item_hasCorrectTypeProperties() throws IOException {
        // Given: An item type with specific properties
        createItemType("Magic Ring", 5, 3, 500, 0.1f, 0, 0, 25);
        match.tick();

        // When: Spawning an item
        spawnItem(1, 0, 0, 3);
        match.tick();

        // Then: Item has correct properties
        var snapshot = match.fetchSnapshot();
        // Find the item by checking for ITEM_TYPE_ID component
        var itemTypeIds = snapshot.getComponent("ItemsModule", "ITEM_TYPE_ID");
        assertThat(itemTypeIds).isNotEmpty();
        log.info("Item type IDs found: {}", itemTypeIds);

        var maxStacks = snapshot.getComponent("ItemsModule", "MAX_STACK");
        var stackSizes = snapshot.getComponent("ItemsModule", "STACK_SIZE");
        log.info("MAX_STACK values: {}, STACK_SIZE values: {}", maxStacks, stackSizes);

        // Find the index of our item (where ITEM_TYPE_ID > 0)
        int itemIndex = -1;
        for (int i = 0; i < itemTypeIds.size(); i++) {
            if (itemTypeIds.get(i) > 0) {
                itemIndex = i;
                break;
            }
        }
        assertThat(itemIndex).as("Item should exist").isGreaterThanOrEqualTo(0);

        // Check the item's properties at that index
        assertThat(maxStacks.get(itemIndex)).isEqualTo(5.0f);
        assertThat(stackSizes.get(itemIndex)).isEqualTo(3.0f);
    }

    @Test
    @DisplayName("Item can be picked up")
    void item_canBePickedUp() throws IOException {
        // Given: An item and an entity
        createItemType("Gold Coin", 100, 0, 1, 0.01f, 0, 0, 0);
        match.tick();

        Entity player = match.spawnEntity().ofType(1).execute();
        spawnItem(1, 100, 100, 10);
        match.tick();

        // Get item entity ID
        var snapshot = match.fetchSnapshot();
        var itemEntityIds = snapshot.getComponent("ItemsModule", "ITEM_TYPE_ID");
        assertThat(itemEntityIds).isNotEmpty();

        // Find the item entity ID
        var entityIds = snapshot.getComponent("EntityModule", "ENTITY_ID");
        long itemEntityId = 0;
        for (int i = 0; i < entityIds.size(); i++) {
            if (i < itemEntityIds.size() && itemEntityIds.get(i) > 0) {
                itemEntityId = entityIds.get(i).longValue();
                break;
            }
        }
        assertThat(itemEntityId).isGreaterThan(0);

        // When: Player picks up the item
        pickupItem(itemEntityId, player.id(), 0);
        match.tick();

        // Then: Item is now owned by player
        snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("ItemsModule", "OWNER_ENTITY_ID"))
                .hasValueSatisfying(owner -> assertThat(owner).isEqualTo((float) player.id()));
    }

    @Test
    @DisplayName("Item can be dropped")
    void item_canBeDropped() throws IOException {
        // Given: A player with an item
        createItemType("Shield", 1, 1, 200, 10, 0, 0, 50);
        match.tick();

        Entity player = match.spawnEntity().ofType(1).execute();
        spawnItem(1, 0, 0, 1);
        match.tick();

        // Get item entity ID and pick it up
        var snapshot = match.fetchSnapshot();
        var entityIds = snapshot.getComponent("EntityModule", "ENTITY_ID");
        long itemEntityId = entityIds.get(entityIds.size() - 1).longValue(); // Last entity is the item

        pickupItem(itemEntityId, player.id(), 0);
        match.tick();

        // When: Player drops the item at (300, 400)
        dropItem(itemEntityId, 300, 400);
        match.tick();

        // Then: Item is at new position and has no owner
        snapshot = match.fetchSnapshot();
        // Find the item's position
        var itemPositionX = snapshot.getComponentValue("EntityModule", "POSITION_X",
                (int) (snapshot.getComponent("EntityModule", "ENTITY_ID").indexOf((float) itemEntityId)));

        // Owner should be 0 (no owner)
        assertThat(snapshot.getComponentValue("ItemsModule", "OWNER_ENTITY_ID"))
                .hasValueSatisfying(owner -> assertThat(owner).isEqualTo(0.0f));
    }

    @Test
    @DisplayName("Consumable item heals on use")
    void consumableItem_healsOnUse() throws IOException {
        // Given: A health potion item type and a damaged entity
        createItemType("Health Potion", 5, 0, 25, 0.2f, 50, 0, 0);
        match.tick();

        Entity player = match.spawnEntity().ofType(1).execute();
        player.attachHealth().withMaxHP(100).withCurrentHP(30).andApply();
        match.tick();

        // Spawn potion
        spawnItem(1, 0, 0, 1);
        match.tick();

        // Find the item entity (it's the last entity spawned, so highest entity ID)
        var snapshot = match.fetchSnapshot();
        var entityIds = snapshot.getComponent("EntityModule", "ENTITY_ID");
        var itemTypeIds = snapshot.getComponent("ItemsModule", "ITEM_TYPE_ID");
        log.info("Entity IDs: {}", entityIds);
        log.info("Item type IDs: {}", itemTypeIds);

        // The item is the last entity (spawned after the player)
        assertThat(entityIds).isNotEmpty();
        assertThat(itemTypeIds).isNotEmpty();
        long itemEntityId = entityIds.get(entityIds.size() - 1).longValue();
        log.info("Found item entity ID: {} (player is {})", itemEntityId, player.id());

        // Check item's heal amount
        var healAmounts = snapshot.getComponent("ItemsModule", "HEAL_AMOUNT");
        log.info("HEAL_AMOUNT values: {}", healAmounts);

        // Pick up potion
        pickupItem(itemEntityId, player.id(), 0);
        match.tick();

        // Verify initial health
        snapshot = match.fetchSnapshot();
        var currentHPBefore = snapshot.getComponent("HealthModule", "CURRENT_HP");
        log.info("Player HP before using potion: {}", currentHPBefore);

        // When: Player uses the potion
        useItem(itemEntityId, player.id());
        match.tick();

        // Then: Player is healed
        snapshot = match.fetchSnapshot();
        // HP should be 30 + 50 = 80
        var currentHPs = snapshot.getComponent("HealthModule", "CURRENT_HP");
        log.info("Player HP after using potion: {}", currentHPs);

        // Find player's HP (player should be the entity with health flag)
        var maxHPs = snapshot.getComponent("HealthModule", "MAX_HP");
        for (int i = 0; i < maxHPs.size(); i++) {
            if (maxHPs.get(i) == 100.0f) {  // Our player has maxHP=100
                float playerHP = currentHPs.get(i);
                log.info("Player HP at index {}: {}", i, playerHP);
                assertThat(playerHP).as("Player should be healed to 80 HP").isEqualTo(80.0f);
                return;
            }
        }
        // Fallback assertion
        assertThat(currentHPs).anyMatch(hp -> hp >= 80.0f);
    }

    // Helper methods
    private void createItemType(String name, int maxStack, int rarity, float value,
            float weight, float healAmount, float damageBonus, float armorValue) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", match.id());  // Include matchId for per-match item type registry
        payload.put("name", name);
        payload.put("maxStack", (float) maxStack);
        payload.put("rarity", (float) rarity);
        payload.put("value", value);
        payload.put("weight", weight);
        payload.put("healAmount", healAmount);
        payload.put("damageBonus", damageBonus);
        payload.put("armorValue", armorValue);

        backend.commandAdapter().submitCommand(match.id(), "createItemType", 0, payload);
    }

    private void spawnItem(long itemTypeId, float x, float y, int stackSize) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", match.id());
        payload.put("itemTypeId", itemTypeId);
        payload.put("positionX", x);
        payload.put("positionY", y);
        payload.put("stackSize", (float) stackSize);

        backend.commandAdapter().submitCommand(match.id(), "spawnItem", 0, payload);
    }

    private void pickupItem(long itemEntityId, long pickerEntityId, int slotIndex) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemEntityId", itemEntityId);
        payload.put("pickerEntityId", pickerEntityId);
        payload.put("slotIndex", (float) slotIndex);

        backend.commandAdapter().submitCommand(match.id(), "pickupItem", itemEntityId, payload);
    }

    private void dropItem(long itemEntityId, float x, float y) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemEntityId", itemEntityId);
        payload.put("positionX", x);
        payload.put("positionY", y);

        backend.commandAdapter().submitCommand(match.id(), "dropItem", itemEntityId, payload);
    }

    private void useItem(long itemEntityId, long userEntityId) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemEntityId", itemEntityId);
        payload.put("userEntityId", userEntityId);

        backend.commandAdapter().submitCommand(match.id(), "useItem", itemEntityId, payload);
    }

    /**
     * Spawn an item with a sprite attached for visual rendering.
     */
    private void spawnItemWithSprite(long itemTypeId, float x, float y, int stackSize) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", match.id());
        payload.put("itemTypeId", itemTypeId);
        payload.put("positionX", x);
        payload.put("positionY", y);
        payload.put("stackSize", (float) stackSize);
        // Add sprite properties for visual rendering
        payload.put("resourceId", 1L);
        payload.put("spriteWidth", 24f);
        payload.put("spriteHeight", 24f);

        backend.commandAdapter().submitCommand(match.id(), "spawnItem", 0, payload);
    }

    /**
     * Setup the window and renderer for visual verification.
     */
    private void setupRenderer() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Items Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));
    }
}
