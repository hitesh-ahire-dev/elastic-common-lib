# elastic-common-lib

> A production-ready, Spring Boot auto-configured library that wraps the [Elasticsearch Java Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html).

Drop it into **any** Spring Boot 3 application and get a fully wired `EsOperations` bean with CRUD, template-based search, dynamic query building, auto index creation, and resilient exponential-backoff retry — all driven by `application.yml` properties. Zero boilerplate, zero `@EnableXxx` annotations required.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Features](#features)
- [Architecture Diagram](#architecture-diagram)
- [Module Structure](#module-structure)
- [Technologies Used](#technologies-used)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Environment Variables](#environment-variables)
- [Core API — `EsOperations`](#core-api--esoperations)
- [Library Integration Guide](#library-integration-guide)
- [Stored Search Templates](#stored-search-templates)
- [Dynamic Query Builder](#dynamic-query-builder)
- [Index Mapping Templates](#index-mapping-templates)
- [Auto Index Management](#auto-index-management)
- [Retry Mechanism](#retry-mechanism)
- [SSL / TLS Support](#ssl--tls-support)
- [Configuration Examples](#configuration-examples)
- [Logging Configuration](#logging-configuration)
- [Exception Handling](#exception-handling)
- [Running Tests](#running-tests)
- [Security](#security)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Limitations](#limitations)
- [Future Enhancements](#future-enhancements)

---

## Project Overview

`elastic-common-lib` is a **domain-agnostic** Spring Boot 3 starter library providing a clean, high-level abstraction over the Elasticsearch Java Client 8.x. It is designed to be dropped into any Spring Boot application with no configuration classes, no `@EnableXxx` annotations, and no coupling to any business domain.

The library auto-configures all Elasticsearch infrastructure (client, retry, index management, template registration) from properties. Consuming applications interact exclusively through the `EsOperations` interface.

---

## Features

- **Zero-boilerplate auto-configuration** — all beans wired automatically via Spring Boot's `AutoConfiguration.imports`.
- **Unified CRUD API** — index, get, update (Painless script upsert), delete by alias + document ID.
- **Stored-template search** — Mustache scripts auto-discovered from `classpath*:es-templates/**/*.json` and registered with ES on startup.
- **Dynamic query builder** — builds type-aware `bool/must` queries from `IndexTemplate` field metadata; no query JSON needed in application code.
- **Paginated & deep-pagination search** — `from`/`size` pagination and `search_after` cursor support via `SearchResult`.
- **Auto index creation** — lazily creates missing indices (plain or with explicit mappings) before every operation; cached per JVM.
- **Resilience4j exponential-backoff retry** — configurable max attempts and initial backoff.
- **SSL/TLS** — truststore-backed or trust-all context; credentials and SSL combined in a single HTTP client callback (no silent overwrite bug).
- **Security-safe configuration** — `password` and `truststorePassword` excluded from `toString()` to prevent secret leakage in logs.

---

## Architecture Diagram

```
┌────────────────────────────────────────────────────────────┐
│                  Consuming Application                     │
│                                                            │
│  ┌──────────────┐    ┌─────────────────────────────────┐   │
│  │ YourService  │───▶│        EsOperations (interface) │   │
│  └──────────────┘    └──────────────┬──────────────────┘   │
│                                     │ implemented by        │
└─────────────────────────────────────┼────────────────────── ┘
                                      │
┌─────────────────────────────────────▼────────────────────── ┐
│                    elastic-common-lib                        │
│                                                              │
│  EsOperationsImpl                                            │
│    │                                                         │
│    ├── RetryExecutor (Resilience4j exponential backoff)      │
│    ├── EsIndexManager (lazy index create + local cache)      │
│    ├── DynamicQueryBuilder (IndexTemplate → ES Query)        │
│    └── ElasticsearchClient (ES Java Client 8.x)              │
│                                                              │
│  EsTemplateInitializer                                       │
│    └── scans classpath*:es-templates/**/*.json               │
│        └── registers as ES stored Mustache scripts           │
│                                                              │
│  EsClientConfiguration (@Configuration)                     │
│    └── reads EsProperties (@ConfigurationProperties "es")    │
│        ├── hosts, username, password                         │
│        ├── ssl-enabled, truststore-path/password             │
│        ├── connect-timeout-ms, socket-timeout-ms             │
│        └── retry.max-attempts, retry.backoff-ms              │
└──────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────▼──────────────┐
                │      Elasticsearch 8.x      │
                └─────────────────────────────┘
```

---

## Module Structure

```
elastic-common-lib/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/yourorg/elasticcommon/
    │   │   ├── config/
    │   │   │   ├── EsClientConfiguration.java   # All bean wiring
    │   │   │   └── EsProperties.java            # @ConfigurationProperties (prefix="es")
    │   │   ├── core/
    │   │   │   ├── EsOperations.java            # Public API interface
    │   │   │   └── EsOperationsImpl.java        # Implementation
    │   │   ├── exception/
    │   │   │   ├── EsOperationException.java    # Runtime exception for ES failures
    │   │   │   └── EsTemplateException.java     # Runtime exception for template failures
    │   │   ├── index/
    │   │   │   ├── EsIndexManager.java          # Lazy index creation with local cache
    │   │   │   └── IndexNameStrategy.java       # Builds index names (prefix_entity_vN)
    │   │   ├── model/
    │   │   │   ├── FieldDefinition.java         # Field metadata (name, type, analyzer…)
    │   │   │   ├── FieldType.java               # Enum: KEYWORD, TEXT, DATE, INTEGER…
    │   │   │   ├── IndexTemplate.java           # Interface for index field metadata
    │   │   │   ├── MappingFileIndexTemplate.java # JSON-file-backed IndexTemplate
    │   │   │   ├── PaginationRequest.java       # Search request with from/size/sort
    │   │   │   ├── SearchCriteria.java          # Dynamic query parameters
    │   │   │   ├── SearchRequest.java           # Base search request
    │   │   │   └── SearchResult.java            # Generic result with search_after
    │   │   ├── query/
    │   │   │   ├── DynamicQueryBuilder.java     # IndexTemplate + SearchCriteria → Query
    │   │   │   └── QueryBuilder.java            # Inline template resolver (extension point)
    │   │   ├── retry/
    │   │   │   └── RetryExecutor.java           # Resilience4j retry wrapper
    │   │   └── template/
    │   │       ├── EsTemplateException.java
    │   │       ├── EsTemplateInitializer.java   # Registers stored Mustache scripts on startup
    │   │       ├── TemplateRegistry.java        # In-memory template key store
    │   │       └── TemplateResolver.java        # Classpath template loader with cache
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/yourorg/elasticcommon/
            └── (unit tests for query, template, retry logic)
```

> **Note:** The `es-templates/` directory does **not** exist in this library. Search templates belong in each consuming application's `src/main/resources/es-templates/` directory. The library auto-discovers and registers them at startup.

---

## Technologies Used

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Language |
| Spring Boot | 3.2.5 | Auto-configuration, DI |
| Elasticsearch Java Client | 8.13.3 | ES communication |
| Resilience4j | 2.1.0 | Retry with exponential backoff |
| Jackson | (managed by Spring Boot) | JSON serialisation/deserialisation |
| Apache Commons Text | 1.x | Inline Mustache template parameter substitution (`QueryBuilder`) |
| Lombok | (managed by Spring Boot) | Boilerplate reduction |
| Testcontainers | 1.19.8 | Integration test ES container |

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x |
| Elasticsearch | 8.x |
| Maven | 3.8+ |
| Docker | 20+ (integration tests only) |

---

## Installation

**Step 1 — Build and install to local Maven repository:**

```bash
# From the project root
mvn clean install -pl elastic-common-lib
```

**Step 2 — Add the dependency to your application's `pom.xml`:**

```xml
<dependency>
    <groupId>com.yourorg</groupId>
    <artifactId>elastic-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

No `@EnableXxx` annotation is needed. Spring Boot's auto-configuration picks up `EsClientConfiguration` automatically.

---

## Configuration

All properties are prefixed with `es`. Add the following to your application's `application.yml`:

```yaml
es:
  hosts:
    - http://localhost:9200          # One or more ES nodes
  username: elastic                  # Optional — omit for unauthenticated clusters
  password: changeme                 # Use env variable in production (see below)
  ssl-enabled: false                 # true to enable TLS
  truststore-path:                   # JKS truststore path (ssl-enabled: true only)
  truststore-password:               # JKS truststore password
  connect-timeout-ms: 5000           # Default: 5 000 ms
  socket-timeout-ms: 30000           # Default: 30 000 ms
  retry:
    max-attempts: 3                  # Default: 3
    backoff-ms: 50                   # Initial backoff; doubles each attempt
```

Multi-node cluster:

```yaml
es:
  hosts:
    - http://es-node-1:9200
    - http://es-node-2:9200
    - http://es-node-3:9200
```

---

## Environment Variables

All sensitive and environment-specific values should be passed as environment variables rather than hard-coded in `application.yml`:

| Environment Variable | Maps to | Default |
|---|---|---|
| `ES_HOST` | `es.hosts[0]` | `http://localhost:9200` |
| `ES_USERNAME` | `es.username` | *(empty)* |
| `ES_PASSWORD` | `es.password` | *(empty)* |
| `ES_SSL_ENABLED` | `es.ssl-enabled` | `false` |
| `ES_TRUSTSTORE_PATH` | `es.truststore-path` | *(empty)* |
| `ES_TRUSTSTORE_PASSWORD` | `es.truststore-password` | *(empty)* |
| `ES_CONNECT_TIMEOUT_MS` | `es.connect-timeout-ms` | `5000` |
| `ES_SOCKET_TIMEOUT_MS` | `es.socket-timeout-ms` | `30000` |
| `ES_RETRY_MAX_ATTEMPTS` | `es.retry.max-attempts` | `3` |
| `ES_RETRY_BACKOFF_MS` | `es.retry.backoff-ms` | `50` |

Reference in `application.yml`:

```yaml
es:
  hosts:
    - ${ES_HOST:http://localhost:9200}
  password: ${ES_PASSWORD:}
```

---

## Core API — `EsOperations`

Inject `EsOperations` into your service. It is the sole public contract; you never depend on `EsOperationsImpl` directly.

```java
@Service
public class OrderService {

    private final EsOperations esOperations;

    public OrderService(EsOperations esOperations) {
        this.esOperations = esOperations;
    }

    public void save(Order order) {
        esOperations.index("orders", order.getId(), order);
    }

    public Optional<Order> findById(String id) {
        return esOperations.getById("orders", id, Order.class);
    }

    public void remove(String id) {
        esOperations.delete("orders", id);
    }

    public void updateStatus(String id, String status, Order fallback) {
        esOperations.updateWithScript(
            "orders", id,
            "ctx._source.status = params.status",
            Map.of("status", status),
            fallback
        );
    }
}
```

### Method Reference

| Method | Description |
|---|---|
| `index(alias, docId, doc)` | Index or overwrite a document |
| `getById(alias, docId, Class<T>)` | Fetch a single document by ID → `Optional<T>` |
| `delete(alias, docId)` | Delete a document |
| `updateWithScript(alias, docId, script, params, upsertDoc)` | Painless script update with upsert fallback |
| `search(templateKey, SearchRequest, Class<T>)` | Stored-template search (unbounded, max 10 000) |
| `paginationSearch(templateKey, PaginationRequest, Class<T>)` | Stored-template search with from/size/sort |
| `count(templateKey, SearchRequest)` | Count documents matching a stored template |
| `dynamicSearch(alias, IndexTemplate, SearchCriteria, from, size, Class<T>)` | Runtime query from field-type metadata |

---

## Library Integration Guide

Follow these five steps to integrate the library into any Spring Boot 3 application:

### 1 — Add the dependency

```xml
<dependency>
    <groupId>com.yourorg</groupId>
    <artifactId>elastic-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2 — Configure Elasticsearch properties

```yaml
# application.yml
es:
  hosts:
    - ${ES_HOST:http://localhost:9200}
  username: ${ES_USERNAME:}
  password: ${ES_PASSWORD:}
```

### 3 — Create a mapping file

Place a JSON mapping file in `src/main/resources/mappings/`:

```json
{
  "name": "order-template",
  "settings": { "number_of_shards": 1, "number_of_replicas": 1 },
  "mappings": {
    "properties": {
      "orderId":   { "type": "keyword" },
      "status":    { "type": "keyword" },
      "total":     { "type": "double" },
      "createdAt": { "type": "date", "format": "strict_date_optional_time" },
      "notes":     { "type": "text", "analyzer": "standard" }
    }
  }
}
```

### 4 — Create an IndexTemplate bean

```java
@Component
public class OrderIndexTemplate extends MappingFileIndexTemplate {
    public OrderIndexTemplate() {
        super("mappings/order-mapping.json");
    }
}
```

### 5 — Inject `EsOperations` and use it

```java
@Service
public class OrderService {

    private final EsOperations esOperations;
    private final IndexNameStrategy indexNameStrategy;
    private final OrderIndexTemplate orderIndexTemplate;

    // ... constructor injection ...

    public SearchResult<Order> findByStatus(String tenantId, String status) {
        SearchCriteria criteria = SearchCriteria.builder()
            .fieldCriteria(Map.of("tenantId", tenantId, "status", status))
            .build();
        return esOperations.dynamicSearch(
            indexNameStrategy.buildIndexName("orders", tenantId),
            orderIndexTemplate, criteria, 0, 50, Order.class);
    }
}
```

---

## Stored Search Templates

Place Mustache JSON files in `src/main/resources/es-templates/<domain>/` inside **your consuming application**.

`EsTemplateInitializer` scans `classpath*:es-templates/**/*.json` on `ApplicationReadyEvent` and registers each file as an Elasticsearch stored script.

### Template key → Stored Script ID

| File | Template Key | ES Script ID |
|---|---|---|
| `es-templates/order/search-by-status.json` | `order/search-by-status` | `order-search-by-status` |
| `es-templates/product/fulltext.json` | `product/fulltext` | `product-fulltext` |

### Example template

```json
{
  "from": {{from}},
  "size": {{size}},
  "query": {
    "bool": {
      "must": [
        { "term": { "tenantId": "{{tenantId}}" } },
        { "term": { "status":   "{{status}}"   } }
      ]
    }
  }
}
```

### Using a stored template

```java
// Unbounded (max 10 000 docs)
SearchRequest req = SearchRequest.builder()
    .indexAlias("orders_tenant-1_v1")
    .params(Map.of("tenantId", "T1", "status", "PENDING"))
    .build();
SearchResult<Order> result = esOperations.search("order/search-by-status", req, Order.class);

// Paginated
PaginationRequest page = PaginationRequest.builder()
    .indexAlias("orders_tenant-1_v1")
    .params(Map.of("tenantId", "T1", "status", "PENDING"))
    .from(0).size(20)
    .sort(List.of(new PaginationRequest.SortField("createdAt", "DESC")))
    .build();
SearchResult<Order> paged = esOperations.paginationSearch("order/search-by-status", page, Order.class);
```

---

## Dynamic Query Builder

`DynamicQueryBuilder` builds a `bool/must` query at runtime from `IndexTemplate` field-type metadata. No raw JSON is needed in application code.

### Query type resolution

| Field type | Criteria value type | ES query clause |
|---|---|---|
| `KEYWORD`, `BOOLEAN`, numeric | Single value | `term` |
| `KEYWORD`, `BOOLEAN`, numeric | `List<?>` | `terms` |
| `TEXT` | Any single value | `match` |
| `DATE`, numeric | `Map<String,Object>` with `gte`/`lte` | `range` |
| *(any)* | `fulltextQuery` set on `SearchCriteria` | `multi_match` across all `TEXT` fields |

### Example

```java
SearchCriteria criteria = SearchCriteria.builder()
    .fieldCriteria(Map.of(
        "status",    "ACTIVE",
        "tenantId",  "T1",
        "createdAt", Map.of("gte", "2024-01-01", "lte", "2024-12-31")
    ))
    .fulltextQuery("express delivery")
    .fulltextFields(List.of("notes", "description"))
    .build();

SearchResult<Order> result = esOperations.dynamicSearch(
    "orders_t1_v1", orderIndexTemplate, criteria, 0, 20, Order.class);
```

---

## Index Mapping Templates

Extend `MappingFileIndexTemplate` in your application and point it to a classpath JSON file:

```java
@Component
public class OrderIndexTemplate extends MappingFileIndexTemplate {
    public OrderIndexTemplate() {
        super("mappings/order-mapping.json");
    }
}
```

### Mapping file format

```json
{
  "name": "order-template",
  "index_patterns": ["orders-*"],
  "settings": {
    "number_of_shards": 2,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "lowercase_keyword": {
          "type": "custom",
          "tokenizer": "keyword",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "orderId":   { "type": "keyword" },
      "tenantId":  { "type": "keyword" },
      "status":    { "type": "keyword" },
      "total":     { "type": "double" },
      "createdAt": { "type": "date",    "format": "strict_date_optional_time" },
      "notes":     { "type": "text",    "analyzer": "standard", "searchable": true }
    }
  }
}
```

**Supported field types:** `keyword` · `text` · `date` · `integer` · `long` · `float` · `double` · `boolean` · `nested` · `object`

**Per-field attributes:**
- `analyzer` — custom analyser name
- `format` — date format string
- `searchable` — include in full-text searches (default `true`)
- `sortable` — allow sorting on this field (auto-inferred; always `false` for `text`, `nested`, `object`)

---

## Auto Index Management

`EsIndexManager` ensures an index exists before every `EsOperations` call. Results are cached in a `ConcurrentHashSet` so the HTTP HEAD check runs **at most once per alias per JVM lifetime**.

| Method | Behaviour |
|---|---|
| `ensureIndex(alias)` | Creates the index with ES default settings if absent |
| `ensureIndexWithMapping(alias, template)` | Creates the index with `settings` + `mappings` from the template JSON |

Failures are logged as `WARN` and swallowed — the operation is still attempted, allowing ES to report a more specific error.

---

## Retry Mechanism

Every `EsOperations` call is wrapped by `RetryExecutor` using **Resilience4j** exponential backoff:

```
Attempt 1  →  immediate
Attempt 2  →  backoff-ms × 2
Attempt 3  →  backoff-ms × 4
```

```yaml
es:
  retry:
    max-attempts: 3    # total attempts (not additional retries)
    backoff-ms: 50     # initial interval in milliseconds
```

After exhausting all attempts the original exception is re-thrown as `EsOperationException`.

---

## SSL / TLS Support

Enable TLS and optionally specify a JKS truststore:

```yaml
es:
  ssl-enabled: true
  truststore-path: /etc/ssl/es-truststore.jks
  truststore-password: ${ES_TRUSTSTORE_PASSWORD}
```

If `truststore-path` is not set and `ssl-enabled: true`, the library uses a **trust-all** SSL context — suitable for development only; never use in production.

When both credentials and SSL are configured, both are applied in a **single** `HttpClientConfigCallback` — preventing the silent overwrite that existed in earlier versions.

---

## Configuration Examples

### Local development (no auth, no SSL)

```yaml
es:
  hosts:
    - http://localhost:9200
```

### Secured cluster (basic auth + SSL, truststore)

```yaml
es:
  hosts:
    - https://es-prod-1:9200
    - https://es-prod-2:9200
  username: ${ES_USERNAME}
  password: ${ES_PASSWORD}
  ssl-enabled: true
  truststore-path: /etc/ssl/truststore.jks
  truststore-password: ${ES_TRUSTSTORE_PASSWORD}
  connect-timeout-ms: 3000
  socket-timeout-ms: 15000
  retry:
    max-attempts: 5
    backoff-ms: 100
```

### Elastic Cloud / Kibana

```yaml
es:
  hosts:
    - https://my-deployment.es.us-east-1.aws.elastic-cloud.com:443
  username: elastic
  password: ${ELASTIC_CLOUD_PASSWORD}
  ssl-enabled: true
```

---

## Logging Configuration

The library uses **SLF4J** and writes to whatever implementation the host application provides (Logback by default in Spring Boot).

Recommended levels per package:

```yaml
logging:
  level:
    com.yourorg.elasticcommon: INFO        # general operation logs
    com.yourorg.elasticcommon.index: DEBUG  # index creation details
    com.yourorg.elasticcommon.template: DEBUG # template registration details
```

**What is logged:**

| Level | Event |
|---|---|
| `INFO` | Index created, stored template registered |
| `WARN` | Index creation failed (non-fatal), template registration failed |
| `DEBUG` | (available in consuming app; library keeps INFO minimal) |

**What is never logged:** passwords, truststore passwords (excluded via `@ToString.Exclude`), document contents, search parameters.

---

## Exception Handling

| Exception | When thrown |
|---|---|
| `EsOperationException` | Any ES operation fails after all retry attempts, index creation body build fails, mapping file parse fails |
| `EsTemplateException` | Template file not found, template JSON parse fails, inline `QueryBuilder` parse fails |

Both extend `RuntimeException` — no checked exception handling required in consuming code. Catch them in your application's global exception handler:

```java
@ExceptionHandler(EsOperationException.class)
public ResponseEntity<ErrorResponse> handleEs(EsOperationException ex) {
    log.error("ES operation failed", ex);
    return ResponseEntity.status(503).body(...);
}
```

---

## Running Tests

**Unit tests** (no external dependencies):

```bash
mvn test -pl elastic-common-lib
```

**Integration tests** (requires Docker for Testcontainers):

```bash
mvn verify -pl elastic-common-lib -Pfailsafe
```

Add a test that extends `EsIntegrationTestBase` (to be created) to get a Testcontainers-managed ES instance automatically.

---

## Security

- **No secrets in code.** Use environment variables (`${ES_PASSWORD}`) for all credentials.
- **Password fields** are annotated with `@ToString.Exclude` in `EsProperties` — they will not appear in Spring Boot's config logging or any `toString()` call.
- **Trust-all SSL** (`ssl-enabled: true` with no truststore) must never be used in production. Always provide a proper truststore.
- **Input sanitisation.** The library does not perform query injection prevention on `SearchCriteria` values. Consuming applications must validate and sanitise user input before constructing `SearchCriteria`.
- **No credentials committed.** The `application.yml` shipped in this library contains no credentials. All are injected at runtime.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Connection refused` on startup | ES not running | Start Elasticsearch: `docker run -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8.13.3` |
| `Could not ensure index` WARN on startup | ES reachable but index creation rejected | Check ES logs; ensure the application user has `create_index` privilege |
| `Failed to register ES search templates` WARN | ES not ready when `ApplicationReadyEvent` fires | Template registration is non-fatal; templates will be missing until ES is healthy |
| `EsOperationException: no such index` | Index not yet created | `ensureIndexWithMapping` is called automatically; verify `MappingFileIndexTemplate` path is correct |
| Credentials ignored when `ssl-enabled: true` | Old bug (pre-1.0.0): second `setHttpClientConfigCallback` overwrote the first | Upgrade to ≥ 1.0.0 where all HTTP client config is applied in a single callback |
| `NoSuchBeanDefinitionException: EsOperations` | Auto-configuration not loaded | Ensure `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` is on the classpath |
| Templates not registered | `es-templates/` placed in the library jar, not the consuming app | Move template JSON files to the **consuming application's** `src/main/resources/es-templates/` |

---

## Best Practices

1. **Always inject `EsOperations` by interface**, not `EsOperationsImpl`, to allow mocking in tests.
2. **Define one `MappingFileIndexTemplate` bean per index type** in your application. Keep mapping JSON in `src/main/resources/mappings/`.
3. **Store Mustache templates in the consuming application**, not in the library. The library auto-discovers them.
4. **Use environment variables for all credentials** — never commit passwords to source control.
5. **Use `dynamicSearch` for ad-hoc queries** and stored templates for fixed, performance-critical queries.
6. **Tune `socket-timeout-ms`** for large result sets; the default 30 s may be insufficient for aggregations.
7. **Use `search_after` (via `SearchResult.getSearchAfter()`)** for deep pagination instead of high `from` values to avoid ES memory pressure.
8. **Keep retry settings conservative in production** — high `max-attempts` with short `backoff-ms` can amplify load during an ES outage.

---

## Limitations

- `dynamicSearch` does **not** support nested field queries (type `NESTED` / `OBJECT`).
- The unbounded `search()` method is capped at 10 000 documents (`DEFAULT_MAX_SIZE`). Use `search_after`-based pagination for larger datasets.
- `IndexNameStrategy` appends a hardcoded version suffix (`_v1`). Schema migration tooling is not included.
- No support for Elasticsearch aliases (write/read alias management) — indices are addressed directly by their computed name.
- No circuit-breaker (only retry); a completely unresponsive ES cluster will block threads until timeout × retry attempts elapses.

---

## Future Enhancements

- [ ] Configurable `DEFAULT_MAX_SIZE` via `EsProperties`
- [ ] Circuit-breaker support (Resilience4j `CircuitBreaker`)
- [ ] Alias management API (create/swap read-write aliases)
- [ ] Bulk indexing support (`BulkRequest`)
- [ ] Aggregation result type (`AggregationResult<T>`)
- [ ] Asynchronous operations via `CompletableFuture`
- [ ] Index version migration utilities
- [ ] Spring Boot 3 Observability integration (Micrometer tracing)
- [ ] Native support for `search_after` in stored-template searches
- [ ] JUnit 5 test extension (`@EsTest`) for zero-setup integration testing
