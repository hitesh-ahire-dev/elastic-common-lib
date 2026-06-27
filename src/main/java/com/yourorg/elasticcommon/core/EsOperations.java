package com.yourorg.elasticcommon.core;

import com.yourorg.elasticcommon.model.IndexTemplate;
import com.yourorg.elasticcommon.model.PaginationRequest;
import com.yourorg.elasticcommon.model.SearchCriteria;
import com.yourorg.elasticcommon.model.SearchRequest;
import com.yourorg.elasticcommon.model.SearchResult;

import java.util.Map;
import java.util.Optional;

public interface EsOperations {
    <T> void index(String indexAlias, String docId, T document);
    void updateWithScript(String indexAlias, String docId, String scriptId,
                           Map<String, Object> params, Object upsertDoc);
    <T> Optional<T> getById(String indexAlias, String docId, Class<T> type);
    void delete(String indexAlias, String docId);

    <T> SearchResult<T> search(String templateKey, SearchRequest request, Class<T> type);
    <T> SearchResult<T> paginationSearch(String templateKey, PaginationRequest request, Class<T> type);
    long count(String templateKey, SearchRequest request);

    /**
     * Builds the query dynamically from the field mappings in {@code template}.
     * Each entry in {@code criteria.fieldCriteria} is resolved to the correct
     * ES query clause (term/match/terms/range) based on the field's {@link com.yourorg.elasticcommon.model.FieldType}.
     */
    <T> SearchResult<T> dynamicSearch(String indexAlias, IndexTemplate template,
                                      SearchCriteria criteria, int from, int size, Class<T> type);
}
