package com.cedarsoftware.io.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for json-io Spring integration.
 * <p>
 * Configure json-io behavior via application.properties or application.yml:
 * </p>
 * <pre>
 * spring:
 *   json-io:
 *     write:
 *       pretty-print: true
 *       show-type-info: MINIMAL
 *       skip-null-fields: true
 *     read:
 *       max-depth: 1000
 *       fail-on-unknown-type: false
 *     integration:
 *       jackson-mode: COEXIST
 * </pre>
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
@ConfigurationProperties(prefix = "spring.json-io")
public class JsonIoProperties {

    private final Write write = new Write();
    private final Read read = new Read();
    private final Integration integration = new Integration();

    public Write getWrite() {
        return write;
    }

    public Read getRead() {
        return read;
    }

    public Integration getIntegration() {
        return integration;
    }

    /**
     * Write/serialization configuration options.
     */
    public static class Write {
        /**
         * Enable pretty printing of JSON output.
         */
        private boolean prettyPrint = false;

        /**
         * When to include type information in JSON output.
         */
        private ShowTypeInfo showTypeInfo = ShowTypeInfo.MINIMAL;

        /**
         * Skip null field values in output.
         */
        private boolean skipNullFields = false;

        /**
         * Short meta-key names (@type becomes @t, @id becomes @i, etc.)
         */
        private boolean shortMetaKeys = false;

        /**
         * Write longs as strings to prevent JavaScript precision loss.
         */
        private boolean writeLongsAsStrings = false;

        /**
         * Allow NaN and Infinity values in numeric output.
         */
        private boolean allowNanAndInfinity = false;

        public boolean isPrettyPrint() {
            return prettyPrint;
        }

        public void setPrettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
        }

        public ShowTypeInfo getShowTypeInfo() {
            return showTypeInfo;
        }

        public void setShowTypeInfo(ShowTypeInfo showTypeInfo) {
            this.showTypeInfo = showTypeInfo;
        }

        public boolean isSkipNullFields() {
            return skipNullFields;
        }

        public void setSkipNullFields(boolean skipNullFields) {
            this.skipNullFields = skipNullFields;
        }

        public boolean isShortMetaKeys() {
            return shortMetaKeys;
        }

        public void setShortMetaKeys(boolean shortMetaKeys) {
            this.shortMetaKeys = shortMetaKeys;
        }

        public boolean isWriteLongsAsStrings() {
            return writeLongsAsStrings;
        }

        public void setWriteLongsAsStrings(boolean writeLongsAsStrings) {
            this.writeLongsAsStrings = writeLongsAsStrings;
        }

        public boolean isAllowNanAndInfinity() {
            return allowNanAndInfinity;
        }

        public void setAllowNanAndInfinity(boolean allowNanAndInfinity) {
            this.allowNanAndInfinity = allowNanAndInfinity;
        }
    }

    /**
     * Read/deserialization configuration options.
     */
    public static class Read {
        /**
         * Maximum depth for nested object parsing.
         */
        private int maxDepth = 1000;

        /**
         * Fail when encountering unknown type in JSON.
         */
        private boolean failOnUnknownType = false;

        /**
         * Return Java Maps instead of fully typed objects.
         */
        private boolean returnAsJavaMaps = false;

        /**
         * Allow NaN and Infinity values in numeric fields.
         */
        private boolean allowNanAndInfinity = false;

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isFailOnUnknownType() {
            return failOnUnknownType;
        }

        public void setFailOnUnknownType(boolean failOnUnknownType) {
            this.failOnUnknownType = failOnUnknownType;
        }

        public boolean isReturnAsJavaMaps() {
            return returnAsJavaMaps;
        }

        public void setReturnAsJavaMaps(boolean returnAsJavaMaps) {
            this.returnAsJavaMaps = returnAsJavaMaps;
        }

        public boolean isAllowNanAndInfinity() {
            return allowNanAndInfinity;
        }

        public void setAllowNanAndInfinity(boolean allowNanAndInfinity) {
            this.allowNanAndInfinity = allowNanAndInfinity;
        }
    }

    /**
     * Spring integration configuration options.
     */
    public static class Integration {
        /**
         * How json-io coexists with Jackson.
         */
        private JacksonMode jacksonMode = JacksonMode.COEXIST;

        public JacksonMode getJacksonMode() {
            return jacksonMode;
        }

        public void setJacksonMode(JacksonMode jacksonMode) {
            this.jacksonMode = jacksonMode;
        }
    }

    /**
     * When to include type information in JSON output.
     */
    public enum ShowTypeInfo {
        /**
         * Always include @type metadata.
         */
        ALWAYS,
        /**
         * Include @type only when needed for polymorphic types.
         */
        MINIMAL,
        /**
         * Never include @type metadata.
         */
        NEVER
    }

    /**
     * How json-io coexists with Jackson in Spring applications.
     */
    public enum JacksonMode {
        /**
         * json-io handles JSON5 and TOON; Jackson handles standard JSON.
         * This is the default mode for gradual adoption.
         */
        COEXIST,
        /**
         * json-io handles all formats; Jackson converters are removed.
         */
        REPLACE
    }
}
