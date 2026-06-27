package com.yourorg.elasticcommon.template;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;

public class EsTemplateInitializer {

    private static final Log log = LogFactory.getLog(EsTemplateInitializer.class);
    private static final String TEMPLATE_PREFIX = "es-templates/";

    private final ElasticsearchClient elasticsearchClient;

    public EsTemplateInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:es-templates/**/*.json");

            for (Resource resource : resources) {
                String url = resource.getURL().toString();
                int idx = url.indexOf(TEMPLATE_PREFIX);
                if (idx < 0) continue;

                String templateKey = url.substring(idx + TEMPLATE_PREFIX.length(), url.length() - ".json".length());
                String templateId = toEsId(templateKey);
                String templateBody = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                elasticsearchClient.putScript(r -> r
                        .id(templateId)
                        .script(s -> s
                                .lang("mustache")
                                .source(templateBody)
                        )
                );
                log.info("Registered ES stored template: '" + templateKey + "' as id: '" + templateId + "'");
            }
        } catch (Exception e) {
            log.warn("Failed to register ES search templates: " + e.getMessage() + ". Ensure Elasticsearch is running.");
        }
    }

    public static String toEsId(String templateKey) {
        return templateKey.replace("/", "-");
    }
}
