package com.yourorg.elasticcommon.query;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourorg.elasticcommon.exception.EsOperationException;
import com.yourorg.elasticcommon.model.FieldDefinition;
import com.yourorg.elasticcommon.model.IndexTemplate;
import com.yourorg.elasticcommon.model.SearchCriteria;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * Builds an Elasticsearch query dynamically by reading field types from an {@link IndexTemplate}.
 *
 * <p>Query type is chosen per field:
 * <ul>
 *   <li>KEYWORD / BOOLEAN / numeric → {@code term} (single) or {@code terms} (list)</li>
 *   <li>TEXT → {@code match}</li>
 *   <li>DATE / numeric (range map) → {@code range}</li>
 *   <li>fulltextQuery → {@code multi_match} across all TEXT fields</li>
 * </ul>
 */
public class DynamicQueryBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public Query build(IndexTemplate template, SearchCriteria criteria) {
        try {
            ArrayNode mustClauses = MAPPER.createArrayNode();

            if (criteria.getFieldCriteria() != null) {
                for (Map.Entry<String, Object> entry : criteria.getFieldCriteria().entrySet()) {
                    if (entry.getValue() == null) continue;
                    template.getField(entry.getKey()).ifPresent(fieldDef -> {
                        JsonNode clause = buildFieldClause(fieldDef, entry.getValue());
                        if (clause != null) mustClauses.add(clause);
                    });
                }
            }

            if (criteria.getFulltextQuery() != null && !criteria.getFulltextQuery().isBlank()) {
                List<String> fields = (criteria.getFulltextFields() != null && !criteria.getFulltextFields().isEmpty())
                        ? criteria.getFulltextFields()
                        : template.getFulltextFields();
                if (!fields.isEmpty()) {
                    mustClauses.add(buildMultiMatchClause(criteria.getFulltextQuery(), fields));
                }
            }

            ObjectNode boolNode = MAPPER.createObjectNode();
            boolNode.set("must", mustClauses);
            ObjectNode root = MAPPER.createObjectNode();
            root.set("bool", boolNode);

            String queryJson = MAPPER.writeValueAsString(root);
            try (StringReader reader = new StringReader(queryJson)) {
                return Query.of(q -> q.withJson(reader));
            }
        } catch (Exception e) {
            throw new EsOperationException("Failed to build dynamic query from index template", e);
        }
    }

    @SuppressWarnings("unchecked")
    private JsonNode buildFieldClause(FieldDefinition field, Object value) {
        if (value instanceof Map) {
            return buildRangeClause(field.getName(), (Map<String, Object>) value);
        }
        if (value instanceof List) {
            return buildTermsClause(field.getName(), (List<?>) value);
        }
        return switch (field.getType()) {
            case TEXT -> buildMatchClause(field.getName(), value.toString());
            default  -> buildTermClause(field.getName(), value.toString());
        };
    }

    private JsonNode buildTermClause(String fieldName, String value) {
        ObjectNode fieldNode = MAPPER.createObjectNode();
        fieldNode.put(fieldName, value);
        ObjectNode clause = MAPPER.createObjectNode();
        clause.set("term", fieldNode);
        return clause;
    }

    private JsonNode buildTermsClause(String fieldName, List<?> values) {
        ArrayNode valuesArray = MAPPER.createArrayNode();
        values.forEach(v -> valuesArray.add(v.toString()));
        ObjectNode fieldNode = MAPPER.createObjectNode();
        fieldNode.set(fieldName, valuesArray);
        ObjectNode clause = MAPPER.createObjectNode();
        clause.set("terms", fieldNode);
        return clause;
    }

    private JsonNode buildMatchClause(String fieldName, String value) {
        ObjectNode fieldNode = MAPPER.createObjectNode();
        fieldNode.put(fieldName, value);
        ObjectNode clause = MAPPER.createObjectNode();
        clause.set("match", fieldNode);
        return clause;
    }

    private JsonNode buildRangeClause(String fieldName, Map<String, Object> rangeMap) {
        ObjectNode rangeField = MAPPER.createObjectNode();
        rangeMap.forEach((k, v) -> {
            if (v instanceof Number num) {
                rangeField.put(k, num.doubleValue());
            } else {
                rangeField.put(k, v.toString());
            }
        });
        ObjectNode rangeNode = MAPPER.createObjectNode();
        rangeNode.set(fieldName, rangeField);
        ObjectNode clause = MAPPER.createObjectNode();
        clause.set("range", rangeNode);
        return clause;
    }

    private JsonNode buildMultiMatchClause(String queryText, List<String> fields) {
        ArrayNode fieldsArray = MAPPER.createArrayNode();
        fields.forEach(fieldsArray::add);
        ObjectNode mmContent = MAPPER.createObjectNode();
        mmContent.put("query", queryText);
        mmContent.set("fields", fieldsArray);
        ObjectNode clause = MAPPER.createObjectNode();
        clause.set("multi_match", mmContent);
        return clause;
    }
}
