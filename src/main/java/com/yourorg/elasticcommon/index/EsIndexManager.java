package com.yourorg.elasticcommon.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourorg.elasticcommon.exception.EsOperationException;
import com.yourorg.elasticcommon.model.MappingFileIndexTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures an Elasticsearch index exists before any operation is performed.
 * <p>
 * Uses a local cache so the existence check (HEAD request) is only made once
 * per index alias per JVM lifetime. If the index is absent it is created:
 * <ul>
 *   <li>{@link #ensureIndex(String)} — plain creation with default settings.</li>
 *   <li>{@link #ensureIndexWithMapping(String, MappingFileIndexTemplate)} — creation
 *       with the {@code settings} and {@code mappings} blocks from the template's
 *       JSON mapping file.</li>
 * </ul>
 */
public class EsIndexManager {

    private static final Logger log = LoggerFactory.getLogger(EsIndexManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ElasticsearchClient client;
    private final Set<String> knownIndices = ConcurrentHashMap.newKeySet();

    public EsIndexManager(ElasticsearchClient client) {
        this.client = client;
    }

    public void ensureIndex(String indexAlias) {
        if (knownIndices.contains(indexAlias)) return;
        try {
            boolean exists = client.indices().exists(r -> r.index(indexAlias)).value();
            if (!exists) {
                client.indices().create(r -> r.index(indexAlias));
                log.info("Created index: {}", indexAlias);
            }
            knownIndices.add(indexAlias);
        } catch (Exception e) {
            log.warn("Could not ensure index '{}': {}", indexAlias, e.getMessage());
        }
    }

    public void ensureIndexWithMapping(String indexAlias, MappingFileIndexTemplate template) {
        if (knownIndices.contains(indexAlias)) return;
        try {
            boolean exists = client.indices().exists(r -> r.index(indexAlias)).value();
            if (!exists) {
                String createBody = buildCreateBody(template);
                try (StringReader reader = new StringReader(createBody)) {
                    CreateIndexRequest request = CreateIndexRequest.of(r -> r
                            .index(indexAlias)
                            .withJson(reader));
                    client.indices().create(request);
                }
                log.info("Created index '{}' using template '{}'", indexAlias, template.getTemplateName());
            }
            knownIndices.add(indexAlias);
        } catch (Exception e) {
            log.warn("Could not ensure index '{}' with mapping: {}", indexAlias, e.getMessage());
        }
    }

    private String buildCreateBody(MappingFileIndexTemplate template) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            String settingsJson = template.getRawSettingsJson();
            String mappingsJson = template.getRawMappingsJson();
            if (settingsJson != null) {
                body.set("settings", MAPPER.readTree(settingsJson));
            }
            if (mappingsJson != null) {
                body.set("mappings", MAPPER.readTree(mappingsJson));
            }
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new EsOperationException("Failed to build create-index body for template: " + template.getTemplateName(), e);
        }
    }
}
