package com.yourorg.elasticcommon.model;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface IndexTemplate {

    List<FieldDefinition> getFields();

    Optional<FieldDefinition> getField(String name);

    default List<String> getFulltextFields() {
        return getFields().stream()
                .filter(f -> f.getType() == FieldType.TEXT && f.isSearchable())
                .map(FieldDefinition::getName)
                .collect(Collectors.toList());
    }
}
