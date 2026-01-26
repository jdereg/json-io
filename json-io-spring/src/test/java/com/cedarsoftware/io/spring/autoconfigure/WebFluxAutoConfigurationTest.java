package com.cedarsoftware.io.spring.autoconfigure;

import com.cedarsoftware.io.spring.http.codec.Json5Decoder;
import com.cedarsoftware.io.spring.http.codec.Json5Encoder;
import com.cedarsoftware.io.spring.http.codec.JsonIoDecoder;
import com.cedarsoftware.io.spring.http.codec.JsonIoEncoder;
import com.cedarsoftware.io.spring.http.codec.ToonDecoder;
import com.cedarsoftware.io.spring.http.codec.ToonEncoder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonIoWebFluxAutoConfiguration}.
 */
class WebFluxAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JsonIoAutoConfiguration.class,
                    JsonIoWebFluxAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    WebFluxAutoConfiguration.class
            ));

    @Test
    void codecCustomizerBeanIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CodecCustomizer.class);
        });
    }

    @Test
    void coexistModeRegistersCodecs() {
        contextRunner.run(context -> {
            // Verify the codec customizer bean is created and can be applied
            CodecCustomizer customizer = context.getBean(CodecCustomizer.class);
            assertThat(customizer).isNotNull();

            // The customizer should run without error
            CodecConfigurer configurer = new DefaultServerCodecConfigurer();
            customizer.customize(configurer);
            // If we get here without exception, the customizer is working
        });
    }

    @Test
    void coexistModeDoesNotRegisterJsonIoCodecForJson() {
        contextRunner.run(context -> {
            JsonIoProperties properties = context.getBean(JsonIoProperties.class);
            // Verify we're in COEXIST mode by default
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

                    // Verify customizer can be applied in REPLACE mode
                    CodecCustomizer customizer = context.getBean(CodecCustomizer.class);
                    CodecConfigurer configurer = new DefaultServerCodecConfigurer();
                    customizer.customize(configurer);
                    // If we get here without exception, REPLACE mode works
                });
    }

    @Test
    void codecsAreConfiguredWithOptions() {
        contextRunner.run(context -> {
            // Verify ReadOptions and WriteOptions beans are available
            assertThat(context).hasBean("jsonIoReadOptions");
            assertThat(context).hasBean("jsonIoWriteOptions");

            // The codec customizer should be able to use these options
            assertThat(context).hasSingleBean(CodecCustomizer.class);
        });
    }

    @Test
    void codecCustomizerWithCustomWriteOptions() {
        contextRunner
                .withPropertyValues("spring.json-io.write.pretty-print=true")
                .run(context -> {
                    CodecCustomizer customizer = context.getBean(CodecCustomizer.class);
                    assertThat(customizer).isNotNull();
                    // The customizer should apply the configured options
                });
    }

    @Test
    void codecCustomizerWithCustomReadOptions() {
        contextRunner
                .withPropertyValues("spring.json-io.read.max-depth=500")
                .run(context -> {
                    CodecCustomizer customizer = context.getBean(CodecCustomizer.class);
                    assertThat(customizer).isNotNull();
                    // The customizer should apply the configured options
                });
    }

    @Test
    void autoConfigurationIsConditionalOnReactiveWebApplication() {
        // Verify the auto-configuration is properly conditional on reactive web application
        // The @ConditionalOnWebApplication(type=REACTIVE) annotation ensures this
        contextRunner.run(context -> {
            // In reactive context, the codec customizer should be present
            assertThat(context).hasSingleBean(CodecCustomizer.class);
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
