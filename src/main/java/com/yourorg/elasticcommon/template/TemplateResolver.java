package com.yourorg.elasticcommon.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateResolver {

    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public String resolveTemplate(String templateKey) {
        return templateCache.computeIfAbsent(templateKey, this::loadTemplate);
    }

    private String loadTemplate(String templateKey) {
        String resourcePath = "es-templates/" + templateKey + ".json";
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
