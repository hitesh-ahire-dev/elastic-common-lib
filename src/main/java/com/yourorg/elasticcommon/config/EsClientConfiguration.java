package com.yourorg.elasticcommon.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import com.yourorg.elasticcommon.core.EsOperations;
import com.yourorg.elasticcommon.core.EsOperationsImpl;
import com.yourorg.elasticcommon.index.EsIndexManager;
import com.yourorg.elasticcommon.index.IndexNameStrategy;
import com.yourorg.elasticcommon.query.DynamicQueryBuilder;
import com.yourorg.elasticcommon.query.QueryBuilder;
import com.yourorg.elasticcommon.retry.RetryExecutor;
import com.yourorg.elasticcommon.template.EsTemplateInitializer;
import com.yourorg.elasticcommon.template.TemplateResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

@Configuration
@EnableConfigurationProperties(EsProperties.class)
public class EsClientConfiguration {

    @Bean
    public ElasticsearchClient elasticsearchClient(EsProperties esProperties) throws Exception {
        List<HttpHost> hosts = esProperties.getHosts().stream()
                .map(this::parseHost)
                .toList();

        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));

        if (esProperties.getUsername() != null && !esProperties.getUsername().isBlank()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(esProperties.getUsername(), esProperties.getPassword())
            );
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                httpClientBuilder.setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom()
                        .setConnectTimeout(esProperties.getConnectTimeoutMs())
                        .setSocketTimeout(esProperties.getSocketTimeoutMs())
                        .build());
                return httpClientBuilder;
            });
        } else {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom()
                        .setConnectTimeout(esProperties.getConnectTimeoutMs())
                        .setSocketTimeout(esProperties.getSocketTimeoutMs())
                        .build());
                return httpClientBuilder;
            });
        }

        if (esProperties.isSslEnabled()) {
            SSLContext sslContext = buildSSLContext(esProperties);
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setSSLContext(sslContext);
                return httpClientBuilder;
            });
        }

        RestClient restClient = builder.build();

        ObjectMapper esMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(esMapper)
        );

        return new ElasticsearchClient(transport);
    }

    @Bean
    public RetryExecutor retryExecutor(EsProperties esProperties) {
        return new RetryExecutor(esProperties);
    }

    @Bean
    public DynamicQueryBuilder dynamicQueryBuilder() {
        return new DynamicQueryBuilder();
    }

    @Bean
    public QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    @Bean
    public TemplateResolver templateResolver() {
        return new TemplateResolver();
    }

    @Bean
    public IndexNameStrategy indexNameStrategy() {
        return new IndexNameStrategy();
    }

    @Bean
    public EsIndexManager esIndexManager(ElasticsearchClient elasticsearchClient) {
        return new EsIndexManager(elasticsearchClient);
    }

    @Bean
    public EsOperations esOperations(ElasticsearchClient elasticsearchClient,
                                     RetryExecutor retryExecutor,
                                     DynamicQueryBuilder dynamicQueryBuilder,
                                     EsIndexManager esIndexManager) {
        return new EsOperationsImpl(elasticsearchClient, retryExecutor, dynamicQueryBuilder, esIndexManager);
    }

    @Bean
    public EsTemplateInitializer esTemplateInitializer(ElasticsearchClient elasticsearchClient) {
        return new EsTemplateInitializer(elasticsearchClient);
    }

    private HttpHost parseHost(String host) {
        String[] parts = host.replace("http://", "").replace("https://", "").split(":");
        String hostname = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
        boolean https = host.startsWith("https://");
        return new HttpHost(hostname, port, https ? "https" : "http");
    }

    private SSLContext buildSSLContext(EsProperties esProperties) throws Exception {
        if (esProperties.getTruststorePath() != null && !esProperties.getTruststorePath().isBlank()) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(esProperties.getTruststorePath())) {
                trustStore.load(fis, esProperties.getTruststorePassword().toCharArray());
            }
            return SSLContextBuilder.create()
                    .loadTrustMaterial(trustStore, null)
                    .build();
        } else {
            return SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
        }
    }
}
