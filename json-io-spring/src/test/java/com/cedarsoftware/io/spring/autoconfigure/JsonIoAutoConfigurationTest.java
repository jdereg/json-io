package com.cedarsoftware.io.spring.autoconfigure;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.customizer.ReadOptionsCustomizer;
import com.cedarsoftware.io.spring.customizer.WriteOptionsCustomizer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonIoAutoConfiguration}.
 */
class JsonIoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JsonIoAutoConfiguration.class));

    @Test
    void defaultBeansAreCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReadOptions.class);
            assertThat(context).hasSingleBean(WriteOptions.class);
        });
    }

    @Test
    void prettyPrintCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.pretty-print=true")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isPrettyPrint()).isTrue();
                });
    }

    @Test
    void maxDepthCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.max-depth=500")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.getMaxDepth()).isEqualTo(500);
                });
    }

    @Test
    void skipNullFieldsCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.skip-null-fields=true")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isSkipNullFields()).isTrue();
                });
    }

    @Test
    void showTypeInfoAlwaysCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.show-type-info=ALWAYS")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isAlwaysShowingType()).isTrue();
                });
    }

    @Test
    void showTypeInfoNeverCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.show-type-info=NEVER")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isNeverShowingType()).isTrue();
                });
    }

    @Test
    void customizersAreApplied() {
        contextRunner
                .withBean(ReadOptionsCustomizer.class, () -> builder -> builder.maxDepth(250))
                .withBean(WriteOptionsCustomizer.class, () -> builder -> builder.prettyPrint(true))
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(readOptions.getMaxDepth()).isEqualTo(250);
                    assertThat(writeOptions.isPrettyPrint()).isTrue();
                });
    }

    @Test
    void existingBeanTakesPrecedence() {
        contextRunner
                .withBean(ReadOptions.class, () -> {
                    return new com.cedarsoftware.io.ReadOptionsBuilder()
                            .maxDepth(100)
                            .build();
                })
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.getMaxDepth()).isEqualTo(100);
                });
    }
}
