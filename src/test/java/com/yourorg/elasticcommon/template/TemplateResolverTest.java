package com.yourorg.elasticcommon.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateResolverTest {

    private TemplateResolver templateResolver;

    @BeforeEach
    void setUp() {
        templateResolver = new TemplateResolver();
    }

    @Test
    void resolveTemplate_CacheHit_ShouldReturnCachedTemplate() {
        String templateKey = "customer/search-by-status";
        
        String firstCall = templateResolver.resolveTemplate(templateKey);
        String secondCall = templateResolver.resolveTemplate(templateKey);
        
        assertNotNull(firstCall);
        assertEquals(firstCall, secondCall);
    }

    @Test
    void resolveTemplate_MissingTemplate_ShouldThrowException() {
        String templateKey = "nonexistent/template";
        
        EsTemplateException exception = assertThrows(EsTemplateException.class, () -> {
            templateResolver.resolveTemplate(templateKey);
        });
        
        assertTrue(exception.getMessage().contains("Template not found"));
    }

    @Test
    void clearCache_ShouldRemoveAllCachedTemplates() {
        String templateKey = "customer/search-by-status";
        
        templateResolver.resolveTemplate(templateKey);
        templateResolver.clearCache();
        
        // Should reload from classpath after cache clear
        String afterClear = templateResolver.resolveTemplate(templateKey);
        assertNotNull(afterClear);
    }
}
