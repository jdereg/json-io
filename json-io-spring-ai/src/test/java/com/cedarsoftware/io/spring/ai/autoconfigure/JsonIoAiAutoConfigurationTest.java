package com.cedarsoftware.io.spring.ai.autoconfigure;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.spring.ai.converter.ToonToolCallResultConverter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonIoAiAutoConfiguration}.
 */
class JsonIoAiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JsonIoAiAutoConfiguration.class));

    @Test
    void defaultBeansAreCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("toonAiWriteOptions");
            assertThat(context).hasBean("toonAiReadOptions");
            assertThat(context).hasSingleBean(ToonToolCallResultConverter.class);
        });
    }

    @Test
    void defaultWriteOptionsHaveNoTypeInfo() {
        contextRunner.run(context -> {
            WriteOptions writeOptions = context.getBean("toonAiWriteOptions", WriteOptions.class);
            assertThat(writeOptions.isNeverShowingType()).isTrue();
        });
    }

    @Test
    void defaultWriteOptionsHaveKeyFoldingEnabled() {
        contextRunner.run(context -> {
            WriteOptions writeOptions = context.getBean("toonAiWriteOptions", WriteOptions.class);
            assertThat(writeOptions.isToonKeyFolding()).isTrue();
        });
    }

    @Test
    void defaultReadOptionsHavePermissiveToon() {
        contextRunner.run(context -> {
            ReadOptions readOptions = context.getBean("toonAiReadOptions", ReadOptions.class);
            assertThat(readOptions.isStrictToon()).isFalse();
        });
    }

    @Test
    void keyFoldingCanBeDisabledViaProperties() {
        contextRunner
                .withPropertyValues("spring.json-io.ai.tool-call.key-folding=false")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean("toonAiWriteOptions", WriteOptions.class);
                    assertThat(writeOptions.isToonKeyFolding()).isFalse();
                });
    }

    @Test
    void strictToonCanBeEnabledViaProperties() {
        contextRunner
                .withPropertyValues("spring.json-io.ai.output.strict-toon=true")
                .run(context -> {
                    ReadOptions readOptions = context.getBean("toonAiReadOptions", ReadOptions.class);
                    assertThat(readOptions.isStrictToon()).isTrue();
                });
    }

    @Test
    void customToonToolCallResultConverterTakesPrecedence() {
        contextRunner
                .withBean(ToonToolCallResultConverter.class, ToonToolCallResultConverter::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ToonToolCallResultConverter.class);
                });
    }

    @Test
    void customWriteOptionsBeanTakesPrecedence() {
        WriteOptions custom = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .toonKeyFolding(false)
                .build();

        contextRunner
                .withBean("toonAiWriteOptions", WriteOptions.class, () -> custom)
                .run(context -> {
                    WriteOptions writeOptions = context.getBean("toonAiWriteOptions", WriteOptions.class);
                    assertThat(writeOptions.isToonKeyFolding()).isFalse();
                });
    }
}
