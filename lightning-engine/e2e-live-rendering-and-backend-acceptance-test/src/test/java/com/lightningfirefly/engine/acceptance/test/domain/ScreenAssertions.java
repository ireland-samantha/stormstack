package com.lightningfirefly.engine.acceptance.test.domain;

import com.lightningfirefly.engine.rendering.render2d.Sprite;
import com.lightningfirefly.engine.rendering.render2d.Window;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertions for verifying rendered screen content.
 *
 * <p>Verifies sprite rendering by checking sprite positions and properties
 * in the window's sprite list - a decoupled approach that doesn't require
 * direct OpenGL framebuffer access.
 *
 * <p>Usage examples:
 * <pre>{@code
 * ScreenAssertions.forWindow(window)
 *     .atPosition(200, 150)
 *     .hasSpriteRendered();
 *
 * ScreenAssertions.forWindow(window)
 *     .inRegion(100, 100, 64, 64)
 *     .hasSpriteCount(3);
 * }</pre>
 */
public class ScreenAssertions {

    private final Window window;
    private int x;
    private int y;
    private int width = 1;
    private int height = 1;
    private int tolerance = 5;

    private ScreenAssertions(Window window) {
        this.window = window;
    }

    /**
     * Create assertions for a window.
     */
    public static ScreenAssertions forWindow(Window window) {
        return new ScreenAssertions(window);
    }

    /**
     * Set the position to check.
     */
    public ScreenAssertions atPosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.width = 1;
        this.height = 1;
        return this;
    }

    /**
     * Set a region to check.
     */
    public ScreenAssertions inRegion(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Set the position tolerance for matching.
     */
    public ScreenAssertions withTolerance(int tolerance) {
        this.tolerance = tolerance;
        return this;
    }

    /**
     * Assert that a sprite is rendered in the region.
     */
    public ScreenAssertions hasContent() {
        List<Sprite> spritesInRegion = findSpritesInRegion();
        assertThat(spritesInRegion)
                .as("Region at (%d, %d) size %dx%d should have sprites", x, y, width, height)
                .isNotEmpty();
        return this;
    }

    /**
     * Assert that no sprites are in the region.
     */
    public ScreenAssertions isEmpty() {
        List<Sprite> spritesInRegion = findSpritesInRegion();
        assertThat(spritesInRegion)
                .as("Region at (%d, %d) size %dx%d should be empty", x, y, width, height)
                .isEmpty();
        return this;
    }

    /**
     * Assert that a visible sprite exists at this location.
     */
    public ScreenAssertions hasVisibleSprite() {
        return hasContent();
    }

    /**
     * Assert that a specific number of sprites are in the region.
     */
    public ScreenAssertions hasSpriteCount(int expectedCount) {
        List<Sprite> spritesInRegion = findSpritesInRegion();
        assertThat(spritesInRegion)
                .as("Region at (%d, %d) size %dx%d should have %d sprites",
                        x, y, width, height, expectedCount)
                .hasSize(expectedCount);
        return this;
    }

    /**
     * Assert that a sprite with the given ID exists in the region.
     */
    public ScreenAssertions hasSpriteWithId(int spriteId) {
        List<Sprite> spritesInRegion = findSpritesInRegion();
        boolean found = spritesInRegion.stream().anyMatch(s -> s.getId() == spriteId);
        assertThat(found)
                .as("Region at (%d, %d) size %dx%d should contain sprite with ID %d",
                        x, y, width, height, spriteId)
                .isTrue();
        return this;
    }

    /**
     * Assert that the window has sprites somewhere.
     */
    public ScreenAssertions hasAnySprites() {
        assertThat(window.getSprites())
                .as("Window should have at least one sprite")
                .isNotEmpty();
        return this;
    }

    /**
     * Assert the total number of sprites in the window.
     */
    public ScreenAssertions hasTotalSpriteCount(int expectedCount) {
        assertThat(window.getSprites())
                .as("Window should have %d sprites", expectedCount)
                .hasSize(expectedCount);
        return this;
    }

    /**
     * Get the number of sprites in the current region.
     */
    public int getSpriteCount() {
        return findSpritesInRegion().size();
    }

    /**
     * Find sprites that overlap with the current region.
     */
    private List<Sprite> findSpritesInRegion() {
        return window.getSprites().stream()
                .filter(this::spriteOverlapsRegion)
                .toList();
    }

    private boolean spriteOverlapsRegion(Sprite sprite) {
        int sx = sprite.getX();
        int sy = sprite.getY();
        int sw = sprite.getSizeX();
        int sh = sprite.getSizeY();

        // Check for overlap with tolerance
        return sx < (x + width + tolerance) &&
               (sx + sw) > (x - tolerance) &&
               sy < (y + height + tolerance) &&
               (sy + sh) > (y - tolerance);
    }
}
