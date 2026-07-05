package com.cedarsoftware.io.spring.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonIoWebFluxAutoConfiguration}.
 * <p>
 * Loads only json-io's own auto-configs and asserts against Spring Framework types
 * ({@link WebFluxConfigurer}) rather than Spring Boot types, so the test compiles and runs on both
 * Spring Boot 3.x and 4.x (Boot relocated {@code CodecCustomizer}/{@code WebFluxAutoConfiguration}).
 */
class WebFluxAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JsonIoAutoConfiguration.class,
                    JsonIoWebFluxAutoConfiguration.class
            ));

    @Test
    void webFluxConfigurerBeanIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("jsonIoWebFluxConfigurer");
            assertThat(context.getBean("jsonIoWebFluxConfigurer")).isInstanceOf(WebFluxConfigurer.class);
        });
    }

    @Test
    void coexistModeRegistersCodecs() {
        contextRunner.run(context -> {
            WebFluxConfigurer configurer = context.getBean("jsonIoWebFluxConfigurer", WebFluxConfigurer.class);
            // Registers json-io's JSON5/TOON codecs without error
            configurer.configureHttpMessageCodecs(new DefaultServerCodecConfigurer());
        });
    }

    @Test
    void coexistModeIsDefault() {
        contextRunner.run(context -> {
            JsonIoProperties properties = context.getBean(JsonIoProperties.class);
            assertThat(properties.getIntegration().getJacksonMode())
                    .isEqualTo(JsonIoProperties.JacksonMode.COEXIST);
        });
    }

    @Test
    void replaceModeConfigurable() {
        contextRunner
                .withPropertyValues("spring.json-io.integration.jackson-mode=REPLACE")
                .run(context -> {
                    JsonIoProperties properties = context.getBean(JsonIoProperties.class);
                    assertThat(properties.getIntegration().getJacksonMode())
                            .isEqualTo(JsonIoProperties.JacksonMode.REPLACE);

                    WebFluxConfigurer configurer = context.getBean("jsonIoWebFluxConfigurer", WebFluxConfigurer.class);
                    // REPLACE mode registers json-io JSON codecs and disables Jackson defaults without error
                    configurer.configureHttpMessageCodecs(new DefaultServerCodecConfigurer());
                });
    }

    @Test
    void codecsAreConfiguredWithOptions() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("jsonIoReadOptions");
            assertThat(context).hasBean("jsonIoWriteOptions");
            assertThat(context).hasBean("jsonIoWebFluxConfigurer");
        });
    }

    @Test
    void configurerWithCustomWriteOptions() {
        contextRunner
                .withPropertyValues("spring.json-io.write.pretty-print=true")
                .run(context -> {
                    WebFluxConfigurer configurer = context.getBean("jsonIoWebFluxConfigurer", WebFluxConfigurer.class);
                    configurer.configureHttpMessageCodecs(new DefaultServerCodecConfigurer());
                });
    }

    @Test
    void configurerWithCustomReadOptions() {
        contextRunner
                .withPropertyValues("spring.json-io.read.max-depth=500")
                .run(context -> {
                    WebFluxConfigurer configurer = context.getBean("jsonIoWebFluxConfigurer", WebFluxConfigurer.class);
                    configurer.configureHttpMessageCodecs(new DefaultServerCodecConfigurer());
                });
    }

    @Test
    void autoConfigurationIsConditionalOnReactiveWebApplication() {
        // @ConditionalOnWebApplication(type=REACTIVE) — the configurer is present in a reactive context
        contextRunner.run(context -> {
            assertThat(context).hasBean("jsonIoWebFluxConfigurer");
        });
    }

    @Test
    void propertiesBeanIsAvailable() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JsonIoProperties.class);
        });
    }

    @Test
    void defaultJacksonModeIsCoexist() {
        contextRunner.run(context -> {
            JsonIoProperties properties = context.getBean(JsonIoProperties.class);
            assertThat(properties.getIntegration().getJacksonMode())
                    .isEqualTo(JsonIoProperties.JacksonMode.COEXIST);
        });
    }
}
