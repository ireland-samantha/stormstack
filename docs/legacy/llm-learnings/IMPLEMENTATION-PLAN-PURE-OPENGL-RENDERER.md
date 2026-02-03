# Implementation Plan: Clean Code Refactoring + Pure OpenGL Renderer + Sprite Controls

## Overview

This plan covers three interconnected tasks:
1. **Clean Code refactoring** of the GL implementation
2. **Pure OpenGL renderer** (no NanoVG) as an alternative backend
3. **Sprite mouse/keyboard handlers** for interactive sprites
4. **Command-line toggle** to switch between NanoVG and pure OpenGL renderers
5. **Test suite validation** including gui-acceptance-tests with both renderers

---

## Phase 1: Clean Code Refactoring (DRY + Extract Methods)

### 1.1 Unify loop()/loopFrames() in GLWindow

**File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/GLWindow.java`

**Current:** Two nearly identical 80-line methods
**After:** Single private implementation with predicate

```java
public void loop(Runnable update, Runnable cleanup) {
    loopWhile(() -> !glfwWindowShouldClose(windowHandle), update, cleanup);
}

public void loopFrames(int frames, Runnable update, Runnable cleanup) {
    int[] count = {0};
    loopWhile(() -> count[0]++ < frames && !glfwWindowShouldClose(windowHandle), update, cleanup);
}

private void loopWhile(BooleanSupplier condition, Runnable update, Runnable cleanup) { ... }
```

### 1.2 Extract NvgUtils for Common Rendering Patterns

**New File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/NvgUtils.java`

Extract duplicated patterns:
- `setupFont(nvg, fontId, fontSize)` - used in 10+ components
- `drawFilledRect(nvg, x, y, w, h, color, nvgColor)` - background rendering
- `drawStrokedRect(nvg, x, y, w, h, color, strokeWidth, nvgColor)` - border rendering
- `drawScrollbar(nvg, x, y, h, scrollOffset, maxScroll, contentHeight, nvgColor)` - used in TreeView, ListView, Panel

### 1.3 Add GuiConstants Class

**New File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/GuiConstants.java`

Consolidate magic numbers:
```java
public final class GuiConstants {
    // Typography
    public static final float FONT_SIZE_SMALL = 12.0f;
    public static final float FONT_SIZE_DEFAULT = 14.0f;
    // Sizing
    public static final float ITEM_HEIGHT_DEFAULT = 24.0f;
    public static final float CORNER_RADIUS = 4.0f;
    // Scrolling
    public static final int SCROLLBAR_WIDTH = 8;
    // Timing
    public static final long DOUBLE_CLICK_MS = 300;
    public static final long CURSOR_BLINK_MS = 530;
}
```

### 1.4 Extract Render Methods in GLTextField

**File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/GLTextField.java`

Break up 130-line `render()` method:
- `renderBackground(nvg, color)`
- `renderBorder(nvg, color)`
- `renderSelection(nvg, color)`
- `renderText(nvg, color)`
- `renderCursor(nvg, color)`

---

## Phase 2: Renderer Abstraction Layer

### 2.1 Create Renderer Interface

**New File:** `lightning-engine/rendering-core/src/main/java/.../render2d/Renderer.java`

```java
public interface Renderer {
    // Lifecycle
    void beginFrame(int width, int height, float pixelRatio);
    void endFrame();
    void dispose();

    // Primitives
    void drawRect(float x, float y, float w, float h, float[] color);
    void drawRoundedRect(float x, float y, float w, float h, float radius, float[] color);
    void strokeRect(float x, float y, float w, float h, float[] color, float strokeWidth);
    void strokeRoundedRect(float x, float y, float w, float h, float radius, float[] color, float strokeWidth);

    // Text
    void setFont(int fontId, float fontSize);
    void drawText(float x, float y, String text, float[] color, int align);
    float measureText(String text);

    // Clipping
    void pushClip(float x, float y, float w, float h);
    void popClip();

    // Transform
    void translate(float x, float y);
    void save();
    void restore();

    // Font management
    int loadFont(String name, String path);
    int getDefaultFontId();
}
```

