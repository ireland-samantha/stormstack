# GL Implementation Clean Code Analysis

Analysis of OpenGL/NanoVG GUI implementation against Robert Martin's "Clean Code" principles.

## Location
`lightning-engine/rendering-core/src/main/java/com/lightningfirefly/engine/rendering/render2d/impl/opengl/`

---

## Chapter 2: Meaningful Names

### Issues Found

#### Abbreviations
| Current | Recommended |
|---------|-------------|
| `mx`, `my` | `mouseX`, `mouseY` |
| `bg`, `bgColor` | `background`, `backgroundColor` |
| `nvg` | Acceptable (NanoVG convention) |
| `ctx` | `context` |

#### Vague Names
```java
// GLListView.java
private long lastClickTime = 0;  // OK
private int lastClickIndex = -1; // OK
private static final long DOUBLE_CLICK_TIME_MS = 300; // Good!
```

### Good Naming Examples
- `onSelectionChanged` - Clear intent
- `hoveredBackgroundColor` - Descriptive
- `calculateTotalHeight()` - Verb phrase

---

## Chapter 3: Functions

### Long Methods (Violations)

#### GLWindow.loop() - 80+ lines
```java
public void loop(Runnable updateCallback, Runnable cleanupCallback) {
    // Window setup
    // Font loading
    // Sprite renderer init
    // Render loop with:
    //   - Input polling
    //   - Update callback
    //   - Render pass
    //   - Sprite rendering
    // Cleanup
}
```
**Issue:** Method does too many things
**Solution:** Extract private methods

#### GLWindow.loopFrames() - 80+ lines
```java
public void loopFrames(int frames, Runnable updateCallback, Runnable cleanupCallback) {
    // Nearly identical to loop() with frame counter
}
```
**Issue:** DRY violation - almost identical to `loop()`
**Solution:** Unify with strategy pattern or predicate parameter

#### GLTextField.render() - 130+ lines
```java
public void render(long nvg) {
    // Background rendering
    // Border rendering
    // Selection rendering
    // Text rendering
    // Cursor rendering
    // Scrollbar rendering (if scrollable)
}
```
**Solution:** Extract: `renderBackground()`, `renderSelection()`, `renderText()`, `renderCursor()`

### Too Many Parameters
```java
// 5 parameters
boolean onMouseClick(int mx, int my, int button, int action);
boolean onMouseScroll(int mx, int my, double scrollX, double scrollY);
```
**Solution:** Introduce value objects:
```java
record MouseEvent(int x, int y, int button, int action) {}
record ScrollEvent(int x, int y, double deltaX, double deltaY) {}
```

---

## Chapter 4: Comments

### Redundant Comments
```java
// Draw background
nvgBeginPath(nvg);
nvgRect(nvg, x, y, width, height);
nvgFillColor(nvg, GLColour.rgba(backgroundColor, color));
nvgFill(nvg);

// Draw border
nvgStrokeColor(nvg, GLColour.rgba(borderColor, color));
```
**Issue:** Comments state the obvious

### Missing Documentation
- Public API methods lack Javadoc
- Complex algorithms not explained

### Good Comment Example
```java
/**
 * Thread-local context for GUI rendering.
 * Provides access to shared resources like fonts during the render pass.
 */
public final class GLContext { ... }
```

---

## DRY Violations

### 1. Scrollbar Rendering (3 duplications)

**GLListView.java:318-336:**
```java
if (needsScrollbar()) {
    float scrollbarX = x + width - scrollbarWidth - 1;
    float scrollbarHeight = height - 2;
    float thumbHeight = Math.max(20, (height / totalHeight) * scrollbarHeight);
    float thumbY = y + 1 + (scrollOffset / maxScroll) * (scrollbarHeight - thumbHeight);
    // Draw track
    // Draw thumb
}
```

**GLTreeView.java:203-220:** Nearly identical code

**GLPanel.java:278-306:** Same pattern

**Solution:** Extract `ScrollbarRenderer` utility class

### 2. Font Setup Pattern (10+ duplications)

```java
// Repeated in every render method
int effectiveFontId = fontId >= 0 ? fontId : GLContext.getDefaultFontId();
if (effectiveFontId >= 0) {
    nvgFontFaceId(nvg, effectiveFontId);
}
nvgFontSize(nvg, fontSize);
```

**Solution:** Extract utility method:
```java
static void setupFont(long nvg, int fontId, float fontSize) {
    int effectiveFontId = fontId >= 0 ? fontId : GLContext.getDefaultFontId();
    if (effectiveFontId >= 0) {
        nvgFontFaceId(nvg, effectiveFontId);
    }
    nvgFontSize(nvg, fontSize);
}
```

### 3. loop() vs loopFrames() in GLWindow

Two 80-line methods that differ only in termination condition.

**Solution:**
```java
public void loop(Runnable update, Runnable cleanup) {
    loopWhile(() -> !glfwWindowShouldClose(windowHandle), update, cleanup);
}

public void loopFrames(int frames, Runnable update, Runnable cleanup) {
    int[] count = {0};
    loopWhile(() -> count[0]++ < frames && !glfwWindowShouldClose(windowHandle),
              update, cleanup);
}

private void loopWhile(BooleanSupplier condition, Runnable update, Runnable cleanup) {
    // Unified implementation
}
```

