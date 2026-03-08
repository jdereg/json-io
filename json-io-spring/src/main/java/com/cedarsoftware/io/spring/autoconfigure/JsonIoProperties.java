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
 *       show-type-info: MINIMAL_PLUS
 *       skip-null-fields: true
 *       short-meta-keys: false
 *       write-longs-as-strings: false
 *       allow-nan-and-infinity: false
 *       force-map-output-as-two-arrays: false
 *       write-enum-as-json-object: false
 *       cycle-support: true    # applies to JSON only; TOON/JSON5 default to false
 *       json5: false
 *       date-format: ISO
 *       indentation-size: 2
 *       show-root-type-info: true
 *       meta-prefix: AT
 *       toon-delimiter: ","
 *     read:
 *       max-depth: 1000
 *       fail-on-unknown-type: false
 *       return-as-java-maps: false
 *       allow-nan-and-infinity: false
 *       use-unsafe: false
 *       floating-point: DOUBLE
 *       integer-type: LONG
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
        private ShowTypeInfo showTypeInfo = ShowTypeInfo.MINIMAL_PLUS;

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

        /**
         * Force all Maps to be written as two parallel arrays (@keys and @values).
         * Useful when Map keys are complex objects rather than simple Strings.
         */
        private boolean forceMapOutputAsTwoArrays = false;

        /**
         * Write enums as JSON objects with public fields instead of as string names.
         */
        private boolean writeEnumAsJsonObject = false;

        /**
         * Enable cycle detection and reference tracking (@id/@ref).
         * Disable for ~35-40% faster writes on acyclic data.
         */
        private boolean cycleSupport = true;

        /**
         * Enable JSON5 output (unquoted keys, smart quotes, Infinity/NaN support).
         */
        private boolean json5 = false;

        /**
         * Date format for serialization (ISO or LONG).
         */
        private DateFormat dateFormat = DateFormat.ISO;

        /**
         * Number of spaces per indentation level when pretty-printing.
         */
        private int indentationSize = 2;

        /**
         * Include @type on the root object. Useful when the root type is known
         * by the consumer and the @type can be omitted.
         */
        private boolean showRootTypeInfo = true;

        /**
         * Prefix character for meta-keys (@type, @id, @ref, etc.).
         * AT uses '@' (default), DOLLAR uses '$' (useful for MongoDB compatibility).
         */
        private MetaPrefix metaPrefix = MetaPrefix.AT;

        /**
         * Delimiter character for TOON format output.
         * Supported values: ',' (comma, default), '\t' (tab), '|' (pipe).
         */
        private Character toonDelimiter = null;

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

        public boolean isForceMapOutputAsTwoArrays() {
            return forceMapOutputAsTwoArrays;
        }

        public void setForceMapOutputAsTwoArrays(boolean forceMapOutputAsTwoArrays) {
            this.forceMapOutputAsTwoArrays = forceMapOutputAsTwoArrays;
        }

        public boolean isWriteEnumAsJsonObject() {
            return writeEnumAsJsonObject;
        }

        public void setWriteEnumAsJsonObject(boolean writeEnumAsJsonObject) {
            this.writeEnumAsJsonObject = writeEnumAsJsonObject;
        }

        public boolean isCycleSupport() {
            return cycleSupport;
        }

        public void setCycleSupport(boolean cycleSupport) {
            this.cycleSupport = cycleSupport;
        }

        public boolean isJson5() {
            return json5;
        }

        public void setJson5(boolean json5) {
            this.json5 = json5;
        }

        public DateFormat getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(DateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }

        public int getIndentationSize() {
            return indentationSize;
        }

        public void setIndentationSize(int indentationSize) {
            this.indentationSize = indentationSize;
        }

        public boolean isShowRootTypeInfo() {
            return showRootTypeInfo;
        }

        public void setShowRootTypeInfo(boolean showRootTypeInfo) {
            this.showRootTypeInfo = showRootTypeInfo;
        }

        public MetaPrefix getMetaPrefix() {
            return metaPrefix;
        }

        public void setMetaPrefix(MetaPrefix metaPrefix) {
            this.metaPrefix = metaPrefix;
        }

        public Character getToonDelimiter() {
            return toonDelimiter;
        }

        public void setToonDelimiter(Character toonDelimiter) {
            this.toonDelimiter = toonDelimiter;
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

        /**
         * Allow deserialization of package-private classes, inner classes, and
         * classes without accessible constructors using sun.misc.Unsafe (opt-in).
         */
        private boolean useUnsafe = false;

        /**
         * How to parse JSON floating-point numbers (DOUBLE, BIG_DECIMAL, or BOTH).
         */
        private FloatingPoint floatingPoint = FloatingPoint.DOUBLE;

        /**
         * How to parse JSON integer numbers (LONG, BIG_INTEGER, or BOTH).
         */
        private IntegerType integerType = IntegerType.LONG;

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

        public boolean isUseUnsafe() {
            return useUnsafe;
        }

        public void setUseUnsafe(boolean useUnsafe) {
            this.useUnsafe = useUnsafe;
        }

        public FloatingPoint getFloatingPoint() {
            return floatingPoint;
        }

        public void setFloatingPoint(FloatingPoint floatingPoint) {
            this.floatingPoint = floatingPoint;
        }

        public IntegerType getIntegerType() {
            return integerType;
        }

        public void setIntegerType(IntegerType integerType) {
            this.integerType = integerType;
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
         * Extends MINIMAL with optimizations for collections, maps, and convertible types.
         * Omits @type for natural defaults (e.g., ArrayList for List) and types with
         * lossless String round-trips (e.g., ZonedDateTime, UUID, BigDecimal).
         * This is the default for Spring integration.
         */
        MINIMAL_PLUS,
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

    /**
     * Prefix character for JSON meta-keys.
     */
    public enum MetaPrefix {
        /**
         * Use '@' prefix (@type, @id, @ref) — the default.
         */
        AT,
        /**
         * Use '$' prefix ($type, $id, $ref) — useful for MongoDB compatibility.
         */
        DOLLAR
    }

    /**
     * Date serialization format.
     */
    public enum DateFormat {
        /**
         * ISO 8601 format (e.g., "2024-01-15T10:30:00Z").
         */
        ISO,
        /**
         * Long epoch milliseconds (e.g., 1705312200000).
         */
        LONG
    }

    /**
     * How JSON floating-point numbers are parsed.
     */
    public enum FloatingPoint {
        /**
         * Parse as Double (default).
         */
        DOUBLE,
        /**
         * Parse as BigDecimal for arbitrary precision.
         */
        BIG_DECIMAL,
        /**
         * Parse as Double when possible, BigDecimal for large values.
         */
        BOTH
    }

    /**
     * How JSON integer numbers are parsed.
     */
    public enum IntegerType {
        /**
         * Parse as Long (default).
         */
        LONG,
        /**
         * Parse as BigInteger for arbitrary precision.
         */
        BIG_INTEGER,
        /**
         * Parse as Long when possible, BigInteger for large values.
         */
        BOTH
    }
}
