# elastic-common-lib

A reusable, Spring Boot auto-configured library that wraps the [Elasticsearch Java client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html).  
Drop it into any Spring Boot 3 application as a JAR dependency and get a fully wired `EsOperations` bean with CRUD, template-based search, dynamic query building, auto index creation, and resilient retry — all driven by `application.yml` properties.

---

## Table of Contents

- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Core API — `EsOperations`](#core-api--esoperations)
- [Stored Search Templates](#stored-search-templates)
- [Dynamic Query Builder](#dynamic-query-builder)
- [Index Mapping Templates](#index-mapping-templates)
- [Auto Index Management](#auto-index-management)
- [Retry Mechanism](#retry-mechanism)
- [SSL / TLS Support](#ssl--tls-support)
- [Running Tests](#running-tests)

---

## Features

- **Zero-boilerplate auto-configuration** — all beans wired automatically via `spring-boot-autoconfigure`.
- **Unified CRUD API** — index, get, update (script upsert), delete by alias + document ID.
- **Template-based search** — Mustache stored scripts auto-registered from `classpath:es-templates/**/*.json` on startup.
- **Paginated search** — first-class pagination with `from`/`size` support.
- **Dynamic query builder** — builds `bool/must` queries at runtime from `IndexTemplate` field type metadata (term, terms, match, range, multi_match).
- **Auto index creation** — lazily creates missing indices (with or without explicit mappings) before every operation.
- **Resilience4j retry** — configurable exponential-backoff retry wrapping every ES call.
- **SSL / TLS support** — optional truststore-backed or trust-all SSL context.

---

## Project Structure

```
elastic-common-lib/
├── src/main/java/com/yourorg/elasticcommon/
│   ├── config/
│   │   ├── EsClientConfiguration.java   # Spring @Configuration — all bean wiring
│   │   └── EsProperties.java            # @ConfigurationProperties (prefix = "es")
│   ├── core/
│   │   ├── EsOperations.java            # Public API interface
│   │   └── EsOperationsImpl.java        # Implementation
│   ├── exception/
│   │   └── EsOperationException.java    # Unchecked runtime exception
│   ├── index/
│   │   ├── EsIndexManager.java          # Auto index creation (cached)
│   │   └── IndexNameStrategy.java       # Index naming helpers
│   ├── model/
│   │   ├── FieldDefinition.java         # Single field metadata
│   │   ├── FieldType.java               # Enum: KEYWORD, TEXT, DATE, INTEGER …
│   │   ├── IndexTemplate.java           # Interface for index/field metadata
│   │   ├── MappingFileIndexTemplate.java # JSON-file-backed IndexTemplate
│   │   ├── PaginationRequest.java
│   │   ├── SearchCriteria.java
│   │   ├── SearchRequest.java
│   │   └── SearchResult.java
│   ├── query/
│   │   ├── DynamicQueryBuilder.java     # Builds ES Query from IndexTemplate + SearchCriteria
│   │   └── QueryBuilder.java
│   ├── retry/
│   │   └── RetryExecutor.java           # Resilience4j retry wrapper
│   └── template/
│       ├── EsTemplateException.java
│       ├── EsTemplateInitializer.java   # Registers Mustache stored scripts on startup
│       ├── TemplateRegistry.java
│       └── TemplateResolver.java
└── src/main/resources/
    ├── META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── es-templates/                    # Mustache search templates (auto-registered)
        └── customer/
            ├── search-by-status.json
            └── search-fulltext.json
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x |
| Elasticsearch | 8.x |
| Maven | 3.8+ |

---

## Installation

Build and install the library to your local Maven repository:

```bash
mvn clean install -pl elastic-common-lib
```

Add it as a dependency in your consuming application's `pom.xml`:

```xml
<dependency>
    <groupId>com.yourorg</groupId>
    <artifactId>elastic-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

No `@EnableXxx` annotation is required — Spring Boot picks up the auto-configuration automatically.

---

## Configuration

Add the following to your application's `application.yml`:

```yaml
es:
  hosts:
    - http://localhost:9200          # one or more ES nodes
  username: elastic                  # optional, omit for unauthenticated clusters
  password: changeme
  ssl-enabled: false                 # set true to enable TLS
  truststore-path:                   # path to JKS truststore (SSL only)
  truststore-password:
  connect-timeout-ms: 5000           # default 5 s
  socket-timeout-ms: 30000           # default 30 s
  retry:
    max-attempts: 3                  # default 3
    backoff-ms: 50                   # initial backoff; doubles on each attempt
```

Multiple hosts (for a cluster):

```yaml
es:
  hosts:
    - http://es-node-1:9200
    - http://es-node-2:9200
    - http://es-node-3:9200
```

---

## Core API — `EsOperations`

Inject `EsOperations` directly into your service:

```java
@Service
public class CustomerService {

    private final EsOperations esOperations;

    public CustomerService(EsOperations esOperations) {
        this.esOperations = esOperations;
    }

    // Index a document
    public void save(Customer customer) {
        esOperations.index("customers", customer.getId(), customer);
    }

    // Get by ID
    public Optional<Customer> findById(String id) {
        return esOperations.getById("customers", id, Customer.class);
    }

    // Delete
    public void remove(String id) {
        esOperations.delete("customers", id);
    }

    // Script upsert
    public void updateStatus(String id, String newStatus, Customer upsertFallback) {
        esOperations.updateWithScript(
            "customers", id,
            "ctx._source.status = params.status",
            Map.of("status", newStatus),
            upsertFallback
        );
    }
}
```

### Full method reference

| Method | Description |
|---|---|
| `index(alias, docId, doc)` | Index or overwrite a document |
| `getById(alias, docId, Class<T>)` | Fetch a single document by ID |
| `delete(alias, docId)` | Delete a document |
| `updateWithScript(alias, docId, script, params, upsertDoc)` | Inline script update with upsert fallback |
| `search(templateKey, SearchRequest, Class<T>)` | Stored-template search (unbounded) |
| `paginationSearch(templateKey, PaginationRequest, Class<T>)` | Stored-template search with pagination |
| `count(templateKey, SearchRequest)` | Count matching docs via stored template |
| `dynamicSearch(alias, IndexTemplate, SearchCriteria, from, size, Class<T>)` | Runtime query built from field-type metadata |

---

## Stored Search Templates

Place Mustache JSON files under `src/main/resources/es-templates/` in **any** classpath module.  
`EsTemplateInitializer` scans `classpath*:es-templates/**/*.json` on `ApplicationReadyEvent` and registers every file as an Elasticsearch stored script.

### Template key → stored script ID mapping

The file path relative to `es-templates/` (without `.json`) becomes the template key.  
Slashes are replaced with dashes to form the ES script ID.

| File path | Template key | ES stored script ID |
|---|---|---|
| `es-templates/customer/search-by-status.json` | `customer/search-by-status` | `customer-search-by-status` |

### Example template file

```json
{
  "from": {{from}},
  "size": {{size}},
  "query": {
    "bool": {
      "must": [
        { "term": { "customerId": "{{customerId}}" } },
        { "term": { "status":     "{{status}}"     } }
      ]
    }
  }
}
```

### Using a template

```java
SearchRequest request = SearchRequest.builder()
    .indexAlias("customers")
    .params(Map.of("customerId", "C001", "status", "ACTIVE"))
    .build();

SearchResult<Customer> result = esOperations.search(
    "customer/search-by-status", request, Customer.class);
```

For paginated results:

```java
PaginationRequest page = PaginationRequest.builder()
    .indexAlias("customers")
    .params(Map.of("customerId", "C001", "status", "ACTIVE"))
    .from(0)
    .size(20)
    .build();

SearchResult<Customer> result = esOperations.paginationSearch(
    "customer/search-by-status", page, Customer.class);
```

---

## Dynamic Query Builder

`DynamicQueryBuilder` builds a `bool/must` query at runtime by inspecting field type metadata from an `IndexTemplate`.

### Query type resolution per field

| Field type | Value type | ES query clause |
|---|---|---|
| `KEYWORD`, `BOOLEAN`, numeric | Single value | `term` |
| `KEYWORD`, `BOOLEAN`, numeric | `List<?>` | `terms` |
| `TEXT` | Single value | `match` |
| `DATE`, numeric | `Map<String,Object>` (`gte`/`lte`) | `range` |
| *(any)* | `fulltextQuery` set | `multi_match` across TEXT fields |

### Usage

```java
SearchCriteria criteria = SearchCriteria.builder()
    .fieldCriteria(Map.of(
        "status",     "ACTIVE",
        "customerId", "C001",
        "age",        Map.of("gte", 18, "lte", 65)
    ))
    .fulltextQuery("premium customer")
    .build();

SearchResult<Customer> result = esOperations.dynamicSearch(
    "customers", myIndexTemplate, criteria, 0, 50, Customer.class);
```

---

## Index Mapping Templates

Extend `MappingFileIndexTemplate` and point to a classpath JSON file that describes your index settings and field mappings:

```java
@Component
public class CustomerIndexTemplate extends MappingFileIndexTemplate {
    public CustomerIndexTemplate() {
        super("mappings/customer-mapping.json");
    }
}
```

### Mapping file format

```json
{
  "name": "customer-template",
  "index_patterns": ["customers-*"],
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1
  },
  "mappings": {
    "properties": {
      "customerId":  { "type": "keyword" },
      "name":        { "type": "text",    "analyzer": "standard" },
      "status":      { "type": "keyword" },
      "age":         { "type": "integer" },
      "createdAt":   { "type": "date",    "format": "strict_date_optional_time" },
      "description": { "type": "text",    "searchable": true, "sortable": false }
    }
  }
}
```

**Supported field types:** `keyword`, `text`, `date`, `integer`, `long`, `float`, `double`, `boolean`, `nested`, `object`.

Optional per-field attributes: `analyzer`, `format`, `searchable` (default `true`), `sortable` (inferred from type; always `false` for `text`, `nested`, `object`).

---

## Auto Index Management

`EsIndexManager` ensures an index exists before every operation — with a local in-process cache so the existence check (HTTP HEAD) is only done once per index alias per JVM lifetime.

| Method | Behaviour |
|---|---|
| `ensureIndex(alias)` | Creates the index with default settings if absent |
| `ensureIndexWithMapping(alias, template)` | Creates the index using `settings` + `mappings` from the `MappingFileIndexTemplate` |

No manual index creation is required; the library handles it transparently.

---

## Retry Mechanism

Every `EsOperations` call is wrapped by `RetryExecutor`, which is backed by **Resilience4j** with exponential backoff:

```
attempt 1  → immediate
attempt 2  → backoff-ms × 2¹
attempt 3  → backoff-ms × 2²
```

Configure via `application.yml`:

```yaml
es:
  retry:
    max-attempts: 3
    backoff-ms: 50
```

---

## SSL / TLS Support

Enable SSL and optionally provide a JKS truststore:

```yaml
es:
  ssl-enabled: true
  truststore-path: /etc/ssl/es-truststore.jks
  truststore-password: secret
```

If `truststore-path` is omitted while `ssl-enabled: true`, the library falls back to a **trust-all** SSL context (suitable for development only).

---

## Running Tests

Integration tests use **Testcontainers** to spin up a real Elasticsearch 8 node — no external cluster required.

```bash
mvn test -pl elastic-common-lib
```

Unit tests (no container needed):

```bash
mvn test -pl elastic-common-lib -Dtest="QueryBuilderTest,TemplateResolverTest"
```

