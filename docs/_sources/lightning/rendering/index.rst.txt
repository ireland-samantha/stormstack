Lightning Rendering Engine
==========================

The Lightning Rendering Engine is a Java-based GUI framework for building
game visualization clients, debugging tools, and custom interfaces.

Overview
--------

Built on LWJGL (Lightweight Java Game Library) and NanoVG, the rendering
engine provides:

- OpenGL-based 2D rendering
- Widget component library
- Sprite rendering with shaders
- Headless test framework

Technology Stack
----------------

- **LWJGL 3** - OpenGL bindings for Java
- **NanoVG** - Vector graphics rendering
- **GLFW** - Window and input management

Package Structure
-----------------

The rendering engine is located at ``lightning/rendering/`` with two modules:

**core**
   Main rendering framework:

   - ``render2d/`` - Abstract component interfaces
   - ``render2d/impl/opengl/`` - OpenGL implementations

**test-framework**
   Headless testing utilities:

   - ``testing/`` - Test driver and utilities
   - ``testing/headless/`` - Mock component implementations

Component Library
-----------------

The rendering engine provides these UI components:

Window
~~~~~~

Top-level window container.

.. code-block:: java

   Window window = WindowFactory.create(RendererType.OPENGL)
       .title("Game Viewer")
       .size(800, 600)
       .build();

   window.show();
   window.runEventLoop();

Panel
~~~~~

Container for organizing components.

.. code-block:: java

   Panel panel = factory.createPanel();
   panel.setPosition(10, 10);
   panel.setSize(200, 300);
   panel.setScrollable(true);

Button
~~~~~~

Clickable button with callback.

.. code-block:: java

   Button button = factory.createButton("Click Me");
   button.setOnClick(() -> {
       System.out.println("Button clicked!");
   });

Label
~~~~~

Text display.

.. code-block:: java

   Label label = factory.createLabel("Score: 0");
   label.setFontSize(18);
   label.setColor(Colour.WHITE);

TextField
~~~~~~~~~

Text input field.

.. code-block:: java

   TextField field = factory.createTextField();
   field.setPlaceholder("Enter name...");
   field.setOnTextChanged(text -> {
       System.out.println("Text: " + text);
   });

ListView
~~~~~~~~

Scrollable list with selection.

.. code-block:: java

   ListView<String> list = factory.createListView();
   list.setItems(Arrays.asList("Item 1", "Item 2", "Item 3"));
   list.setOnSelect(item -> {
       System.out.println("Selected: " + item);
   });

TreeView
~~~~~~~~

Hierarchical tree display.

.. code-block:: java

   TreeView tree = factory.createTreeView();
   TreeNode root = factory.createTreeNode("Root");
   TreeNode child = factory.createTreeNode("Child");
   root.addChild(child);
   tree.setRoot(root);

ContextMenu
~~~~~~~~~~~

Right-click popup menu.

.. code-block:: java

   ContextMenu menu = factory.createContextMenu();
   menu.addItem("Delete", () -> handleDelete());
   menu.addItem("Rename", () -> handleRename());

FileDialog
~~~~~~~~~~

File open/save dialogs.

.. code-block:: java

   FileDialog dialog = factory.createFileDialog();
   dialog.setMode(FileDialog.Mode.OPEN);
   dialog.setFilter("*.json");
   String path = dialog.show();

Sprite Rendering
----------------

For game visualization, use the sprite renderer:

.. code-block:: java

   SpriteRenderer renderer = factory.createSpriteRenderer();

   // Load texture
   Texture texture = factory.loadTexture("player.png");

   // Create sprite
   Sprite sprite = factory.createSprite(texture);
   sprite.setPosition(100, 100);
   sprite.setSize(32, 32);
   sprite.setRotation(45); // degrees

   // Render
   renderer.begin();
   renderer.draw(sprite);
   renderer.end();

Shaders
~~~~~~~

Custom shaders are in ``resources/shaders/``:

- ``sprite.vert`` - Vertex shader for sprites
- ``sprite.frag`` - Fragment shader for sprites

Colour Class
------------

Colours use the ``Colour`` class:

