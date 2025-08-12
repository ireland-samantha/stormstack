package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.CommandService.ParameterInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Panel for displaying a dynamic form based on a command's schema.
 *
 * <p>When a command is selected, this panel displays labeled input fields
 * for each parameter defined in the command's schema.
 */
@Slf4j
public class CommandFormPanel extends AbstractWindowComponent {

    private static final int FIELD_HEIGHT = 28;
    private static final int FIELD_SPACING = 8;
    private static final int LABEL_WIDTH = 120;
    private static final int PADDING = 10;

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final Label titleLabel;
    private final Label noCommandLabel;
    private final Button clearButton;
    private final Button sendButton;
    private final Runnable onSendCallback;

    private CommandInfo currentCommand;
    private final List<ParameterField> parameterFields = new ArrayList<>();

    /**
     * Create a command form panel without a send callback.
     */
    public CommandFormPanel(ComponentFactory factory, int x, int y, int width, int height) {
        this(factory, x, y, width, height, null);
    }

    /**
     * Create a command form panel with a send callback.
     */
    public CommandFormPanel(ComponentFactory factory, int x, int y, int width, int height, Runnable onSendCallback) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();
        this.onSendCallback = onSendCallback;

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Command Parameters");

        // Create title label for showing the command name
        titleLabel = factory.createLabel(x + PADDING, y + 35, "No command selected", 14.0f);
        titleLabel.setTextColor(colours.textPrimary());

        // Create placeholder when no command is selected
        noCommandLabel = factory.createLabel(x + PADDING, y + 70, "Select a command from the list", 12.0f);
        noCommandLabel.setTextColor(colours.textSecondary());

        // Create clear button (top right)
        clearButton = factory.createButton(x + width - 70, y + 30, 60, 24, "Clear");
        clearButton.setOnClick(this::clearForm);

        // Create send button (will be positioned dynamically below parameters)
        sendButton = factory.createButton(x + PADDING, y + height - 50, width - PADDING * 2, 32, "Send Command");
        sendButton.setBackgroundColor(colours.success());
        sendButton.setOnClick(() -> {
            if (onSendCallback != null) {
                onSendCallback.run();
            }
        });

        visualPanel.addChild((WindowComponent) titleLabel);
        visualPanel.addChild((WindowComponent) noCommandLabel);
        visualPanel.addChild((WindowComponent) clearButton);
        visualPanel.addChild((WindowComponent) sendButton);
    }

    /**
     * Set the command to display the form for.
     *
     * @param command the command info, or null to clear
     */
    public void setCommand(CommandInfo command) {
        this.currentCommand = command;
        rebuildForm();
    }

    /**
     * Get the current command, if any.
     */
    public CommandInfo getCurrentCommand() {
        return currentCommand;
    }

    /**
     * Clear all input fields.
     */
    public void clearForm() {
        for (ParameterField field : parameterFields) {
            field.textField.setText("");
        }
    }

    /**
     * Get the parameter values as a map.
     *
     * @return map of parameter name to value (parsed to appropriate type)
     */
    public Map<String, Object> getParameterValues() {
        Map<String, Object> values = new HashMap<>();
        for (ParameterField field : parameterFields) {
            String text = field.textField.getText().trim();
            if (!text.isEmpty()) {
                Object value = parseValue(text, field.param.type());
                values.put(field.param.name(), value);
            }
        }
        return values;
    }

    /**
     * Parse a string value to the appropriate type based on the parameter type.
     */
    private Object parseValue(String text, String type) {
        try {
            return switch (type.toLowerCase()) {
                case "long", "int", "integer" -> Long.parseLong(text);
                case "double", "float" -> Double.parseDouble(text);
                case "boolean", "bool" -> Boolean.parseBoolean(text);
                default -> text; // String or unknown type
            };
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value '{}' as {}", text, type);
            return text;
        }
    }

    private void rebuildForm() {
        // Remove old parameter fields from visual panel
        for (ParameterField field : parameterFields) {
            visualPanel.removeChild((WindowComponent) field.label);
            visualPanel.removeChild((WindowComponent) field.textField);
            visualPanel.removeChild((WindowComponent) field.typeHint);
        }
        parameterFields.clear();

        if (currentCommand == null) {
            titleLabel.setText("No command selected");
            noCommandLabel.setVisible(true);
            // Keep send button visible but it will have no effect without a command
            return;
        }

        titleLabel.setText(currentCommand.name());
        noCommandLabel.setVisible(false);

        // Create fields for each parameter
        List<ParameterInfo> params = currentCommand.parameters();
        int fieldY = y + 60;
        int fieldWidth = width - LABEL_WIDTH - PADDING * 3;

        for (ParameterInfo param : params) {
            // Create label
            Label label = factory.createLabel(x + PADDING, fieldY + 6, param.name() + ":", 12.0f);
            label.setTextColor(colours.textPrimary());

            // Create text field
            TextField textField = factory.createTextField(
                    x + PADDING + LABEL_WIDTH,
                    fieldY,
                    fieldWidth,
                    FIELD_HEIGHT
            );
            textField.setPlaceholder(getPlaceholderForType(param.type()));

            // Add type hint as tooltip-style label
            Label typeHint = factory.createLabel(
                    x + PADDING + LABEL_WIDTH + fieldWidth + 5,
                    fieldY + 6,
                    "(" + param.type() + ")",
                    10.0f
            );
            typeHint.setTextColor(colours.textSecondary());

            visualPanel.addChild((WindowComponent) label);
            visualPanel.addChild((WindowComponent) textField);
            visualPanel.addChild((WindowComponent) typeHint);

            parameterFields.add(new ParameterField(param, label, textField, typeHint));
            fieldY += FIELD_HEIGHT + FIELD_SPACING;
        }

        // Position the Send button below the parameters
        int sendButtonY = fieldY + FIELD_SPACING;
        ((WindowComponent) sendButton).setPosition(x + PADDING, sendButtonY);

        // Show message if no parameters
        if (params.isEmpty()) {
            noCommandLabel.setText("This command has no parameters");
            noCommandLabel.setVisible(true);
            // Position send button below the message
            ((WindowComponent) sendButton).setPosition(x + PADDING, y + 100);
        }
    }

    private String getPlaceholderForType(String type) {
        return switch (type.toLowerCase()) {
            case "long", "int", "integer" -> "e.g., 100";
            case "double", "float" -> "e.g., 3.14";
            case "boolean", "bool" -> "true or false";
            case "string" -> "text value";
            default -> "value";
        };
    }

    /**
     * Get the number of parameter fields.
     */
    public int getFieldCount() {
        return parameterFields.size();
    }

    /**
     * Get a parameter field's text field by index (for testing).
     */
    public TextField getField(int index) {
        if (index >= 0 && index < parameterFields.size()) {
            return parameterFields.get(index).textField;
        }
        return null;
    }

    /**
     * Get a parameter field's text field by name (for testing).
     */
    public TextField getFieldByName(String paramName) {
        for (ParameterField field : parameterFields) {
            if (field.param.name().equals(paramName)) {
                return field.textField;
            }
        }
        return null;
    }

    // Delegate rendering and input to the visual panel

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        return visible && contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        return visible && visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        return visible && contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        return visible && visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        return visible && visualPanel.onCharInput(codepoint);
    }

    /**
     * Internal record to track parameter field components.
     */
    private record ParameterField(ParameterInfo param, Label label, TextField textField, Label typeHint) {}
}
