package com.yourorg.elasticcommon.index;

public class IndexNameStrategy {

    public static final String INDEX_SEPARATOR  = "_";
    public static final String INDEX_VERSION    = "v1";

    public String buildIndexName(String prefix, String entityId) {
        return prefix + INDEX_SEPARATOR + entityId + INDEX_SEPARATOR + INDEX_VERSION;
    }
}
