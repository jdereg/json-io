# json-io Spring Integration Guide

This guide covers integrating json-io with Spring Boot applications, providing automatic JSON, JSON5, and TOON format support for your REST APIs.

> **Trademark Notice:** Spring is a trademark of Broadcom Inc. and/or its subsidiaries. This project is not affiliated with or endorsed by Broadcom.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration Properties](#configuration-properties)
- [Content Negotiation](#content-negotiation)
- [Customizer Beans](#customizer-beans)
- [Jackson Coexistence Modes](#jackson-coexistence-modes)
- [WebFlux and WebClient](#webflux-and-webclient)
- [Media Types](#media-types)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Installation

Add the Spring Boot starter to your project:

**Maven**
```xml
<dependency>
    <groupId>com.cedarsoftware</groupId>
    <artifactId>json-io-spring-boot-starter</artifactId>
    <version>4.88.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'com.cedarsoftware:json-io-spring-boot-starter:4.88.0'
```

**Requirements:**
- Spring Boot 3.x (Java 17+)
- json-io is included transitively

## Quick Start

Once you add the dependency, json-io automatically integrates with Spring MVC. Your REST controllers immediately support JSON, JSON5, and TOON formats:

```java
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
}
```

Clients can request different formats using the `Accept` header:

```bash
# Standard JSON (default)
curl -H "Accept: application/json" http://localhost:8080/api/users/1

# JSON5 (with comments and relaxed syntax)
curl -H "Accept: application/vnd.json5" http://localhost:8080/api/users/1

# TOON (40-50% fewer tokens, ideal for LLMs)
curl -H "Accept: application/vnd.toon" http://localhost:8080/api/users/1
```

## Configuration Properties

Configure json-io behavior in `application.properties` or `application.yml`:

### Write Options

```yaml
spring:
  json-io:
    write:
      # Enable pretty printing
      pretty-print: false

      # When to include @type metadata
      # ALWAYS - Always include type info
      # MINIMAL - Only when needed for polymorphism
      # MINIMAL_PLUS - Extends MINIMAL with optimizations for collections, maps, and convertible types (default)
      # NEVER - Never include type info
      show-type-info: MINIMAL_PLUS

      # Skip fields with null values
      skip-null-fields: false

      # Use short meta-key names (@t instead of @type, @i instead of @id)
      short-meta-keys: false

      # Write longs as strings (prevents JavaScript precision loss)
      write-longs-as-strings: false

      # Allow NaN and Infinity in numeric output
      allow-nan-and-infinity: false
```

### Read Options

```yaml
spring:
  json-io:
    read:
      # Maximum depth for nested objects (prevents stack overflow attacks)
      max-depth: 1000

      # Fail when encountering unknown type in JSON
      fail-on-unknown-type: false

      # Return Java Maps instead of typed objects
      return-as-java-maps: false

      # Allow NaN and Infinity values in numeric fields
      allow-nan-and-infinity: false
```

### Integration Options

```yaml
spring:
  json-io:
    integration:
      # How json-io coexists with Jackson
      # COEXIST - json-io handles JSON5/TOON, Jackson handles JSON (default)
      # REPLACE - json-io handles all formats, Jackson removed
      jackson-mode: COEXIST
```

## Content Negotiation

Spring automatically selects the appropriate converter based on HTTP headers:

### Request Format (Content-Type)

The `Content-Type` header determines how request bodies are parsed:

```bash
# Send JSON
curl -X POST -H "Content-Type: application/json" \
     -d '{"name":"John","age":30}' \
     http://localhost:8080/api/users

# Send JSON5 (with comments)
curl -X POST -H "Content-Type: application/vnd.json5" \
     -d '{name: "John", age: 30, /* comment */}' \
     http://localhost:8080/api/users

# Send TOON
curl -X POST -H "Content-Type: application/vnd.toon" \
     -d 'name: John
age: 30' \
     http://localhost:8080/api/users
```

### Response Format (Accept)

The `Accept` header determines the response format:

```bash
# Request JSON response
curl -H "Accept: application/json" http://localhost:8080/api/users/1

# Request TOON response (great for LLM integrations)
curl -H "Accept: application/vnd.toon" http://localhost:8080/api/users/1
```

## Customizer Beans

For programmatic configuration, implement customizer interfaces and register them as Spring beans:

### ReadOptionsCustomizer

```java
@Configuration
public class JsonIoConfig {

    @Bean
    public ReadOptionsCustomizer myReadCustomizer() {
        return builder -> builder
            .maxDepth(500)
            .allowNanAndInfinity(true)
            .addClassFactory(MyClass.class, new MyClassFactory());
    }
}
```

### WriteOptionsCustomizer

```java
@Bean
public WriteOptionsCustomizer myWriteCustomizer() {
    return builder -> builder
        .prettyPrint(true)
        .skipNullFields(true)
        .addCustomWriter(LocalDate.class, new MyLocalDateWriter());
}
```

### Multiple Customizers

Multiple customizers are applied in order. Use `@Order` to control sequencing:

```java
@Bean
@Order(1)
public WriteOptionsCustomizer baseCustomizer() {
    return builder -> builder.prettyPrint(true);
}

@Bean
@Order(2)
public WriteOptionsCustomizer securityCustomizer() {
    return builder -> builder
        .addExcludedField(User.class, "password")
        .addExcludedField(User.class, "ssn");
}
```

## Jackson Coexistence Modes

json-io can work alongside Jackson or replace it entirely:

### COEXIST Mode (Default)

Jackson handles `application/json`, json-io handles `application/vnd.json5` and `application/vnd.toon`:

```yaml
spring:
  json-io:
    integration:
      jackson-mode: COEXIST
```

This is ideal for gradual adoption—your existing JSON APIs continue using Jackson while you can use JSON5 and TOON for specific endpoints or clients.

### REPLACE Mode

json-io handles all formats, Jackson converters are removed:

```yaml
spring:
  json-io:
    integration:
      jackson-mode: REPLACE
```

Use this when you want json-io's features (cycle support, polymorphic types) for all JSON handling.

## WebFlux and WebClient

json-io provides reactive Encoders and Decoders for WebFlux applications and WebClient.

### Reactive Controllers

WebFlux controllers automatically support JSON, JSON5, and TOON:

```java
@RestController
@RequestMapping("/api")
public class ReactiveController {

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping("/users")
    public Flux<User> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping("/users")
    public Mono<User> createUser(@RequestBody Mono<User> user) {
        return user.flatMap(userService::save);
    }
}
```

### WebClient Usage

Use WebClient with json-io formats for external API calls:

```java
@Service
public class LlmService {

    private final WebClient webClient;

    public LlmService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.example.com")
            .build();
    }

    // Request TOON format for LLM efficiency
    public Mono<LlmResponse> sendToLlm(LlmRequest request) {
        return webClient.post()
            .uri("/llm/complete")
            .contentType(MediaType.parseMediaType("application/vnd.toon"))
            .accept(MediaType.parseMediaType("application/vnd.toon"))
            .bodyValue(request)
            .retrieve()
            .bodyToMono(LlmResponse.class);
    }
}
```

### Customizing WebClient Codecs

For fine-grained control, customize WebClient codecs directly:

```java
@Bean
public WebClient customWebClient(ReadOptions readOptions, WriteOptions writeOptions) {
    return WebClient.builder()
        .codecs(configurer -> {
            configurer.customCodecs().register(new ToonEncoder(writeOptions));
            configurer.customCodecs().register(new ToonDecoder(readOptions));
        })
        .build();
}
```

### Available Codecs

| Codec | Media Type | Description |
|-------|------------|-------------|
| `JsonIoEncoder` / `JsonIoDecoder` | `application/json` | Standard JSON |
| `Json5Encoder` / `Json5Decoder` | `application/vnd.json5` | JSON5 format |
| `ToonEncoder` / `ToonDecoder` | `application/vnd.toon` | TOON format |

## Media Types

| Format | Media Type | Description |
|--------|------------|-------------|
| JSON | `application/json` | Standard JSON (RFC 8259) |
| JSON5 | `application/vnd.json5` | JSON5 with comments, trailing commas, unquoted keys |
| TOON | `application/vnd.toon` | Token-Oriented Object Notation (40-50% fewer tokens) |
| TOON | `application/vnd.toon+json` | Alternative TOON media type |

### Using Media Types in Code

```java
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

@GetMapping(value = "/data", produces = JsonIoMediaTypes.APPLICATION_TOON_VALUE)
public MyData getToonData() {
    return myData;  // Always returns TOON format
}
```

## Examples

### LLM Integration with TOON

TOON format reduces token count by 40-50%, making it ideal for LLM applications:

```java
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    @GetMapping(value = "/context", produces = "application/vnd.toon")
    public ContextData getContext() {
        // Returns TOON format, saving tokens when sending to LLMs
        return contextService.buildContext();
    }
}
```

Client code:
```java
// Request TOON format
HttpHeaders headers = new HttpHeaders();
headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.toon")));

ResponseEntity<String> response = restTemplate.exchange(
    "/api/llm/context",
    HttpMethod.GET,
    new HttpEntity<>(headers),
    String.class
);

// Send TOON directly to LLM API
String toonContext = response.getBody();
```

### Custom Type Handling

```java
@Configuration
public class JsonIoConfig {

    @Bean
    public WriteOptionsCustomizer customTypeHandling() {
        return builder -> builder
            // Exclude sensitive fields
            .addExcludedField(User.class, "password")
            .addExcludedField(User.class, "apiKey")

            // Custom writer for Money type
            .addCustomWriter(Money.class, (obj, showType, output, options) -> {
                Money money = (Money) obj;
                output.write(String.format("\"%s %s\"",
                    money.getCurrency(), money.getAmount()));
            });
    }

    @Bean
    public ReadOptionsCustomizer customTypeReading() {
        return builder -> builder
            // Custom factory for immutable types
            .addClassFactory(ImmutableUser.class, new ImmutableUserFactory());
    }
}
```

### Handling Cyclic References

json-io automatically handles object cycles that would break Jackson/Gson:

```java
@Entity
public class Employee {
    private String name;
    private Employee manager;      // Can reference another Employee
    private List<Employee> reports; // Can include self-references
}

@GetMapping("/org-chart")
public Employee getOrgChart() {
    // json-io handles cycles with @id/@ref
    return employeeService.getOrgChart();
}
```

## Troubleshooting

### JSON5/TOON endpoints return 406 Not Acceptable

Ensure the starter is on the classpath and auto-configuration is not disabled:

```java
// Check that auto-config is enabled
@SpringBootApplication
public class MyApp {
    // Do NOT exclude JsonIoAutoConfiguration
}
```

### Jackson still handling application/json in REPLACE mode

Verify no other libraries are adding Jackson converters. Check your configuration:

```java
@Bean
public WebMvcConfigurer debugConverters() {
    return new WebMvcConfigurer() {
        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.forEach(c -> System.out.println(c.getClass().getName()));
        }
    };
}
```

### Type information missing in responses

Configure `show-type-info`:

```yaml
spring:
  json-io:
    write:
      show-type-info: ALWAYS  # or MINIMAL/MINIMAL_PLUS for polymorphic types only
```

### Stack overflow on deeply nested objects

Increase the max depth limit:

```yaml
spring:
  json-io:
    read:
      max-depth: 2000
```

### Custom ReadOptions/WriteOptions not being used

Ensure your customizer beans are being created:

```java
@Bean
public ReadOptionsCustomizer myCustomizer() {
    System.out.println("Customizer created");  // Debug
    return builder -> builder.maxDepth(500);
}
```

Or provide your own `ReadOptions`/`WriteOptions` beans to override auto-configuration:

```java
@Bean
public ReadOptions jsonIoReadOptions() {
    return new ReadOptionsBuilder()
        .closeStream(false)
        .maxDepth(500)
        .build();
}
```

---

For more information, see:
- [json-io User Guide](/user-guide.md)
- [WriteOptions Reference](/user-guide-writeOptions.md)
- [ReadOptions Reference](/user-guide-readOptions.md)
- [TOON Format Specification](https://toonformat.dev/)
