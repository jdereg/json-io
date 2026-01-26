package com.cedarsoftware.io.spring.autoconfigure;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoProperties.JacksonMode;
import com.cedarsoftware.io.spring.http.codec.Json5Decoder;
import com.cedarsoftware.io.spring.http.codec.Json5Encoder;
import com.cedarsoftware.io.spring.http.codec.JsonIoDecoder;
import com.cedarsoftware.io.spring.http.codec.JsonIoEncoder;
import com.cedarsoftware.io.spring.http.codec.ToonDecoder;
import com.cedarsoftware.io.spring.http.codec.ToonEncoder;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Spring Boot auto-configuration for json-io WebFlux support.
 * <p>
 * Registers Encoders and Decoders for JSON, JSON5, and TOON formats
 * for use with WebFlux controllers and WebClient.
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
@AutoConfiguration(after = {JsonIoAutoConfiguration.class, WebFluxAutoConfiguration.class})
@ConditionalOnClass({JsonIo.class, WebFluxConfigurer.class})
@ConditionalOnWebApplication(type = Type.REACTIVE)
public class JsonIoWebFluxAutoConfiguration {

    private final JsonIoProperties properties;
    private final ReadOptions readOptions;
    private final WriteOptions writeOptions;

    public JsonIoWebFluxAutoConfiguration(JsonIoProperties properties,
                                           ReadOptions readOptions,
                                           WriteOptions writeOptions) {
        this.properties = properties;
        this.readOptions = readOptions;
        this.writeOptions = writeOptions;
    }

    /**
     * CodecCustomizer that registers json-io encoders and decoders.
     */
    @Bean
    public CodecCustomizer jsonIoCodecCustomizer() {
        return configurer -> {
            JacksonMode jacksonMode = properties.getIntegration().getJacksonMode();

            CodecConfigurer.CustomCodecs customCodecs = configurer.customCodecs();

            // Always register JSON5 and TOON codecs
            customCodecs.register(new Json5Encoder(writeOptions));
            customCodecs.register(new Json5Decoder(readOptions));
            customCodecs.register(new ToonEncoder(writeOptions));
            customCodecs.register(new ToonDecoder(readOptions));

            // Handle JSON codec based on Jackson mode
            if (jacksonMode == JacksonMode.REPLACE) {
                // Register json-io JSON codec (will be used instead of Jackson)
                customCodecs.register(new JsonIoEncoder(writeOptions));
                customCodecs.register(new JsonIoDecoder(readOptions));

                // Remove default Jackson codecs
                configurer.defaultCodecs().jackson2JsonEncoder(null);
                configurer.defaultCodecs().jackson2JsonDecoder(null);
            }
            // In COEXIST mode, Jackson handles application/json
        };
    }
}