### 2.2 NanoVG Renderer Implementation

**New File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/NanoVGRenderer.java`

Wraps existing NanoVG calls. Components will call `renderer.drawRect()` instead of `nvgBeginPath(); nvgRect(); nvgFill()`.

### 2.3 Pure OpenGL Renderer Implementation

**New File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/PureGLRenderer.java`

Uses direct OpenGL calls with custom shaders:
- Shader-based quad rendering for rectangles
- Font rendering via texture atlas (FreeType → texture)
- Scissor test for clipping
- Batch rendering for performance

**Supporting Files:**
- `shaders/gui.vert` - GUI vertex shader
- `shaders/gui.frag` - GUI fragment shader
- `shaders/text.vert` - Text vertex shader
- `shaders/text.frag` - Text fragment shader

### 2.4 Update WindowComponent Interface

**File:** `lightning-engine/rendering-core/src/main/java/.../render2d/WindowComponent.java`

Change render signature:
```java
// Old: void render(long nvg);
// New: void render(Renderer renderer);
```

This is a breaking change that requires updating all components.

### 2.5 Update All GL Components

Update these files to use `Renderer` instead of direct NanoVG:
- GLButton.java
- GLLabel.java
- GLPanel.java
- GLTextField.java
- GLTreeView.java
- GLListView.java
- GLImage.java
- GLContextMenu.java

---

## Phase 3: Sprite Input Handlers

### 3.1 Add SpriteInputHandler Interface

**New File:** `lightning-engine/rendering-core/src/main/java/.../render2d/SpriteInputHandler.java`

```java
public interface SpriteInputHandler {
    /** Called when mouse clicks on this sprite */
    default boolean onMouseClick(Sprite sprite, int button, int action) { return false; }

    /** Called when mouse hovers over this sprite */
    default void onMouseEnter(Sprite sprite) {}
    default void onMouseExit(Sprite sprite) {}
    default boolean onMouseMove(Sprite sprite, int x, int y) { return false; }

    /** Called when this sprite has focus and key is pressed */
    default boolean onKeyPress(Sprite sprite, int key, int action, int mods) { return false; }
}
```

### 3.2 Extend Sprite Class

**File:** `lightning-engine/rendering-core/src/main/java/.../render2d/Sprite.java`

Add:
```java
@Getter @Setter
private SpriteInputHandler inputHandler;

@Getter @Setter
private boolean focusable = false;  // Can receive keyboard focus

public boolean contains(int px, int py) {
    return px >= x && px < x + sizeX && py >= y && py < y + sizeY;
}
```

### 3.3 Update GLWindow Input Dispatch

**File:** `lightning-engine/rendering-core/src/main/java/.../impl/opengl/GLWindow.java`

In mouse callback, add sprite hit testing:
```java
// Mouse click callback - check sprites BEFORE components (sprites are behind)
// Or make sprites optionally "on top" via a flag
for (Sprite sprite : sprites) {
    if (sprite.getInputHandler() != null && sprite.contains(mx, my)) {
        if (sprite.getInputHandler().onMouseClick(sprite, button, action)) {
            return; // Event consumed
        }
    }
}
```

Add sprite focus tracking for keyboard input.

### 3.4 Update HeadlessWindow for Testing

**File:** `lightning-engine/rendering-test/src/main/java/.../testing/HeadlessWindow.java`

Add sprite input simulation to match GLWindow behavior.

---

## Phase 4: Command-Line Renderer Toggle

### 4.1 Update EngineGuiApplication

**File:** `lightning-engine/gui/src/main/java/.../gui/EngineGuiApplication.java`

Add argument parsing:
```java
case "--renderer", "-r" -> {
    if (i + 1 < args.length) {
        rendererType = args[++i];  // "nanovg" or "opengl"
    }
}
```

Update help message:
```
  --renderer, -r    Renderer backend: nanovg (default) or opengl
```

### 4.2 Update GuiConfig

**File:** `lightning-engine/gui/src/main/java/.../gui/GuiConfig.java`

Add:
```java
public enum RendererType { NANOVG, PURE_OPENGL }

@Getter
private RendererType rendererType = RendererType.NANOVG;
```

