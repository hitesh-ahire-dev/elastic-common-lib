package com.yourorg.elasticcommon.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldDefinition {
    private String name;
    private FieldType type;
    private String analyzer;
    private String format;
    private boolean searchable;
    private boolean sortable;
}
