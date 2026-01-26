package com.cedarsoftware.io.spring.autoconfigure;

import java.util.List;

import com.cedarsoftware.io.spring.http.converter.Json5HttpMessageConverter;
import com.cedarsoftware.io.spring.http.converter.JsonIoHttpMessageConverter;
import com.cedarsoftware.io.spring.http.converter.ToonHttpMessageConverter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Jackson coexistence modes (COEXIST and REPLACE).
 */
class JacksonCoexistenceModeTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JsonIoAutoConfiguration.class,
                    JsonIoWebMvcAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    WebMvcAutoConfiguration.class
            ));

    @Test
    void coexistModeIsDefault() {
        contextRunner.run(context -> {
            JsonIoProperties properties = context.getBean(JsonIoProperties.class);
            assertThat(properties.getIntegration().getJacksonMode())
                    .isEqualTo(JsonIoProperties.JacksonMode.COEXIST);
        });
    }

    @Test
    void coexistModeHasJson5Converter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Json5HttpMessageConverter.class);
        });
    }

    @Test
    void coexistModeHasToonConverter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ToonHttpMessageConverter.class);
        });
    }

    @Test
    void coexistModeHasJsonIoConverter() {
        // JsonIoHttpMessageConverter bean is always created,
        // but only added to the converter list in REPLACE mode
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JsonIoHttpMessageConverter.class);
        });
    }

    @Test
    void replaceModeConfiguredViaProperty() {
        contextRunner
                .withPropertyValues("spring.json-io.integration.jackson-mode=REPLACE")
                .run(context -> {
                    JsonIoProperties properties = context.getBean(JsonIoProperties.class);
                    assertThat(properties.getIntegration().getJacksonMode())
                            .isEqualTo(JsonIoProperties.JacksonMode.REPLACE);
                });
    }

    @Test
    void replaceModeHasAllConverters() {
        contextRunner
                .withPropertyValues("spring.json-io.integration.jackson-mode=REPLACE")
                .run(context -> {
                    assertThat(context).hasSingleBean(JsonIoHttpMessageConverter.class);
                    assertThat(context).hasSingleBean(Json5HttpMessageConverter.class);
                    assertThat(context).hasSingleBean(ToonHttpMessageConverter.class);
                });
    }

    @Test
    void coexistModeKeepsJacksonConvertersInList() {
        contextRunner.run(context -> {
            List<HttpMessageConverter<?>> converters = getMessageConverters(context);

            // Should have JSON5 and TOON converters from json-io
            boolean hasJson5 = converters.stream()
                    .anyMatch(c -> c instanceof Json5HttpMessageConverter);
            boolean hasToon = converters.stream()
                    .anyMatch(c -> c instanceof ToonHttpMessageConverter);

            assertThat(hasJson5).isTrue();
            assertThat(hasToon).isTrue();

            // Jackson converters should still be present for application/json
            boolean hasJackson = converters.stream()
                    .anyMatch(c -> c.getClass().getName().toLowerCase().contains("jackson"));
            assertThat(hasJackson).isTrue();
        });
    }

    @Test
    void replaceModeRemovesJacksonConvertersFromList() {
        contextRunner
                .withPropertyValues("spring.json-io.integration.jackson-mode=REPLACE")
                .run(context -> {
                    List<HttpMessageConverter<?>> converters = getMessageConverters(context);

                    // Should have all three json-io converters
                    boolean hasJsonIo = converters.stream()
                            .anyMatch(c -> c instanceof JsonIoHttpMessageConverter);
                    boolean hasJson5 = converters.stream()
                            .anyMatch(c -> c instanceof Json5HttpMessageConverter);
                    boolean hasToon = converters.stream()
                            .anyMatch(c -> c instanceof ToonHttpMessageConverter);

                    assertThat(hasJsonIo).isTrue();
                    assertThat(hasJson5).isTrue();
                    assertThat(hasToon).isTrue();

                    // Jackson converters should be removed
                    boolean hasJackson = converters.stream()
                            .anyMatch(c -> c.getClass().getName().toLowerCase().contains("jackson"));
                    assertThat(hasJackson).isFalse();
                });
    }

    @Test
    void json5ConverterIsAtHighPriority() {
        contextRunner.run(context -> {
            List<HttpMessageConverter<?>> converters = getMessageConverters(context);

            // Find positions of converters
            int json5Index = -1;
            for (int i = 0; i < converters.size(); i++) {
                if (converters.get(i) instanceof Json5HttpMessageConverter) {
                    json5Index = i;
                    break;
                }
            }

            // JSON5 should be near the beginning (first few positions)
            assertThat(json5Index).isGreaterThanOrEqualTo(0);
            assertThat(json5Index).isLessThan(5);
        });
    }

    @Test
    void toonConverterIsAtHighPriority() {
        contextRunner.run(context -> {
            List<HttpMessageConverter<?>> converters = getMessageConverters(context);

            // Find positions of converters
            int toonIndex = -1;
            for (int i = 0; i < converters.size(); i++) {
                if (converters.get(i) instanceof ToonHttpMessageConverter) {
                    toonIndex = i;
                    break;
                }
            }

            // TOON should be near the beginning (first few positions)
            assertThat(toonIndex).isGreaterThanOrEqualTo(0);
            assertThat(toonIndex).isLessThan(5);
        });
    }

    private List<HttpMessageConverter<?>> getMessageConverters(WebApplicationContext context) {
        // Get the message converters from the RequestMappingHandlerAdapter
        RequestMappingHandlerAdapter adapter = context.getBean(RequestMappingHandlerAdapter.class);
        return adapter.getMessageConverters();
    }
}
