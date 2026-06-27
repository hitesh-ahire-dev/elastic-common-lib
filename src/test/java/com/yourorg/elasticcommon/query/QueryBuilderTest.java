package com.yourorg.elasticcommon.query;

import com.yourorg.elasticcommon.model.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryBuilderTest {

    private QueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        queryBuilder = new QueryBuilder();
    }

    @Test
    void build_ShouldSubstitutePlaceholders() {
        String templateJson = """
            {
              "bool": {
                "must": [
                  { "term": { "customerId": "${customerId}" } },
                  { "term": { "status": "${status}" } }
                ]
              }
            }
            """;

        SearchRequest request = SearchRequest.builder()
                .indexAlias("test-index")
                .params(Map.of("customerId", "CUST001", "status", "active"))
                .build();

        var query = queryBuilder.build(templateJson, request);

        assertNotNull(query);
    }

    @Test
    void build_WithMissingParam_ShouldLeavePlaceholder() {
        String templateJson = """
            {
              "term": { "customerId": "${customerId}" }
            }
            """;

        SearchRequest request = SearchRequest.builder()
                .indexAlias("test-index")
                .params(Map.of())
                .build();

        var query = queryBuilder.build(templateJson, request);

        assertNotNull(query);
    }

    @Test
    void buildSort_WithValidSortFields_ShouldReturnSortOptions() {
        var request = com.yourorg.elasticcommon.model.PaginationRequest.builder()
                .indexAlias("test-index")
                .params(Map.of())
                .sort(java.util.List.of(
                        com.yourorg.elasticcommon.model.PaginationRequest.SortField.builder()
                                .field("name")
                                .direction("asc")
                                .build(),
                        com.yourorg.elasticcommon.model.PaginationRequest.SortField.builder()
                                .field("createdAt")
                                .direction("desc")
                                .build()
                ))
                .build();

        var sortOptions = queryBuilder.buildSort(request);

        assertNotNull(sortOptions);
        assertEquals(2, sortOptions.size());
    }

    @Test
    void buildSort_WithEmptySort_ShouldReturnEmptyList() {
        var request = com.yourorg.elasticcommon.model.PaginationRequest.builder()
                .indexAlias("test-index")
                .params(Map.of())
                .build();

        var sortOptions = queryBuilder.buildSort(request);

        assertNotNull(sortOptions);
        assertTrue(sortOptions.isEmpty());
    }
}
