package com.yourorg.elasticcommon.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.json.JsonData;
import com.yourorg.elasticcommon.exception.EsOperationException;
import com.yourorg.elasticcommon.model.IndexTemplate;
import com.yourorg.elasticcommon.model.PaginationRequest;
import com.yourorg.elasticcommon.model.SearchCriteria;
import com.yourorg.elasticcommon.model.SearchRequest;
import com.yourorg.elasticcommon.model.SearchResult;
import com.yourorg.elasticcommon.index.EsIndexManager;
import com.yourorg.elasticcommon.model.MappingFileIndexTemplate;
import com.yourorg.elasticcommon.query.DynamicQueryBuilder;
import com.yourorg.elasticcommon.retry.RetryExecutor;
import com.yourorg.elasticcommon.template.EsTemplateInitializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EsOperationsImpl implements EsOperations {

    private final ElasticsearchClient elasticsearchClient;
    private final RetryExecutor retryExecutor;
    private final DynamicQueryBuilder dynamicQueryBuilder;
    private final EsIndexManager esIndexManager;

    public EsOperationsImpl(ElasticsearchClient elasticsearchClient,
                            RetryExecutor retryExecutor,
                            DynamicQueryBuilder dynamicQueryBuilder,
                            EsIndexManager esIndexManager) {
        this.elasticsearchClient = elasticsearchClient;
        this.retryExecutor = retryExecutor;
        this.dynamicQueryBuilder = dynamicQueryBuilder;
        this.esIndexManager = esIndexManager;
    }

    @Override
    public <T> void index(String indexAlias, String docId, T document) {
        esIndexManager.ensureIndex(indexAlias);
        retryExecutor.execute(() -> {
            try {
                IndexRequest<T> request = IndexRequest.of(i -> i
                        .index(indexAlias)
                        .id(docId)
                        .document(document));
                elasticsearchClient.index(request);
            } catch (IOException e) {
                throw new EsOperationException("Failed to index document in index: " + indexAlias + ", docId: " + docId, e);
            }
        });
    }

    @Override
    public void updateWithScript(String indexAlias, String docId, String scriptId,
                                  Map<String, Object> params, Object upsertDoc) {
        esIndexManager.ensureIndex(indexAlias);
        retryExecutor.execute(() -> {
            try {
                Map<String, co.elastic.clients.json.JsonData> jsonParams = params.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> co.elastic.clients.json.JsonData.of(e.getValue())
                        ));

                UpdateRequest<Object, Object> request = UpdateRequest.of(u -> u
                        .index(indexAlias)
                        .id(docId)
                        .script(s -> s
                                .inline(i -> i
                                        .source(scriptId)
                                        .params(jsonParams)))
                        .upsert(upsertDoc));
                elasticsearchClient.update(request, Object.class);
            } catch (IOException e) {
                throw new EsOperationException("Failed to update document in index: " + indexAlias + ", docId: " + docId, e);
            }
        });
    }

    @Override
    public <T> Optional<T> getById(String indexAlias, String docId, Class<T> type) {
        esIndexManager.ensureIndex(indexAlias);
        return retryExecutor.execute(() -> {
            try {
                GetRequest request = GetRequest.of(g -> g
                        .index(indexAlias)
                        .id(docId));
                GetResponse<T> response = elasticsearchClient.get(request, type);
                if (response.found()) {
                    return Optional.of(response.source());
                }
                return Optional.empty();
            } catch (IOException e) {
                throw new EsOperationException("Failed to get document from index: " + indexAlias + ", docId: " + docId, e);
            }
        });
    }

    @Override
    public void delete(String indexAlias, String docId) {
        esIndexManager.ensureIndex(indexAlias);
        retryExecutor.execute(() -> {
            try {
                DeleteRequest request = DeleteRequest.of(d -> d
                        .index(indexAlias)
                        .id(docId));
                elasticsearchClient.delete(request);
            } catch (IOException e) {
                throw new EsOperationException("Failed to delete document from index: " + indexAlias + ", docId: " + docId, e);
            }
        });
    }

    @Override
    public <T> SearchResult<T> search(String templateKey, SearchRequest request, Class<T> type) {
        esIndexManager.ensureIndex(request.getIndexAlias());
        return retryExecutor.execute(() -> {
            try {
                String templateId = EsTemplateInitializer.toEsId(templateKey);
                Map<String, JsonData> params = toJsonParams(request.getParams());
                params.put("from", JsonData.of(0));
                params.put("size", JsonData.of(10000));

                SearchTemplateResponse<T> response = elasticsearchClient.searchTemplate(r -> r
                        .index(request.getIndexAlias())
                        .id(templateId)
                        .params(params),
                        type);

                return SearchResult.fromTemplate(response);
            } catch (Exception e) {
                throw new EsOperationException(
                        "Failed to search with stored template: " + templateKey + ", index: " + request.getIndexAlias(), e);
            }
        });
    }

    @Override
    public <T> SearchResult<T> paginationSearch(String templateKey, PaginationRequest request, Class<T> type) {
        esIndexManager.ensureIndex(request.getIndexAlias());
        return retryExecutor.execute(() -> {
            try {
                String templateId = EsTemplateInitializer.toEsId(templateKey);
                Map<String, JsonData> params = toJsonParams(request.getParams());
                params.put("from", JsonData.of(request.getFrom()));
                params.put("size", JsonData.of(request.getSize()));

                SearchTemplateResponse<T> response = elasticsearchClient.searchTemplate(r -> r
                        .index(request.getIndexAlias())
                        .id(templateId)
                        .params(params),
                        type);

                return SearchResult.fromTemplate(response);
            } catch (Exception e) {
                throw new EsOperationException(
                        "Failed to pagination search with stored template: " + templateKey + ", index: " + request.getIndexAlias(), e);
            }
        });
    }

    @Override
    public long count(String templateKey, SearchRequest request) {
        esIndexManager.ensureIndex(request.getIndexAlias());
        return retryExecutor.execute(() -> {
            try {
                String templateId = EsTemplateInitializer.toEsId(templateKey);
                Map<String, JsonData> params = toJsonParams(request.getParams());
                params.put("from", JsonData.of(0));
                params.put("size", JsonData.of(0));

                SearchTemplateResponse<Object> response = elasticsearchClient.searchTemplate(r -> r
                        .index(request.getIndexAlias())
                        .id(templateId)
                        .params(params),
                        Object.class);

                return response.hits().total() != null ? response.hits().total().value() : 0L;
            } catch (Exception e) {
                throw new EsOperationException(
                        "Failed to count with stored template: " + templateKey + ", index: " + request.getIndexAlias(), e);
            }
        });
    }

    @Override
    public <T> SearchResult<T> dynamicSearch(String indexAlias, IndexTemplate template,
                                              SearchCriteria criteria, int from, int size, Class<T> type) {
        if (template instanceof MappingFileIndexTemplate mft) {
            esIndexManager.ensureIndexWithMapping(indexAlias, mft);
        } else {
            esIndexManager.ensureIndex(indexAlias);
        }
        return retryExecutor.execute(() -> {
            try {
                Query query = dynamicQueryBuilder.build(template, criteria);

                co.elastic.clients.elasticsearch.core.SearchRequest searchRequest =
                        co.elastic.clients.elasticsearch.core.SearchRequest.of(s -> s
                                .index(indexAlias)
                                .from(from)
                                .size(size)
                                .query(query));

                SearchResponse<T> response = elasticsearchClient.search(searchRequest, type);
                return SearchResult.from(response);
            } catch (Exception e) {
                throw new EsOperationException("Failed dynamic search on index: " + indexAlias, e);
            }
        });
    }

    private Map<String, JsonData> toJsonParams(Map<String, Object> params) {
        Map<String, JsonData> result = new HashMap<>();
        if (params != null) {
            params.forEach((k, v) -> result.put(k, JsonData.of(v)));
        }
        return result;
    }
}
