package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.game.orchestrator.Snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing snapshot JSON responses from the backend.
 *
 * <p>Parses the JSON structure:
 * <pre>{@code
 * {
 *   "matchId": 1,
 *   "tick": 3,
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

    private SnapshotParser() {
    }

    /**
     * Parse a snapshot JSON string.
     *
     * @param json the JSON string from the backend
     * @return a SnapshotParser instance for extracting data
     */
    public static SnapshotParser parse(String json) {
        SnapshotParser parser = new SnapshotParser();
        parser.parseJson(json);
        return parser;
    }

    /**
     * Get a component's values from a specific module.
     *
     * @param moduleName    the module name (e.g., "MoveModule", "RenderModule")
     * @param componentName the component name (e.g., "POSITION_X", "RESOURCE_ID")
     * @return list of float values, or empty list if not found
     */
    public List<Float> getComponent(String moduleName, String componentName) {
        Map<String, List<Float>> module = moduleData.get(moduleName);
        if (module == null) {
            return List.of();
        }
        return module.getOrDefault(componentName, List.of());
    }

    /**
     * Get a single component value from a specific module at index 0.
     *
     * @param moduleName    the module name
     * @param componentName the component name
     * @return the value, or empty if not found
     */
    public Optional<Float> getComponentValue(String moduleName, String componentName) {
        List<Float> values = getComponent(moduleName, componentName);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * Get a component value at a specific index.
     *
     * @param moduleName    the module name
     * @param componentName the component name
     * @param index         the entity index
     * @return the value, or empty if not found
     */
    public Optional<Float> getComponentValue(String moduleName, String componentName, int index) {
        List<Float> values = getComponent(moduleName, componentName);
        return index < values.size() ? Optional.of(values.get(index)) : Optional.empty();
    }

    /**
     * Get all module names in the snapshot.
     *
     * @return set of module names
     */
    public java.util.Set<String> getModuleNames() {
        return moduleData.keySet();
    }

    /**
     * Check if a module exists in the snapshot.
     *
     * @param moduleName the module name
     * @return true if the module has data
     */
    public boolean hasModule(String moduleName) {
        return moduleData.containsKey(moduleName);
    }

    /**
     * Convert to a Snapshot record for use with SpriteSnapshotMapper.
     * Merges data from all modules into a single map.
     *
     * @return Snapshot record
     */
    public Snapshot toSnapshot() {
        // Merge all module data, prioritizing specific modules for certain components
        Map<String, List<Float>> merged = new HashMap<>();

        // Get ENTITY_ID from SpawnModule (primary source)
        List<Float> entityIds = getComponent("SpawnModule", "ENTITY_ID");
        if (!entityIds.isEmpty()) {
            merged.put("ENTITY_ID", entityIds);
        }

        // Get position from MoveModule
        List<Float> posX = getComponent("MoveModule", "POSITION_X");
        List<Float> posY = getComponent("MoveModule", "POSITION_Y");
        if (!posX.isEmpty()) merged.put("POSITION_X", posX);
        if (!posY.isEmpty()) merged.put("POSITION_Y", posY);

        // Get resource ID from RenderModule
        List<Float> resourceIds = getComponent("RenderModule", "RESOURCE_ID");
        if (!resourceIds.isEmpty()) merged.put("RESOURCE_ID", resourceIds);

        // Include all components from all modules for completeness
        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : moduleData.entrySet()) {
            for (Map.Entry<String, List<Float>> componentEntry : moduleEntry.getValue().entrySet()) {
                merged.putIfAbsent(componentEntry.getKey(), componentEntry.getValue());
            }
        }

        return new Snapshot(Map.of("MergedData", merged));
    }

    /**
     * Parse the JSON string and populate moduleData.
     */
    private void parseJson(String json) {
        // Find the "data" section
        int dataStart = json.indexOf("\"data\"");
        if (dataStart == -1) {
            // Try without "data" wrapper (old format)
            parseModules(json);
            return;
        }

        // Extract from "data" onwards
        String dataSection = json.substring(dataStart);
        parseModules(dataSection);
    }

    /**
     * Parse module data from JSON.
     * Expected format: "data":{"ModuleName":{"COMPONENT":[values],...},...}
     */
    private void parseModules(String json) {
        // Find each module by looking for patterns like "ModuleName":{"COMPONENT":[
        // Module names typically end with "Module" but we also handle others like "CheckersModule"
        Pattern modulePattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\{\\s*\"([A-Z_]+)\"\\s*:\\s*\\[");
        Matcher moduleMatcher = modulePattern.matcher(json);

        java.util.Set<String> foundModules = new java.util.HashSet<>();
        while (moduleMatcher.find()) {
            String potentialModule = moduleMatcher.group(1);
            // Skip non-module keys like "data", "matchId", "tick"
            if (potentialModule.equals("data") || potentialModule.equals("matchId") ||
                potentialModule.equals("tick") || potentialModule.equals("snapshot")) {
                continue;
            }
            foundModules.add(potentialModule);
        }

        // Extract each module's content
        for (String moduleName : foundModules) {
            String moduleContent = extractModuleContent(json, moduleName);
            if (moduleContent != null) {
                Map<String, List<Float>> componentData = parseComponents(moduleContent);
                if (!componentData.isEmpty()) {
                    moduleData.put(moduleName, componentData);
                }
            }
        }

        // If no modules found with pattern, try simpler parsing
        if (moduleData.isEmpty()) {
            parseSimpleFormat(json);
        }
    }

    /**
     * Extract the content of a specific module from JSON.
     */
    private String extractModuleContent(String json, String moduleName) {
        // Find the start of this module
        String moduleStart = "\"" + moduleName + "\"";
        int startIdx = json.indexOf(moduleStart);
        if (startIdx == -1) return null;

        // Find the opening brace
        int braceStart = json.indexOf('{', startIdx + moduleStart.length());
        if (braceStart == -1) return null;

        // Find the matching closing brace
        int depth = 1;
        int pos = braceStart + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            pos++;
        }

        if (depth == 0) {
            return json.substring(braceStart + 1, pos - 1);
        }
        return null;
    }

    /**
     * Parse component arrays from module content.
     */
    private Map<String, List<Float>> parseComponents(String moduleContent) {
        Map<String, List<Float>> components = new HashMap<>();

        // Pattern: "COMPONENT_NAME": [values...]
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

    /**
     * Parse a comma-separated list of float values.
     */
    private List<Float> parseFloatArray(String valuesStr) {
        List<Float> values = new ArrayList<>();
        if (valuesStr == null || valuesStr.isBlank()) {
            return values;
        }

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

    /**
     * Fallback parsing for simpler JSON formats.
     */
    private void parseSimpleFormat(String json) {
        Map<String, List<Float>> defaultModule = new HashMap<>();

        // Parse any component arrays found
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
        StringBuilder sb = new StringBuilder("SnapshotParser{\n");
        for (Map.Entry<String, Map<String, List<Float>>> module : moduleData.entrySet()) {
            sb.append("  ").append(module.getKey()).append(": {\n");
            for (Map.Entry<String, List<Float>> component : module.getValue().entrySet()) {
                sb.append("    ").append(component.getKey()).append(": ").append(component.getValue()).append("\n");
            }
            sb.append("  }\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
