package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.CommandService.ParameterInfo;
import com.lightningfirefly.engine.rendering.render2d.TextField;
import com.lightningfirefly.engine.rendering.testing.headless.HeadlessComponentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommandFormPanel.
 */
@DisplayName("CommandFormPanel")
class CommandFormPanelTest {

    private HeadlessComponentFactory factory;
    private CommandFormPanel formPanel;

    @BeforeEach
    void setUp() {
        factory = HeadlessComponentFactory.getInstance();
        formPanel = new CommandFormPanel(factory, 0, 0, 350, 400);
    }

    @Test
    @DisplayName("initially shows no command selected")
    void initiallyShowsNoCommandSelected() {
        assertThat(formPanel.getCurrentCommand()).isNull();
        assertThat(formPanel.getFieldCount()).isZero();
    }

    @Test
    @DisplayName("creates fields for command parameters")
    void createsFieldsForCommandParameters() {
        // Given a command with parameters
        CommandInfo command = new CommandInfo("move", List.of(
                new ParameterInfo("targetX", "long"),
                new ParameterInfo("targetY", "long")
        ));

        // When setting the command
        formPanel.setCommand(command);

        // Then fields are created for each parameter
        assertThat(formPanel.getCurrentCommand()).isEqualTo(command);
        assertThat(formPanel.getFieldCount()).isEqualTo(2);
        assertThat(formPanel.getFieldByName("targetX")).isNotNull();
        assertThat(formPanel.getFieldByName("targetY")).isNotNull();
    }

    @Test
    @DisplayName("clears fields when command is set to null")
    void clearsFieldsWhenCommandIsNull() {
        // Given a command was set
        CommandInfo command = new CommandInfo("spawn", List.of(
                new ParameterInfo("entityType", "String")
        ));
        formPanel.setCommand(command);
        assertThat(formPanel.getFieldCount()).isEqualTo(1);

        // When setting null
        formPanel.setCommand(null);

        // Then fields are cleared
        assertThat(formPanel.getCurrentCommand()).isNull();
        assertThat(formPanel.getFieldCount()).isZero();
    }

    @Test
    @DisplayName("handles command with no parameters")
    void handlesCommandWithNoParameters() {
        // Given a command with no parameters
        CommandInfo command = new CommandInfo("pause", List.of());

        // When setting the command
        formPanel.setCommand(command);

        // Then no fields are created
        assertThat(formPanel.getCurrentCommand()).isEqualTo(command);
        assertThat(formPanel.getFieldCount()).isZero();
    }

    @Test
    @DisplayName("getParameterValues returns entered values")
    void getParameterValuesReturnsEnteredValues() {
        // Given a command with parameters
        CommandInfo command = new CommandInfo("move", List.of(
                new ParameterInfo("targetX", "long"),
                new ParameterInfo("targetY", "long")
        ));
        formPanel.setCommand(command);

        // When entering values
        TextField targetXField = formPanel.getFieldByName("targetX");
        TextField targetYField = formPanel.getFieldByName("targetY");
        targetXField.setText("100");
        targetYField.setText("200");

        // Then getParameterValues returns parsed values
        Map<String, Object> values = formPanel.getParameterValues();
        assertThat(values).hasSize(2);
        assertThat(values.get("targetX")).isEqualTo(100L);
        assertThat(values.get("targetY")).isEqualTo(200L);
    }

    @Test
    @DisplayName("parses different types correctly")
    void parsesDifferentTypesCorrectly() {
        // Given a command with various parameter types
        CommandInfo command = new CommandInfo("configure", List.of(
                new ParameterInfo("intValue", "int"),
                new ParameterInfo("floatValue", "double"),
                new ParameterInfo("boolValue", "boolean"),
                new ParameterInfo("stringValue", "String")
        ));
        formPanel.setCommand(command);

        // When entering values
        formPanel.getFieldByName("intValue").setText("42");
        formPanel.getFieldByName("floatValue").setText("3.14");
        formPanel.getFieldByName("boolValue").setText("true");
        formPanel.getFieldByName("stringValue").setText("hello");

        // Then values are parsed to correct types
        Map<String, Object> values = formPanel.getParameterValues();
        assertThat(values.get("intValue")).isEqualTo(42L);
        assertThat(values.get("floatValue")).isEqualTo(3.14);
        assertThat(values.get("boolValue")).isEqualTo(true);
        assertThat(values.get("stringValue")).isEqualTo("hello");
    }

    @Test
    @DisplayName("skips empty fields in parameter values")
    void skipsEmptyFieldsInParameterValues() {
        // Given a command with parameters
        CommandInfo command = new CommandInfo("move", List.of(
                new ParameterInfo("x", "long"),
                new ParameterInfo("y", "long")
        ));
        formPanel.setCommand(command);

        // When only entering one value
        formPanel.getFieldByName("x").setText("50");
        // y is left empty

        // Then only non-empty values are returned
        Map<String, Object> values = formPanel.getParameterValues();
        assertThat(values).hasSize(1);
        assertThat(values).containsKey("x");
        assertThat(values).doesNotContainKey("y");
    }

    @Test
    @DisplayName("clearForm clears all field values")
    void clearFormClearsAllFieldValues() {
        // Given a command with parameters and entered values
        CommandInfo command = new CommandInfo("test", List.of(
                new ParameterInfo("param1", "String"),
                new ParameterInfo("param2", "String")
        ));
        formPanel.setCommand(command);
        formPanel.getFieldByName("param1").setText("value1");
        formPanel.getFieldByName("param2").setText("value2");

        // When clearing the form
        formPanel.clearForm();

        // Then all fields are empty
        assertThat(formPanel.getFieldByName("param1").getText()).isEmpty();
        assertThat(formPanel.getFieldByName("param2").getText()).isEmpty();
        assertThat(formPanel.getParameterValues()).isEmpty();
    }

    @Test
    @DisplayName("rebuilds form when new command is selected")
    void rebuildsFormWhenNewCommandSelected() {
        // Given first command is selected
        CommandInfo command1 = new CommandInfo("cmd1", List.of(
                new ParameterInfo("a", "int")
        ));
        formPanel.setCommand(command1);
        formPanel.getFieldByName("a").setText("123");

        // When selecting a different command
        CommandInfo command2 = new CommandInfo("cmd2", List.of(
                new ParameterInfo("x", "long"),
                new ParameterInfo("y", "long")
        ));
        formPanel.setCommand(command2);

        // Then form is rebuilt with new fields
        assertThat(formPanel.getCurrentCommand()).isEqualTo(command2);
        assertThat(formPanel.getFieldCount()).isEqualTo(2);
        assertThat(formPanel.getFieldByName("a")).isNull(); // Old field gone
        assertThat(formPanel.getFieldByName("x")).isNotNull();
        assertThat(formPanel.getFieldByName("y")).isNotNull();
    }

    @Test
    @DisplayName("getField by index works correctly")
    void getFieldByIndexWorksCorrectly() {
        // Given a command with parameters
        CommandInfo command = new CommandInfo("test", List.of(
                new ParameterInfo("first", "int"),
                new ParameterInfo("second", "int")
        ));
        formPanel.setCommand(command);

        // Then getField by index works
        assertThat(formPanel.getField(0)).isNotNull();
        assertThat(formPanel.getField(1)).isNotNull();
        assertThat(formPanel.getField(2)).isNull(); // Out of bounds
        assertThat(formPanel.getField(-1)).isNull(); // Negative
    }
}
