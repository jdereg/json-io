package com.cedarsoftware.io.spring.autoconfigure;

import java.util.List;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.JacksonMode;
import com.cedarsoftware.io.spring.http.converter.Json5HttpMessageConverter;
import com.cedarsoftware.io.spring.http.converter.JsonIoHttpMessageConverter;
import com.cedarsoftware.io.spring.http.converter.ToonHttpMessageConverter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot auto-configuration for json-io Web MVC support.
 * <p>
 * Registers HttpMessageConverters for JSON, JSON5, and TOON formats based on
 * the configured Jackson coexistence mode.
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
@AutoConfiguration(after = {JsonIoAutoConfiguration.class, WebMvcAutoConfiguration.class})
@ConditionalOnClass({JsonIo.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
public class JsonIoWebMvcAutoConfiguration {

    private final JsonIoProperties properties;
    private final ReadOptions readOptions;
    private final WriteOptions writeOptions;

    public JsonIoWebMvcAutoConfiguration(JsonIoProperties properties,
                                          ReadOptions readOptions,
                                          WriteOptions writeOptions) {
        this.properties = properties;
        this.readOptions = readOptions;
        this.writeOptions = writeOptions;
    }

    /**
     * Create the JSON HttpMessageConverter.
     * Only created in REPLACE mode; otherwise Jackson handles JSON.
     */
    @Bean
    public JsonIoHttpMessageConverter jsonIoHttpMessageConverter() {
        return new JsonIoHttpMessageConverter(readOptions, writeOptions);
    }

    /**
     * Create the JSON5 HttpMessageConverter.
     */
    @Bean
    public Json5HttpMessageConverter json5HttpMessageConverter() {
        return new Json5HttpMessageConverter(readOptions, writeOptions);
    }

    /**
     * Create the TOON HttpMessageConverter.
     */
    @Bean
    public ToonHttpMessageConverter toonHttpMessageConverter() {
        return new ToonHttpMessageConverter(readOptions, writeOptions);
    }

    /**
     * WebMvcConfigurer that registers json-io converters.
     */
    @Bean
    public WebMvcConfigurer jsonIoWebMvcConfigurer(
            JsonIoHttpMessageConverter jsonConverter,
            Json5HttpMessageConverter json5Converter,
            ToonHttpMessageConverter toonConverter) {

        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                JacksonMode jacksonMode = properties.getIntegration().getJacksonMode();

                // Always add JSON5 and TOON converters at the beginning
                // so they take precedence for their specific media types
                converters.add(0, json5Converter);
                converters.add(0, toonConverter);

                // Handle JSON converter based on Jackson mode
                if (jacksonMode == JacksonMode.REPLACE) {
                    // Remove existing Jackson converters and add json-io JSON converter
                    converters.removeIf(converter ->
                            converter.getClass().getName().contains("jackson") ||
                            converter.getClass().getName().contains("Jackson"));
                    converters.add(0, jsonConverter);
                }
                // In COEXIST mode (default), Jackson handles application/json
            }
        };
    }
}
