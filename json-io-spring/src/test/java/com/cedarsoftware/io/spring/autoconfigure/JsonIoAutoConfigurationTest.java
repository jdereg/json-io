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

    // --- New Write property tests ---

    @Test
    void forceMapOutputAsTwoArraysCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.force-map-output-as-two-arrays=true")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isForceMapOutputAsTwoArrays()).isTrue();
                });
    }

    @Test
    void writeEnumAsJsonObjectCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.write-enum-as-json-object=true")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isEnumPublicFieldsOnly()).isTrue();
                });
    }

    @Test
    void cycleSupportCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.cycle-support=false")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isCycleSupport()).isFalse();
                });
    }

    @Test
    void json5CanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.json5=true")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isJson5UnquotedKeys()).isTrue();
                    assertThat(writeOptions.isJson5SmartQuotes()).isTrue();
                    assertThat(writeOptions.isJson5InfinityNaN()).isTrue();
                });
    }

    @Test
    void dateFormatLongCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.date-format=LONG")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isLongDateFormat()).isTrue();
                });
    }

    // --- New Read property tests ---

    @Test
    void useUnsafeCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.use-unsafe=true")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.isUseUnsafe()).isTrue();
                });
    }

    @Test
    void floatingPointBigDecimalCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.floating-point=BIG_DECIMAL")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.isFloatingPointBigDecimal()).isTrue();
                });
    }

    @Test
    void floatingPointBothCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.floating-point=BOTH")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.isFloatingPointBoth()).isTrue();
                });
    }

    @Test
    void integerTypeBigIntegerCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.integer-type=BIG_INTEGER")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.isIntegerTypeBigInteger()).isTrue();
                });
    }

    @Test
    void integerTypeBothCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.read.integer-type=BOTH")
                .run(context -> {
                    ReadOptions readOptions = context.getBean(ReadOptions.class);
                    assertThat(readOptions.isIntegerTypeBoth()).isTrue();
                });
    }

    // --- Additional Write property tests ---

    @Test
    void indentationSizeCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.indentation-size=4")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.getIndentationSize()).isEqualTo(4);
                });
    }

    @Test
    void showRootTypeInfoCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.show-root-type-info=false")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.isShowingRootTypeInfo()).isFalse();
                });
    }

    @Test
    void metaPrefixDollarCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.meta-prefix=DOLLAR")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.getMetaPrefixOverride()).isEqualTo('$');
                });
    }

    @Test
    void toonDelimiterCanBeConfigured() {
        contextRunner
                .withPropertyValues("spring.json-io.write.toon-delimiter=|")
                .run(context -> {
                    WriteOptions writeOptions = context.getBean(WriteOptions.class);
                    assertThat(writeOptions.getToonDelimiter()).isEqualTo('|');
                });
    }
}
