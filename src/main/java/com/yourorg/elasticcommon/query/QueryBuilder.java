package com.yourorg.elasticcommon.query;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.SortOptions;
import com.yourorg.elasticcommon.model.PaginationRequest;
import com.yourorg.elasticcommon.model.SearchRequest;
import org.apache.commons.text.StringSubstitutor;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

public class QueryBuilder {

    public Query build(String templateJson, SearchRequest request) {
        Map<String, Object> params = request.getParams();
        StringSubstitutor substitutor = new StringSubstitutor(params);
        String resolved = substitutor.replace(templateJson);

        try {
            return Query.of(q -> q.withJson(new StringReader(resolved)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse query JSON: " + resolved, e);
        }
    }

    public List<SortOptions> buildSort(PaginationRequest request) {
        if (request.getSort() == null || request.getSort().isEmpty()) {
            return List.of();
        }

        return request.getSort().stream()
                .map(sortField -> SortOptions.of(s -> s
                        .field(f -> f
                                .field(sortField.getField())
                                .order(parseSortOrder(sortField.getDirection())))))
                .toList();
    }

    private SortOrder parseSortOrder(String direction) {
        if (direction == null || direction.isBlank()) {
            return SortOrder.Asc;
        }
        return switch (direction.toLowerCase()) {
            case "desc", "descending" -> SortOrder.Desc;
            default -> SortOrder.Asc;
        };
    }
}
