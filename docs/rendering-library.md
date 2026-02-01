# Rendering Library

The rendering library (`lightning/rendering/core`) is a custom GUI framework built on LWJGL 3 and NanoVG.

## Architecture

```
lightning/rendering/core/src/main/java/.../rendering/
├── render2d/                   # Component abstractions
│   ├── Window.java             # Window interface
│   ├── WindowBuilder.java      # Factory for windows
│   ├── Panel.java              # Container with title bar
│   ├── Button.java             # Clickable button
│   ├── Label.java              # Text display
│   ├── TextField.java          # Text input
│   ├── TreeView.java           # Hierarchical data display
│   ├── ListView.java           # Scrollable list
│   ├── SpriteRenderer.java     # Game sprite rendering
│   ├── ComponentFactory.java   # Factory interface
│   ├── Colour.java             # Color abstraction
│   ├── ContextMenu.java        # Right-click menu
│   └── impl/opengl/            # OpenGL/NanoVG implementations
│       ├── GLWindow.java
│       ├── GLButton.java
│       ├── GLPanel.java
│       ├── GLTreeView.java
│       ├── GLComponentFactory.java
│       ├── NanoVGRenderer.java
│       └── ...
```

## Key Features

| Feature | Description |
|---------|-------------|
| **68 Java files** | Full-featured GUI framework |
| **NanoVG rendering** | Hardware-accelerated 2D graphics |
| **Component hierarchy** | Panel → children with automatic layout |
| **Event propagation** | Mouse/keyboard events bubble through components |
| **HiDPI support** | Proper pixel ratio handling |
| **Sprite rendering** | Textured sprites with rotation and z-ordering |

## Creating a Window

```java
Window window = WindowBuilder.create()
    .size(1200, 800)
    .title("My Application")
    .build();

Panel panel = new Panel(10, 10, 400, 300);
panel.setTitle("Controls");
panel.addComponent(new Button(10, 40, 100, 30, "Click Me"));
panel.addComponent(new Label(10, 80, "Status: Ready"));

window.addComponent(panel);
window.run();  // Blocks until closed
```

## Components

### Button

```java
Button button = new Button(x, y, width, height, "Label");
button.setOnClick(() -> {
    System.out.println("Button clicked!");
});
```

### TextField

```java
TextField field = new TextField(x, y, width, height);
field.setText("Initial value");
field.setOnChange(newText -> {
    System.out.println("Text changed: " + newText);
});
```

### TreeView

```java
TreeView tree = new TreeView(x, y, width, height);
TreeNode root = new TreeNode("Root");
root.addChild(new TreeNode("Child 1"));
root.addChild(new TreeNode("Child 2"));
tree.setRootNode(root);
```

### ListView

```java
ListView list = new ListView(x, y, width, height);
list.setItems(List.of("Item 1", "Item 2", "Item 3"));
list.setOnSelect(item -> {
    System.out.println("Selected: " + item);
});
```

### Panel

```java
Panel panel = new Panel(x, y, width, height);
panel.setTitle("My Panel");
panel.setDraggable(true);
panel.addComponent(button);
panel.addComponent(label);
```

## Sprite Rendering

```java
SpriteRenderer renderer = new SpriteRenderer(x, y, width, height);

// Add sprites
Sprite sprite = new Sprite(entityId, textureId, x, y, width, height);
sprite.setRotation(45);
sprite.setZIndex(10);
renderer.addSprite(sprite);

// Render in window
window.addComponent(renderer);
```

## Headless Testing

The `lightning/rendering/test-framework` module provides GPU-free testing:

```java
import ca.samanthaireland.stormstack.lightning.rendering.testing.*;
import ca.samanthaireland.stormstack.lightning.rendering.testing.headless.*;

HeadlessWindow window = new HeadlessWindow(800, 600);
HeadlessComponentFactory factory = new HeadlessComponentFactory();

MockButton button = factory.createButton(10, 10, 100, 30, "Save");
window.addComponent(button);

GuiDriver driver = GuiDriver.connect(window);
driver.findElement(By.text("Save")).click();
```

### Locators

| Locator | Description |
|---------|-------------|
| `By.text("exact")` | Match exact text |
| `By.textContaining("partial")` | Match partial text |
| `By.id("component-id")` | Match by component ID |
| `By.type(Button.class)` | Match by component type |
| `By.title("Panel Title")` | Match panel by title |
| `By.and(loc1, loc2)` | Match both conditions |
| `By.or(loc1, loc2)` | Match either condition |
| `.within(By.title("..."))` | Search within container |

### GuiDriver API

```java
// Find and interact
driver.findElement(By.text("Save")).click();
driver.findElement(By.type(TextField.class)).type("Hello");

// Wait for conditions
driver.waitFor().until(ExpectedConditions.elementVisible(By.text("Done")));

// Simulate input
driver.type("Hello World");
driver.pressKey(KeyCodes.ENTER);

// Debug
driver.dumpComponentTree();
```

## Platform Notes

| Platform | Requirement |
|----------|-------------|
| macOS | Add `-XstartOnFirstThread` JVM argument |
| Linux | Requires X11 or Wayland with OpenGL 3.2+ |
| Windows | OpenGL 3.2+ GPU drivers |

## Debug GUI

The debug GUI is built on this framework and runs as a native application connecting to Thunder Engine:

| Panel | Features |
|-------|----------|
| **Entity Inspector** | TreeView of entities/components, real-time WebSocket updates |
| **Command Console** | Send commands with auto-generated parameter forms |
| **Resource Browser** | Upload textures, preview images, attach to entities |
| **Module Manager** | View/upload/reload modules, see enabled match counts |
| **Match Manager** | Create/delete matches, select modules per match |

## Test Framework Components

The `lightning/rendering/test-framework` module provides:

| Class | Purpose |
|-------|---------|
| `HeadlessWindow` | Window that runs without GPU |
| `HeadlessComponentFactory` | Creates mock components |
| `GuiDriver` | Selenium-style driver for GUI testing |
| `GuiElement` | Wrapper for interacting with components |
| `By` | Locator strategies for finding elements |
| `ExpectedConditions` | Wait conditions for async operations |
| `Wait` | Fluent wait API |
| `KeyCodes` | Keyboard constants for simulation |