---

## Magic Numbers

### Scattered Constants
```java
// GLTreeView.java
private float itemHeight = 22.0f;
private float fontSize = 13.0f;
private float indentWidth = 20.0f;

// GLListView.java
private float itemHeight = 24.0f;  // Different default!
private float fontSize = 14.0f;
private static final long DOUBLE_CLICK_TIME_MS = 300;

// GLButton.java
private float fontSize = 14.0f;
private float cornerRadius = 4.0f;

// GLPanel.java
private float borderWidth = 1.0f;
private float cornerRadius = 4.0f;
private static final float SCROLL_SPEED = 20.0f;
private static final int SCROLLBAR_WIDTH = 8;
private static final int SCROLLBAR_MIN_LENGTH = 20;
```

### Solution: Theme Constants Class
```java
public final class GuiConstants {
    // Typography
    public static final float FONT_SIZE_SMALL = 12.0f;
    public static final float FONT_SIZE_DEFAULT = 14.0f;
    public static final float FONT_SIZE_LARGE = 16.0f;

    // Sizing
    public static final float ITEM_HEIGHT_COMPACT = 22.0f;
    public static final float ITEM_HEIGHT_DEFAULT = 24.0f;
    public static final float CORNER_RADIUS = 4.0f;
    public static final float BORDER_WIDTH = 1.0f;

    // Scrolling
    public static final int SCROLLBAR_WIDTH = 8;
    public static final int SCROLLBAR_MIN_LENGTH = 20;
    public static final float SCROLL_SPEED = 20.0f;

    // Timing
    public static final long DOUBLE_CLICK_MS = 300;
    public static final long CURSOR_BLINK_MS = 530;
}
```

---

## Error Handling (Chapter 7)

### Silent Failures
```java
// GLButton.onMouseClick returns false silently
@Override
public boolean onMouseClick(int mx, int my, int button, int action) {
    if (!visible) {
        return false;  // Silent
    }
    // ...
}
```

### Missing Defensive Checks
```java
// GLListView - no null check on items
public void setItems(List<String> items) {
    this.items.clear();
    this.items.addAll(items);  // NPE if items is null
}
```

### Solution
```java
public void setItems(List<String> items) {
    Objects.requireNonNull(items, "items must not be null");
    this.items.clear();
    this.items.addAll(items);
}
```

---

## Classes (Chapter 10)

### Cohesion Issues

#### GLTextField - Low Cohesion
741 lines handling:
- Text state
- Selection state
- Cursor state
- Scroll state
- Context menu state

**Solution:** Split into focused classes

### Encapsulation Issues

Many fields are `private` but have public setters with no validation:
```java
public void setFontSize(float fontSize) {
    this.fontSize = fontSize;  // No validation
}
```

**Solution:** Add validation:
```java
public void setFontSize(float fontSize) {
    if (fontSize <= 0) {
        throw new IllegalArgumentException("fontSize must be positive");
    }
    this.fontSize = fontSize;
}
```

---

## Summary of Refactoring Priorities

### High Priority
1. **Unify loop()/loopFrames()** - Direct DRY violation
2. **Extract ScrollbarRenderer** - 3 duplications
3. **Extract font setup utility** - 10+ duplications
4. **Add GuiConstants class** - Magic number consolidation

### Medium Priority
5. **Break up GLTextField.render()** - 130+ lines
6. **Introduce MouseEvent/ScrollEvent** - Parameter reduction
7. **Add null checks** - Defensive programming

### Low Priority
8. **Rename mx/my parameters** - Style improvement
9. **Remove redundant comments** - Noise reduction
10. **Add Javadoc** - Documentation

---

## Before/After Example

### Before (Current)
```java
// GLTreeView.render() excerpt
try (var color = NVGColor.malloc()) {
    // Draw background
    nvgBeginPath(nvg);
    nvgRect(nvg, x, y, width, height);
    nvgFillColor(nvg, GLColour.rgba(backgroundColor, color));
    nvgFill(nvg);

    // Draw border
    nvgStrokeColor(nvg, GLColour.rgba(borderColor, color));
    nvgStrokeWidth(nvg, 1.0f);
    nvgStroke(nvg);

    // ... 80 more lines
}
```

### After (Refactored)
```java
@Override
public void render(long nvg) {
    if (!visible) return;

    try (var color = NVGColor.malloc()) {
        renderBackground(nvg, color);
        renderBorder(nvg, color);
        renderContent(nvg, color);
        renderScrollbar(nvg, color);
    }
}

private void renderBackground(long nvg, NVGColor color) {
    NvgUtils.drawFilledRect(nvg, x, y, width, height, backgroundColor, color);
}

private void renderBorder(long nvg, NVGColor color) {
    NvgUtils.drawStrokedRect(nvg, x, y, width, height, borderColor, BORDER_WIDTH, color);
}
```