.. code-block:: java

   // Predefined colours
   Colour.WHITE
   Colour.BLACK
   Colour.RED

   // Custom colours
   Colour custom = new Colour(0.5f, 0.5f, 0.5f, 1.0f); // RGBA

Input Handling
--------------

Components receive input events via handlers:

.. code-block:: java

   // Mouse input
   window.setMouseInputHandler(new MouseInputHandler() {
       @Override
       public void onMouseClick(int x, int y, int button) {
           // Handle click
       }

       @Override
       public void onMouseMove(int x, int y) {
           // Handle move
       }
   });

   // Keyboard input
   window.setKeyInputHandler(new KeyInputHandler() {
       @Override
       public void onKeyPress(int keyCode) {
           // Handle key press
       }
   });

Key codes are defined in ``InputConstants``.

Example Application
-------------------

Complete example showing a game state viewer:

.. code-block:: java

   public class GameViewer {
       public static void main(String[] args) {
           // Create window
           Window window = WindowFactory.create(RendererType.OPENGL)
               .title("Game State Viewer")
               .size(1024, 768)
               .build();

           ComponentFactory factory = window.getComponentFactory();

           // Create UI
           Panel sidebar = factory.createPanel();
           sidebar.setPosition(0, 0);
           sidebar.setSize(200, 768);

           Label title = factory.createLabel("Entities");
           title.setPosition(10, 10);
           sidebar.add(title);

           ListView<String> entityList = factory.createListView();
           entityList.setPosition(10, 40);
           entityList.setSize(180, 300);
           sidebar.add(entityList);

           window.add(sidebar);

           // Create sprite area
           Panel gameArea = factory.createPanel();
           gameArea.setPosition(200, 0);
           gameArea.setSize(824, 768);

           SpriteRenderer sprites = factory.createSpriteRenderer();
           // ... setup sprites

           window.add(gameArea);

           // Run
           window.show();
           window.runEventLoop();
       }
   }

Headless Testing
----------------

The test framework allows GUI testing without a display:

.. code-block:: java

   import ca.samanthaireland.stormstack.lightning.rendering.testing.*;

   @Test
   void testButtonClick() {
       // Create headless driver
       GuiDriver driver = new HeadlessGuiDriver();

       // Find button
       GuiElement button = driver.findElement(By.text("Submit"));

       // Click and verify
       button.click();

       // Wait for result
       Wait.until(ExpectedConditions.textPresent("Success"));
   }

**Test Driver Features:**

- ``By.text()`` - Find by text content
- ``By.id()`` - Find by component ID
- ``By.type()`` - Find by component type
- ``Actions.click()`` - Simulate click
- ``Actions.type()`` - Simulate typing
- ``ExpectedConditions`` - Wait conditions

Building
--------

The rendering engine is built with Maven:

.. code-block:: bash

   cd lightning/rendering
   mvn clean install

**Dependencies:**

The POM includes LWJGL natives for multiple platforms:
- Windows (x64)
- Linux (x64)
- macOS (x64, ARM64)

Running on macOS
----------------

macOS requires the ``-XstartOnFirstThread`` JVM argument:

.. code-block:: bash

   java -XstartOnFirstThread -jar game-viewer.jar

Integration with Thunder
------------------------

To visualize Thunder Engine snapshots:

1. Connect to snapshot WebSocket
2. Parse snapshot JSON
3. Create sprites for entities
4. Update positions each frame

Example:

.. code-block:: java

   // Connect to snapshot stream
   WebSocketClient ws = new WebSocketClient("ws://localhost:8080/ws/snapshots/1");
   ws.onMessage(json -> {
       Snapshot snapshot = parseSnapshot(json);
       updateSprites(snapshot);
   });

See the ``thunder/engine/adapters/game-sdk`` for client libraries.

Resources
---------

Default resources are in ``resources/``:

- ``textures/`` - Default textures (checkers demo)
- ``shaders/`` - GLSL shaders
- ``fonts/`` - Default TTF font

Load custom resources:

.. code-block:: java

   Texture tex = factory.loadTexture("/path/to/texture.png");
   Font font = factory.loadFont("/path/to/font.ttf");
