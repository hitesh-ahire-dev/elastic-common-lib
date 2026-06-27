package com.yourorg.elasticcommon.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
public class SearchRequest {
    private String indexAlias;
    private Map<String, Object> params;
}
