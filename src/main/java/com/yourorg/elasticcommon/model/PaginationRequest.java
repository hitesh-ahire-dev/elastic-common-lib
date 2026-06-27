package com.yourorg.elasticcommon.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaginationRequest extends SearchRequest {
    private int from;
    private int size;
    private List<SortField> sort;
    private List<String> searchAfter;

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SortField {
        private String field;
        private String direction; // "asc" or "desc"
    }
}
