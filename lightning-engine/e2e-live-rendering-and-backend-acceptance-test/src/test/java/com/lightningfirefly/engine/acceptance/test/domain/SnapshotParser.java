package com.lightningfirefly.engine.acceptance.test.domain;

import com.lightningfirefly.game.orchestrator.Snapshot;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses snapshot JSON responses from the backend.
 *
 * <p>Handles the nested module structure:
 * <pre>{@code
 * {
 *   "data": {
 *     "ModuleName": {
 *       "COMPONENT_NAME": [value1, value2, ...]
 *     }
 *   }
 * }
 * }</pre>
 */
public class SnapshotParser {

    private final Map<String, Map<String, List<Float>>> moduleData = new HashMap<>();

    private SnapshotParser() {}

    /**
     * Parse a snapshot JSON string.
     */
    public static SnapshotParser parse(String json) {
        SnapshotParser parser = new SnapshotParser();
        parser.parseJson(json);
        return parser;
    }

    /**
     * Get component values from a module.
     */
    public List<Float> getComponent(String moduleName, String componentName) {
        Map<String, List<Float>> module = moduleData.get(moduleName);
        if (module == null) return List.of();
        return module.getOrDefault(componentName, List.of());
    }

    /**
     * Get the first component value.
     */
    public Optional<Float> getFirstValue(String moduleName, String componentName) {
        List<Float> values = getComponent(moduleName, componentName);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * Get a value at specific index.
     */
    public Optional<Float> getValue(String moduleName, String componentName, int index) {
        List<Float> values = getComponent(moduleName, componentName);
        return index < values.size() ? Optional.of(values.get(index)) : Optional.empty();
    }

    /**
     * Get all module names.
     */
    public Set<String> modules() {
        return moduleData.keySet();
    }

    /**
     * Get all module names (alias for modules()).
     */
    public Set<String> getModuleNames() {
        return modules();
    }

    /**
     * Check if a module exists.
     */
    public boolean hasModule(String moduleName) {
        return moduleData.containsKey(moduleName);
    }

    /**
     * Get the first component value (alias for getFirstValue).
     */
    public Optional<Float> getComponentValue(String moduleName, String componentName) {
        return getFirstValue(moduleName, componentName);
    }

    /**
     * Get a value at specific index (alias for getValue).
     */
    public Optional<Float> getComponentValue(String moduleName, String componentName, int index) {
        return getValue(moduleName, componentName, index);
    }

    /**
     * Convert to a Snapshot record for rendering.
     */
    public Snapshot toSnapshot() {
        Map<String, List<Float>> merged = new HashMap<>();

        // Get ENTITY_ID from SpawnModule
        List<Float> entityIds = getComponent("SpawnModule", "ENTITY_ID");
        if (!entityIds.isEmpty()) merged.put("ENTITY_ID", entityIds);

        // Get position from MoveModule
        List<Float> posX = getComponent("MoveModule", "POSITION_X");
        List<Float> posY = getComponent("MoveModule", "POSITION_Y");
        if (!posX.isEmpty()) merged.put("POSITION_X", posX);
        if (!posY.isEmpty()) merged.put("POSITION_Y", posY);

        // Get resource from RenderModule
        List<Float> resourceIds = getComponent("RenderModule", "RESOURCE_ID");
        if (!resourceIds.isEmpty()) merged.put("RESOURCE_ID", resourceIds);

        // Include all components
        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : moduleData.entrySet()) {
            for (Map.Entry<String, List<Float>> componentEntry : moduleEntry.getValue().entrySet()) {
                merged.putIfAbsent(componentEntry.getKey(), componentEntry.getValue());
            }
        }

        return new Snapshot(Map.of("MergedData", merged));
    }

    private void parseJson(String json) {
        int dataStart = json.indexOf("\"data\"");
        if (dataStart == -1) {
            parseModules(json);
            return;
        }
        parseModules(json.substring(dataStart));
    }

    private void parseModules(String json) {
        Pattern modulePattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\{\\s*\"([A-Z_]+)\"\\s*:\\s*\\[");
        Matcher moduleMatcher = modulePattern.matcher(json);

        Set<String> foundModules = new HashSet<>();
        while (moduleMatcher.find()) {
            String potentialModule = moduleMatcher.group(1);
            if (!potentialModule.equals("data") && !potentialModule.equals("matchId") &&
                !potentialModule.equals("tick") && !potentialModule.equals("snapshot")) {
                foundModules.add(potentialModule);
            }
        }

        for (String moduleName : foundModules) {
            String moduleContent = extractModuleContent(json, moduleName);
            if (moduleContent != null) {
                Map<String, List<Float>> componentData = parseComponents(moduleContent);
                if (!componentData.isEmpty()) {
                    moduleData.put(moduleName, componentData);
                }
            }
        }

        if (moduleData.isEmpty()) {
            parseSimpleFormat(json);
        }
    }

    private String extractModuleContent(String json, String moduleName) {
        String moduleStart = "\"" + moduleName + "\"";
        int startIdx = json.indexOf(moduleStart);
        if (startIdx == -1) return null;

        int braceStart = json.indexOf('{', startIdx + moduleStart.length());
        if (braceStart == -1) return null;

        int depth = 1;
        int pos = braceStart + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            pos++;
        }

        return depth == 0 ? json.substring(braceStart + 1, pos - 1) : null;
    }

    private Map<String, List<Float>> parseComponents(String moduleContent) {
        Map<String, List<Float>> components = new HashMap<>();
        Pattern componentPattern = Pattern.compile("\"([A-Z_]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher componentMatcher = componentPattern.matcher(moduleContent);

        while (componentMatcher.find()) {
            String componentName = componentMatcher.group(1);
            String valuesStr = componentMatcher.group(2);
            List<Float> values = parseFloatArray(valuesStr);
            if (!values.isEmpty()) {
                components.put(componentName, values);
            }
        }

        return components;
    }

    private List<Float> parseFloatArray(String valuesStr) {
        List<Float> values = new ArrayList<>();
        if (valuesStr == null || valuesStr.isBlank()) return values;

        for (String val : valuesStr.split(",")) {
            val = val.trim();
            if (!val.isEmpty()) {
                try {
                    values.add(Float.parseFloat(val));
                } catch (NumberFormatException e) {
                    // Skip non-numeric values
                }
            }
        }
        return values;
    }

    private void parseSimpleFormat(String json) {
        Map<String, List<Float>> defaultModule = new HashMap<>();
        Pattern componentPattern = Pattern.compile("\"([A-Z_]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher componentMatcher = componentPattern.matcher(json);

        while (componentMatcher.find()) {
            String componentName = componentMatcher.group(1);
            String valuesStr = componentMatcher.group(2);
            List<Float> values = parseFloatArray(valuesStr);
            if (!values.isEmpty()) {
                defaultModule.put(componentName, values);
            }
        }

        if (!defaultModule.isEmpty()) {
            moduleData.put("DefaultModule", defaultModule);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SnapshotParser{\n");
        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : moduleData.entrySet()) {
            sb.append("  ").append(moduleEntry.getKey()).append(": {\n");
            for (Map.Entry<String, List<Float>> componentEntry : moduleEntry.getValue().entrySet()) {
                sb.append("    ").append(componentEntry.getKey()).append(": ")
                  .append(componentEntry.getValue()).append("\n");
            }
            sb.append("  }\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
