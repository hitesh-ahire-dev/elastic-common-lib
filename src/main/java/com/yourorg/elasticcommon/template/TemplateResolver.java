package com.yourorg.elasticcommon.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateResolver {

    private static final String TEMPLATE_BASE_PATH = "es-templates/";
    private static final String TEMPLATE_EXTENSION  = ".json";

    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public String resolveTemplate(String templateKey) {
        return templateCache.computeIfAbsent(templateKey, this::loadTemplate);
    }

    private String loadTemplate(String templateKey) {
        String resourcePath = TEMPLATE_BASE_PATH + templateKey + TEMPLATE_EXTENSION;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new EsTemplateException("Template not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EsTemplateException("Failed to load template: " + resourcePath, e);
        }
    }

    public void clearCache() {
        templateCache.clear();
    }
}
