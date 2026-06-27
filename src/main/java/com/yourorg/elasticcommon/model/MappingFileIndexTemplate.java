package com.yourorg.elasticcommon.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.elasticcommon.exception.EsOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base class for index templates loaded from a classpath JSON mapping file.
 * <p>
 * Supported JSON structure:
 * <pre>
 * {
 *   "name": "my-template",
 *   "index_patterns": ["my-index-*"],
 *   "settings": { ... },
 *   "mappings": {
 *     "properties": {
 *       "fieldName": { "type": "keyword|text|date|integer|long|float|double|boolean",
 *                      "analyzer": "...",   // optional
 *                      "format":   "...",   // optional, for date fields
 *                      "searchable": true,  // optional override
 *                      "sortable":  true    // optional override
 *                    }
 *     }
 *   }
 * }
 * </pre>
 * {@code sortable} is inferred from the field type when not specified
 * (false for TEXT/NESTED/OBJECT, true otherwise).
 */
public abstract class MappingFileIndexTemplate implements IndexTemplate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String templateName;
    private final List<String> indexPatterns;
    private final String rawSettingsJson;
    private final String rawMappingsJson;
    private final List<FieldDefinition> fields;
    private final Map<String, FieldDefinition> fieldMap;

    protected MappingFileIndexTemplate(String mappingResourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(mappingResourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Mapping file not found on classpath: " + mappingResourcePath);
            }
            JsonNode root = MAPPER.readTree(is);
            this.templateName    = root.path("name").asText(mappingResourcePath);
            this.indexPatterns   = parseIndexPatterns(root);
            this.rawSettingsJson = nodeToJson(root.path("settings"));
            this.rawMappingsJson = nodeToJson(root.path("mappings"));
            this.fields          = Collections.unmodifiableList(parseFields(root, mappingResourcePath));
            this.fieldMap        = this.fields.stream()
                    .collect(Collectors.toUnmodifiableMap(FieldDefinition::getName, f -> f));
        } catch (IOException e) {
            throw new EsOperationException("Failed to parse mapping file: " + mappingResourcePath, e);
        }
    }

    public String getTemplateName() {
        return templateName;
    }

    public List<String> getIndexPatterns() {
        return indexPatterns;
    }

    public String getRawSettingsJson() {
        return rawSettingsJson;
    }

    public String getRawMappingsJson() {
        return rawMappingsJson;
    }

    @Override
    public List<FieldDefinition> getFields() {
        return fields;
    }

    @Override
    public Optional<FieldDefinition> getField(String name) {
        return Optional.ofNullable(fieldMap.get(name));
    }

    private String nodeToJson(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseIndexPatterns(JsonNode root) {
        List<String> patterns = new ArrayList<>();
        JsonNode patternsNode = root.path("index_patterns");
        if (patternsNode.isArray()) {
            patternsNode.forEach(p -> patterns.add(p.asText()));
        }
        return Collections.unmodifiableList(patterns);
    }

    private List<FieldDefinition> parseFields(JsonNode root, String resourcePath) {
        JsonNode properties = root.path("mappings").path("properties");
        if (properties.isMissingNode()) {
            properties = root.path("properties");
        }
        if (properties.isMissingNode()) {
            throw new IllegalArgumentException("No 'properties' section found in mapping: " + resourcePath);
        }
        List<FieldDefinition> result = new ArrayList<>();
        properties.fields().forEachRemaining(entry ->
                result.add(buildFieldDefinition(entry.getKey(), entry.getValue())));
        return result;
    }

    private FieldDefinition buildFieldDefinition(String name, JsonNode config) {
        String esType = config.path("type").asText("keyword").toLowerCase();
        FieldType type = parseFieldType(esType);
        String analyzer   = config.has("analyzer")   ? config.get("analyzer").asText()        : null;
        String format     = config.has("format")     ? config.get("format").asText()           : null;
        boolean searchable = config.has("searchable") ? config.get("searchable").asBoolean()   : true;
        boolean sortable   = config.has("sortable")   ? config.get("sortable").asBoolean()     : inferSortable(type);

        return FieldDefinition.builder()
                .name(name)
                .type(type)
                .analyzer(analyzer)
                .format(format)
                .searchable(searchable)
                .sortable(sortable)
                .build();
    }

    private FieldType parseFieldType(String esType) {
        return switch (esType) {
            case "text"              -> FieldType.TEXT;
            case "date"              -> FieldType.DATE;
            case "integer", "int"    -> FieldType.INTEGER;
            case "long"              -> FieldType.LONG;
            case "float"             -> FieldType.FLOAT;
            case "double"            -> FieldType.DOUBLE;
            case "boolean", "bool"   -> FieldType.BOOLEAN;
            case "nested"            -> FieldType.NESTED;
            case "object"            -> FieldType.OBJECT;
            default                  -> FieldType.KEYWORD;
        };
    }

    private boolean inferSortable(FieldType type) {
        return switch (type) {
            case TEXT, NESTED, OBJECT -> false;
            default                   -> true;
        };
    }
}
