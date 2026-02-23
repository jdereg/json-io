package com.cedarsoftware.io.spring.autoconfigure;

import java.util.List;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.DateFormat;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.FloatingPoint;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.IntegerType;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.MetaPrefix;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.ShowTypeInfo;
import com.cedarsoftware.io.spring.customizer.ReadOptionsCustomizer;
import com.cedarsoftware.io.spring.customizer.WriteOptionsCustomizer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for json-io.
 * <p>
 * Provides ReadOptions and WriteOptions beans based on configuration properties
 * and customizer beans.
 * </p>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@AutoConfiguration
@ConditionalOnClass(JsonIo.class)
@EnableConfigurationProperties(JsonIoProperties.class)
public class JsonIoAutoConfiguration {

    private final JsonIoProperties properties;

    public JsonIoAutoConfiguration(JsonIoProperties properties) {
        this.properties = properties;
    }

    /**
     * Create ReadOptions bean based on properties and customizers.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReadOptions jsonIoReadOptions(ObjectProvider<ReadOptionsCustomizer> customizers) {
        ReadOptionsBuilder builder = new ReadOptionsBuilder()
                .closeStream(false)  // Spring manages stream lifecycle
                .maxDepth(properties.getRead().getMaxDepth())
                .failOnUnknownType(properties.getRead().isFailOnUnknownType());

        builder.allowNanAndInfinity(properties.getRead().isAllowNanAndInfinity());
        builder.useUnsafe(properties.getRead().isUseUnsafe());

        // Configure floating-point parsing
        FloatingPoint fp = properties.getRead().getFloatingPoint();
        switch (fp) {
            case BIG_DECIMAL:
                builder.floatPointBigDecimal();
                break;
            case BOTH:
                builder.floatPointBoth();
                break;
            case DOUBLE:
            default:
                builder.floatPointDouble();
                break;
        }

        // Configure integer parsing
        IntegerType it = properties.getRead().getIntegerType();
        switch (it) {
            case BIG_INTEGER:
                builder.integerTypeBigInteger();
                break;
            case BOTH:
                builder.integerTypeBoth();
                break;
            case LONG:
            default:
                builder.integerTypeLong();
                break;
        }

        // Apply customizers
        List<ReadOptionsCustomizer> customizerList = customizers.orderedStream().toList();
        for (ReadOptionsCustomizer customizer : customizerList) {
            customizer.customize(builder);
        }

        return builder.build();
    }

    /**
     * Create WriteOptions bean based on properties and customizers.
     */
    @Bean
    @ConditionalOnMissingBean
    public WriteOptions jsonIoWriteOptions(ObjectProvider<WriteOptionsCustomizer> customizers) {
        WriteOptionsBuilder builder = new WriteOptionsBuilder()
                .closeStream(false)  // Spring manages stream lifecycle
                .prettyPrint(properties.getWrite().isPrettyPrint())
                .skipNullFields(properties.getWrite().isSkipNullFields())
                .shortMetaKeys(properties.getWrite().isShortMetaKeys())
                .writeLongsAsStrings(properties.getWrite().isWriteLongsAsStrings())
                .allowNanAndInfinity(properties.getWrite().isAllowNanAndInfinity())
                .forceMapOutputAsTwoArrays(properties.getWrite().isForceMapOutputAsTwoArrays())
                .writeEnumAsJsonObject(properties.getWrite().isWriteEnumAsJsonObject())
                .cycleSupport(properties.getWrite().isCycleSupport())
                .indentationSize(properties.getWrite().getIndentationSize());

        // Configure root type info
        if (properties.getWrite().isShowRootTypeInfo()) {
            builder.showRootTypeInfo();
        } else {
            builder.omitRootTypeInfo();
        }

        // Configure meta-key prefix (@ vs $)
        MetaPrefix metaPrefix = properties.getWrite().getMetaPrefix();
        switch (metaPrefix) {
            case DOLLAR:
                builder.useMetaPrefixDollar();
                break;
            case AT:
            default:
                builder.useMetaPrefixAt();
                break;
        }

        // Configure TOON delimiter
        Character toonDelimiter = properties.getWrite().getToonDelimiter();
        if (toonDelimiter != null) {
            builder.toonDelimiter(toonDelimiter);
        }

        // Configure JSON5 output
        if (properties.getWrite().isJson5()) {
            builder.json5();
        }

        // Configure date format
        DateFormat dateFormat = properties.getWrite().getDateFormat();
        switch (dateFormat) {
            case LONG:
                builder.longDateFormat();
                break;
            case ISO:
            default:
                builder.isoDateFormat();
                break;
        }

        // Configure type info
        ShowTypeInfo showTypeInfo = properties.getWrite().getShowTypeInfo();
        switch (showTypeInfo) {
            case ALWAYS:
                builder.showTypeInfoAlways();
                break;
            case NEVER:
                builder.showTypeInfoNever();
                break;
            case MINIMAL:
                builder.showTypeInfoMinimal();
                break;
            case MINIMAL_PLUS:
            default:
                builder.showTypeInfoMinimalPlus();
                break;
        }

        // Apply customizers
        List<WriteOptionsCustomizer> customizerList = customizers.orderedStream().toList();
        for (WriteOptionsCustomizer customizer : customizerList) {
            customizer.customize(builder);
        }

        return builder.build();
    }
}
