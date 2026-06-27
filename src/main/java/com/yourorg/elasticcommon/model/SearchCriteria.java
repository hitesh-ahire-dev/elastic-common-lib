package com.yourorg.elasticcommon.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates all search parameters for a dynamic query.
 * <p>
 * {@code fieldCriteria} maps field names to values:
 * <ul>
 *   <li>Single value → term / match query (based on field type)</li>
 *   <li>List of values → terms query</li>
 *   <li>Map with "gte"/"lte" keys → range query</li>
 * </ul>
 * {@code fulltextQuery} triggers a multi_match across all TEXT fields in the index template.
 */
@Data
@Builder
public class SearchCriteria {
    private Map<String, Object> fieldCriteria;
    private String fulltextQuery;
    private List<String> fulltextFields;
}
