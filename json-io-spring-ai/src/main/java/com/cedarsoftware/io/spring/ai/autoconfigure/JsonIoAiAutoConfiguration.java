package com.cedarsoftware.io.spring.ai.autoconfigure;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.spring.ai.converter.ToonToolCallResultConverter;

import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for json-io Spring AI TOON integration.
 *
 * <p>Activates when both {@link JsonIo} and {@link ToolCallResultConverter} are on the
 * classpath, providing pre-configured beans for TOON-based tool call result
 * serialization.</p>
 *
 * <p>Note: {@code ToonBeanOutputConverter} is NOT auto-configured because it is
 * parameterized per target type — users instantiate it themselves, same as
 * Spring AI's own {@code BeanOutputConverter}.</p>
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
@ConditionalOnClass({JsonIo.class, ToolCallResultConverter.class})
@EnableConfigurationProperties(JsonIoAiProperties.class)
public class JsonIoAiAutoConfiguration {

    private final JsonIoAiProperties properties;

    public JsonIoAiAutoConfiguration(JsonIoAiProperties properties) {
        this.properties = properties;
    }

    /**
     * WriteOptions for TOON tool call result serialization.
     * Optimized for LLM consumption: no type info, key folding configurable.
     */
    @Bean
    @ConditionalOnMissingBean(name = "toonAiWriteOptions")
    public WriteOptions toonAiWriteOptions() {
        return new WriteOptionsBuilder()
                .showTypeInfoNever()
                .toonKeyFolding(properties.getToolCall().isKeyFolding())
                .build();
    }

    /**
     * ReadOptions for parsing TOON responses from LLMs.
     * Strict TOON parsing configurable via properties.
     */
    @Bean
    @ConditionalOnMissingBean(name = "toonAiReadOptions")
    public ReadOptions toonAiReadOptions() {
        return new ReadOptionsBuilder()
                .strictToon(properties.getOutput().isStrictToon())
                .build();
    }

    /**
     * Default ToonToolCallResultConverter using the auto-configured WriteOptions.
     */
    @Bean
    @ConditionalOnMissingBean(ToonToolCallResultConverter.class)
    public ToonToolCallResultConverter toonToolCallResultConverter(WriteOptions toonAiWriteOptions) {
        return new ToonToolCallResultConverter(toonAiWriteOptions);
    }
}
