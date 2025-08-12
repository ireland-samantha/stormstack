package com.lightningfirefly.engine.acceptance.test.modules;

import com.lightningfirefly.engine.acceptance.test.domain.Entity;
import com.lightningfirefly.engine.acceptance.test.domain.Match;
import com.lightningfirefly.engine.acceptance.test.domain.TestBackend;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for the HealthModule.
 *
 * <p>Tests health, damage, and healing functionality via REST API against a live backend.
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Health Module E2E Tests")
@Testcontainers
class HealthIT {

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

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL: {}", backendUrl);

        backend = TestBackend.connectTo(backendUrl);
        match = backend.createMatch()
                .withModules("EntityModule", "HealthModule")
                .start();
        log.info("Created match {} with EntityModule and HealthModule", match.id());
    }

    @AfterEach
    void tearDown() {
        if (match != null) {
            match.delete();
        }
    }

    @Test
    @DisplayName("Entity with health shows correct HP values")
    void entityWithHealth_showsCorrectHPValues() {
        // Given: An entity with 100 max HP
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(100).andApply();
        match.tick();

        // Then: Snapshot shows correct health values
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "MAX_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(100.0f));
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(100.0f));
    }

    @Test
    @DisplayName("Damage reduces HP correctly")
    void damage_reducesHPCorrectly() {
        // Given: An entity with 100 HP
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(100).andApply();
        match.tick();

        // When: 30 damage is dealt
        entity.damage(30);
        match.tick();

        // Then: HP is reduced to 70
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(70.0f));
    }

    @Test
    @DisplayName("Entity dies when HP reaches zero")
    void entity_diesWhenHPReachesZero() {
        // Given: An entity with 50 HP
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(50).andApply();
        match.tick();

        // When: 50 damage is dealt (exactly depletes HP)
        entity.damage(50);
        match.tick();

        // Then: Entity is marked as dead
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(0.0f));
        assertThat(snapshot.getComponentValue("HealthModule", "IS_DEAD"))
                .hasValueSatisfying(dead -> assertThat(dead).isEqualTo(1.0f));
    }

    @Test
    @DisplayName("Healing restores HP correctly")
    void healing_restoresHPCorrectly() {
        // Given: An entity with 50/100 HP
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(100).withCurrentHP(50).andApply();
        match.tick();

        // When: 25 healing is applied
        entity.heal(25);
        match.tick();

        // Then: HP is restored to 75
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(75.0f));
    }

    @Test
    @DisplayName("Healing does not exceed max HP")
    void healing_doesNotExceedMaxHP() {
        // Given: An entity with 80/100 HP
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(100).withCurrentHP(80).andApply();
        match.tick();

        // When: 50 healing is applied (more than needed)
        entity.heal(50);
        match.tick();

        // Then: HP is capped at 100
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(100.0f));
    }

    @Test
    @DisplayName("Multiple entities have independent HP")
    void multipleEntities_haveIndependentHP() {
        // Given: Two entities with different HP values
        Entity entity1 = match.spawnEntity().ofType(1).execute();
        entity1.attachHealth().withMaxHP(100).andApply();

        Entity entity2 = match.spawnEntity().ofType(1).execute();
        entity2.attachHealth().withMaxHP(200).andApply();

        match.tick();

        // When: Damage one entity
        entity1.damage(30);
        match.tick();

        // Then: Only entity1's HP is reduced
        var snapshot = match.fetchSnapshot();
        var currentHPs = snapshot.getComponent("HealthModule", "CURRENT_HP");
        var maxHPs = snapshot.getComponent("HealthModule", "MAX_HP");

        // Entity1 should have 70 HP, Entity2 should have 200 HP
        assertThat(currentHPs).contains(70.0f, 200.0f);
        assertThat(maxHPs).contains(100.0f, 200.0f);
    }

    @Test
    @DisplayName("Dead entity does not take further damage")
    void deadEntity_doesNotTakeFurtherDamage() {
        // Given: An entity that dies from damage
        Entity entity = match.spawnEntity().ofType(1).execute();
        entity.attachHealth().withMaxHP(50).andApply();
        match.tick();

        entity.damage(50);
        match.tick();

        // When: More damage is dealt to dead entity
        entity.damage(100);
        match.tick();

        // Then: HP stays at 0
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("HealthModule", "CURRENT_HP"))
                .hasValueSatisfying(hp -> assertThat(hp).isEqualTo(0.0f));
        assertThat(snapshot.getComponentValue("HealthModule", "IS_DEAD"))
                .hasValueSatisfying(dead -> assertThat(dead).isEqualTo(1.0f));
    }
}
