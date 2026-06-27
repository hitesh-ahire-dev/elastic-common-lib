package com.yourorg.elasticcommon.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yourorg.elasticcommon.config.EsClientConfiguration;
import com.yourorg.elasticcommon.config.EsProperties;
import com.yourorg.elasticcommon.model.SearchRequest;
import com.yourorg.elasticcommon.retry.RetryExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires Docker to be running")
class EsOperationsIntegrationTest {

    private ElasticsearchContainer elasticsearchContainer;
    private EsOperations esOperations;
    private ElasticsearchClient elasticsearchClient;

    @BeforeEach
    void setUp() throws Exception {
        DockerImageName imageName = DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.3");
        elasticsearchContainer = new ElasticsearchContainer(imageName)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false");
        elasticsearchContainer.start();

        EsProperties esProperties = new EsProperties();
        esProperties.setHosts(java.util.List.of(elasticsearchContainer.getHttpHostAddress()));
        esProperties.setSslEnabled(false);
        esProperties.setRetry(new EsProperties.RetryProperties());

        EsClientConfiguration config = new EsClientConfiguration();
        elasticsearchClient = config.elasticsearchClient(esProperties);

        RetryExecutor retryExecutor = new RetryExecutor(esProperties);

        esOperations = new EsOperationsImpl(elasticsearchClient, retryExecutor,
                new com.yourorg.elasticcommon.query.DynamicQueryBuilder(),
                new com.yourorg.elasticcommon.index.EsIndexManager(elasticsearchClient));
    }

    @AfterEach
    void tearDown() {
        if (elasticsearchContainer != null) {
            elasticsearchContainer.stop();
        }
    }

    @Test
    void indexAndGetById_ShouldWork() {
        String indexName = "test-index";
        String docId = "test-doc-1";
        TestDocument document = new TestDocument("John Doe", "john@example.com");

        esOperations.index(indexName, docId, document);

        var retrieved = esOperations.getById(indexName, docId, TestDocument.class);
        
        assertTrue(retrieved.isPresent());
        assertEquals("John Doe", retrieved.get().name);
        assertEquals("john@example.com", retrieved.get().email);
    }

    @Test
    void paginationSearch_ShouldReturnResults() {
        String indexName = "test-index";
        
        // Index multiple documents
        for (int i = 0; i < 5; i++) {
            TestDocument doc = new TestDocument("User " + i, "user" + i + "@example.com");
            esOperations.index(indexName, "doc-" + i, doc);
        }

        // Wait for indexing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String templateJson = """
            {
              "match_all": {}
            }
            """;

        SearchRequest request = SearchRequest.builder()
                .indexAlias(indexName)
                .params(Map.of())
                .build();

        var result = esOperations.search("customer/search-by-status", request, TestDocument.class);

        assertNotNull(result);
    }

    @Test
    void delete_ShouldRemoveDocument() {
        String indexName = "test-index";
        String docId = "test-doc-delete";
        TestDocument document = new TestDocument("Delete Me", "delete@example.com");

        esOperations.index(indexName, docId, document);

        var beforeDelete = esOperations.getById(indexName, docId, TestDocument.class);
        assertTrue(beforeDelete.isPresent());

        esOperations.delete(indexName, docId);

        var afterDelete = esOperations.getById(indexName, docId, TestDocument.class);
        assertFalse(afterDelete.isPresent());
    }

    record TestDocument(String name, String email) {}
}
