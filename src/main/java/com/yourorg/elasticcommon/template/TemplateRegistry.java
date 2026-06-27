package com.yourorg.elasticcommon.template;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TemplateRegistry {

    private final ConcurrentHashMap<String, String> registeredTemplates = new ConcurrentHashMap<>();

    public void registerTemplate(String templateKey, String templateJson) {
        registeredTemplates.put(templateKey, templateJson);
    }

    public String getTemplate(String templateKey) {
        return registeredTemplates.get(templateKey);
    }

    public Set<String> getAllTemplateKeys() {
        return registeredTemplates.keySet();
    }

    public boolean containsTemplate(String templateKey) {
        return registeredTemplates.containsKey(templateKey);
    }
}