### 4.3 Update WindowBuilder

**File:** `lightning-engine/rendering-core/src/main/java/.../render2d/WindowBuilder.java`

Add:
```java
public WindowBuilder renderer(RendererType type) {
    this.rendererType = type;
    return this;
}
```

Build method creates appropriate renderer.

---

## Phase 5: Test Suite Validation

### 5.1 Component Unit Tests

Existing tests in `rendering-core/src/test/java` should continue to work since they test component logic, not rendering.

### 5.2 Headless GUI Tests

Tests using `HeadlessWindow` (in `gui/src/test/java/.../panel/PanelHeadlessTest.java`) are renderer-agnostic and should pass unchanged.

### 5.3 Add Renderer-Specific Integration Tests

**New Test:** `lightning-engine/rendering-core/src/test/java/.../gui/PureGLRendererTest.java`

Test that PureGLRenderer produces correct output using off-screen framebuffer:
- Rectangle rendering
- Text rendering
- Clipping
- Component rendering

### 5.4 GUI Acceptance Tests with Both Renderers

**File:** `lightning-engine/gui/src/test/java/.../panel/GuiAcceptanceTest.java`

Run existing acceptance tests with both renderer types:
```java
@ParameterizedTest
@EnumSource(RendererType.class)
void fullGuiTest(RendererType renderer) {
    // Configure window with specific renderer
    // Run acceptance test suite
}
```

---

## Implementation Order

1. **Phase 1** (Clean Code) - No breaking changes, can be done first
2. **Phase 2.1-2.2** (Renderer interface + NanoVG impl) - Refactor without changing behavior
3. **Phase 2.4-2.5** (Update components) - Change render signature
4. **Phase 2.3** (Pure OpenGL renderer) - Add new backend
5. **Phase 3** (Sprite handlers) - New feature, independent of renderer
6. **Phase 4** (CLI toggle) - Wire everything together
7. **Phase 5** (Tests) - Validate everything works

---

## Files to Modify (Summary)

### New Files
- `render2d/Renderer.java`
- `render2d/SpriteInputHandler.java`
- `impl/opengl/NanoVGRenderer.java`
- `impl/opengl/PureGLRenderer.java`
- `impl/opengl/NvgUtils.java`
- `impl/opengl/GuiConstants.java`
- `shaders/gui.vert`, `shaders/gui.frag`
- `shaders/text.vert`, `shaders/text.frag`

### Modified Files
- `render2d/WindowComponent.java` - render signature
- `render2d/Sprite.java` - add input handler
- `render2d/WindowBuilder.java` - add renderer option
- `impl/opengl/GLWindow.java` - use Renderer, add sprite input
- `impl/opengl/GLButton.java` - use Renderer
- `impl/opengl/GLLabel.java` - use Renderer
- `impl/opengl/GLPanel.java` - use Renderer
- `impl/opengl/GLTextField.java` - use Renderer + extract methods
- `impl/opengl/GLTreeView.java` - use Renderer
- `impl/opengl/GLListView.java` - use Renderer
- `impl/opengl/GLImage.java` - use Renderer
- `impl/opengl/GLContextMenu.java` - use Renderer
- `gui/EngineGuiApplication.java` - CLI argument
- `gui/GuiConfig.java` - renderer type config
- `testing/HeadlessWindow.java` - sprite input simulation

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking change to render() signature | High | Phase 2.2 creates NanoVGRenderer that wraps existing behavior first |
| Pure OpenGL text rendering complexity | Medium | Use STB TrueType for font rasterization (already in LWJGL) |
| Performance regression | Medium | Batch rendering, texture atlases |
| Test coverage gaps | Medium | Parameterized tests run both renderers |

---

## Estimated Complexity

- Phase 1 (Clean Code): Low - straightforward refactoring
- Phase 2 (Renderer abstraction): Medium-High - affects many files
- Phase 3 (Sprite handlers): Low - additive feature
- Phase 4 (CLI toggle): Low - simple configuration
- Phase 5 (Tests): Medium - need thorough coverage
