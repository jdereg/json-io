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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;
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
// Order after Boot's WebFlux auto-config by NAME (not class literal) so this compiles/runs on both
// Spring Boot 3.x and 4.x — Boot 4 relocated the class; an absent name is simply ignored.
@AutoConfiguration(after = JsonIoAutoConfiguration.class, afterName = {
        "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration",  // Spring Boot 3.x
        "org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration"        // Spring Boot 4.x
})
@ConditionalOnClass({JsonIo.class, WebFluxConfigurer.class})
@ConditionalOnWebApplication(type = Type.REACTIVE)
public class JsonIoWebFluxAutoConfiguration {

    private final JsonIoProperties properties;
    private final ReadOptions readOptions;
    private final WriteOptions writeOptions;
    private final WriteOptions toonWriteOptions;

    public JsonIoWebFluxAutoConfiguration(JsonIoProperties properties,
                                           ReadOptions readOptions,
                                           WriteOptions writeOptions,
                                           @Qualifier("toonWriteOptions") WriteOptions toonWriteOptions) {
        this.properties = properties;
        this.readOptions = readOptions;
        this.writeOptions = writeOptions;
        this.toonWriteOptions = toonWriteOptions;
    }

    /**
     * WebFluxConfigurer that registers json-io encoders and decoders on the reactive codec pipeline.
     * <p>
     * Uses Spring Framework's {@link WebFluxConfigurer#configureHttpMessageCodecs(ServerCodecConfigurer)}
     * rather than Spring Boot's {@code CodecCustomizer} so the class carries no Boot-version-specific
     * type — it compiles and runs on both Spring Boot 3.x and 4.x.
     */
    @Bean
    public WebFluxConfigurer jsonIoWebFluxConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
                JacksonMode jacksonMode = properties.getIntegration().getJacksonMode();

                CodecConfigurer.CustomCodecs customCodecs = configurer.customCodecs();

                // Always register JSON5 and TOON codecs (cycleSupport=false)
                customCodecs.register(new Json5Encoder(toonWriteOptions));
                customCodecs.register(new Json5Decoder(readOptions));
                customCodecs.register(new ToonEncoder(toonWriteOptions));
                customCodecs.register(new ToonDecoder(readOptions));

                // Handle JSON codec based on Jackson mode
                if (jacksonMode == JacksonMode.REPLACE) {
                    // Register json-io JSON codec (cycleSupport=true)
                    customCodecs.register(new JsonIoEncoder(writeOptions));
                    customCodecs.register(new JsonIoDecoder(readOptions));

                    // Remove default Jackson codecs
                    configurer.defaultCodecs().jackson2JsonEncoder(null);
                    configurer.defaultCodecs().jackson2JsonDecoder(null);
                }
                // In COEXIST mode, Jackson handles application/json
            }
        };
    }
}
