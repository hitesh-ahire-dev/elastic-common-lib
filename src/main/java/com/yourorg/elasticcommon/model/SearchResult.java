package com.yourorg.elasticcommon.model;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.SearchTemplateResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult<T> {
    private List<T> data;
    private long totalCount;
    private List<String> searchAfter;

    public static <T> SearchResult<T> from(SearchResponse<T> response) {
        return fromHits(response.hits());
    }

    public static <T> SearchResult<T> fromTemplate(SearchTemplateResponse<T> response) {
        return fromHits(response.hits());
    }

    private static <T> SearchResult<T> fromHits(HitsMetadata<T> hits) {
        List<T> data = hits.hits().stream()
                .map(Hit::source)
                .collect(Collectors.toList());

        List<String> searchAfter = null;
        if (!hits.hits().isEmpty()) {
            var lastHit = hits.hits().get(hits.hits().size() - 1);
            if (lastHit.sort() != null && !lastHit.sort().isEmpty()) {
                searchAfter = lastHit.sort().stream()
                        .map(fv -> fv._kind().name().equals("StringValue") ? fv.stringValue() : String.valueOf(fv))
                        .collect(Collectors.toList());
            }
        }

        return SearchResult.<T>builder()
                .data(data)
                .totalCount(hits.total() != null ? hits.total().value() : 0)
                .searchAfter(searchAfter)
                .build();
    }
}
