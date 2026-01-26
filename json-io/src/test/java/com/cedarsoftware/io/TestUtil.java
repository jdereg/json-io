package com.cedarsoftware.io;

import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

/**
 * Useful utilities for use in unit testing.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
public class TestUtil {
    private static final Logger logger = Logger.getLogger(TestUtil.class.getName());

    public static <T> T serializeDeserialize(T initial) {
        String json = toJson(initial, null);
        @SuppressWarnings("unchecked")
        Class<T> root = initial == null ? null : (Class<T>) initial.getClass();
        return toJava(json, null).asClass(root);
    }

    public static <T> Object serializeDeserializeAsMaps(T initial) {
        String json = toJson(initial, new WriteOptionsBuilder().showTypeInfoNever().build());
        return toMaps(json, null).asClass(null);
    }

    public static boolean isDebug() {
        return TestUtil.debug;
    }

    private static class TestInfo {
        long nanos;
        Throwable t;
        String json;
        Object obj;
    }

    private static TestInfo writeJsonIo(Object obj, WriteOptions writeOptions) {
        TestInfo testInfo = new TestInfo();
        try {
            long start = System.nanoTime();
            testInfo.json = JsonIo.toJson(obj, writeOptions);
            testInfo.nanos = System.nanoTime() - start;
        } catch (Exception e) {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo writeGSON(Object obj) {
        TestInfo testInfo = new TestInfo();
        try {
            Gson gson = new Gson();
            long start = System.nanoTime();
            String json = gson.toJson(obj);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.json = json;
        } catch (Throwable t) {
            testInfo.t = t;
        }
        return testInfo;
    }

    private static TestInfo writeJackson(Object obj) {
        TestInfo testInfo = new TestInfo();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            long start = System.nanoTime();
            testInfo.json = objectMapper.writeValueAsString(obj);
            testInfo.nanos = System.nanoTime() - start;
        } catch (Throwable t) {
            testInfo.t = t;
        }
        return testInfo;
    }

    /**
     * Write JSON using default options. Creates a new WriteOptionsBuilder to pick up current permanent aliases.
     */
    public static String toJson(Object obj) {
        return toJson(obj, new WriteOptionsBuilder().build());
    }

    /**
     * Generally, use this API to write JSON. It will do so using json-io and other serializers, so that
     * timing statistics can be measured.
     */
    public static String toJson(Object obj, WriteOptions writeOptions) {
        totalWrites++;
        // json-io
        TestInfo jsonIoTestInfo = writeJsonIo(obj, writeOptions);
        TestInfo gsonTestInfo = writeGSON(obj);
        TestInfo jacksonTestInfo = writeJackson(obj);

        if (jsonIoTestInfo.json != null) {
            printLine(jsonIoTestInfo.json);
        }

        if (jsonIoTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null) { // Only add times when all parsers succeeded
            totalJsonWrite += jsonIoTestInfo.nanos;
            totalGsonWrite += gsonTestInfo.nanos;
            totalJacksonWrite += jacksonTestInfo.nanos;
        } else {
            if (jsonIoTestInfo.t != null) {
                jsonIoWriteFails++;
            }
            if (gsonTestInfo.t != null) {
                gsonWriteFails++;
            }
            if (jacksonTestInfo.t != null) {
                jacksonWriteFails++;
            }
        }

        if (jsonIoTestInfo.t != null) {
            try {
                throw jsonIoTestInfo.t;
            } catch (Throwable t) {
                throw (RuntimeException) t;
            }
        }
        return jsonIoTestInfo.json;
    }

    private static TestInfo readJsonIo(String json, ReadOptions options, Class<?> root) {
        TestInfo testInfo = new TestInfo();
        try {
            long start = System.nanoTime();
            testInfo.obj = JsonIo.toJava(json, options).asClass(root);
            testInfo.nanos = System.nanoTime() - start;
        } catch (Exception e) {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo readGson(String json) {
        TestInfo testInfo = new TestInfo();
        try {
            Gson gson = new Gson();
            long start = System.nanoTime();
            Map<?, ?> map = gson.fromJson(json, Map.class);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = map;
        } catch (Exception e) {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo readJackson(String json) {
        TestInfo testInfo = new TestInfo();
        try {
            ObjectMapper mapper = new ObjectMapper();
            long start = System.nanoTime();
            Map<?, ?> map = mapper.readValue(json, Map.class);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = map;
        } catch (Exception e) {
            testInfo.t = e;
        }
        return testInfo;
    }


    public static void printLine(String s) {
        if (debug) {
            logger.fine(s);
        }
    }

    public static void writeTimings() {
        logger.info("Write JSON");
        logger.info("  json-io: " + (totalJsonWrite / 1_000_000.0) + " ms");
        logger.info("  GSON: " + (totalGsonWrite / 1_000_000.0) + " ms");
        logger.info("  Jackson: " + (totalJacksonWrite / 1_000_000.0) + " ms");
        logger.info("Read JSON");
        logger.info("  json-io: " + (totalJsonRead / 1_000_000.0) + " ms");
        logger.info("  GSON: " + (totalGsonRead / 1_000_000.0) + " ms");
        logger.info("  Jackson: " + (totalJacksonRead / 1_000_000.0) + " ms");
        logger.info("Write Fails:");
        logger.info("  json-io: " + jsonIoWriteFails + " / " + totalWrites);
        logger.info("  GSON: " + gsonWriteFails + " / " + totalWrites);
        logger.info("  Jackson: " + jacksonWriteFails + " / " + totalWrites);
        logger.info("Read Fails");
        logger.info("  json-io: " + jsonIoReadFails + " / " + totalReads);
        logger.info("  GSON: " + gsonReadFails + " / " + totalReads);
        logger.info("  Jackson: " + jacksonReadFails + " / " + totalReads);
    }

    /**
     * Builder for testing JSON deserialization with json-io, GSON, and Jackson.
     * Matches the API of JsonIo.JavaStringBuilder.
     */
    public static class JavaTestBuilder {
        private final String json;
        private final ReadOptions readOptions;

        JavaTestBuilder(String json, ReadOptions readOptions) {
            this.json = json;
            this.readOptions = readOptions != null ? readOptions : new ReadOptionsBuilder().build();
        }

        /**
         * Complete deserialization to a specific class.
         * Tests json-io, GSON, and Jackson and collects timing statistics.
         */
        @SuppressWarnings("unchecked")
        public <T> T asClass(Class<T> root) {
            totalReads++;

            TestInfo jsonIoTestInfo = readJsonIo(json, readOptions, root);
            TestInfo gsonTestInfo = readGson(json);
            TestInfo jacksonTestInfo = readJackson(json);

            if (jsonIoTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null) {
                totalJsonRead += jsonIoTestInfo.nanos;
                totalGsonRead += gsonTestInfo.nanos;
                totalJacksonRead += jacksonTestInfo.nanos;
            } else {
                if (jsonIoTestInfo.t != null) {
                    jsonIoReadFails++;
                }
                if (gsonTestInfo.t != null) {
                    gsonReadFails++;
                }
                if (jacksonTestInfo.t != null) {
                    jacksonReadFails++;
                }
            }

            if (jsonIoTestInfo.t != null) {
                try {
                    throw jsonIoTestInfo.t;
                } catch (Throwable t) {
                    throw (RuntimeException) t;
                }
            }

            return (T) jsonIoTestInfo.obj;
        }

        /**
         * Complete deserialization using generic type information.
         * Tests json-io, GSON, and Jackson and collects timing statistics.
         */
        @SuppressWarnings("unchecked")
        public <T> T asType(TypeHolder<T> typeHolder) {
            totalReads++;

            TestInfo jsonIoTestInfo = readJsonIoAsType(json, readOptions, typeHolder);
            TestInfo gsonTestInfo = readGson(json);
            TestInfo jacksonTestInfo = readJackson(json);

            if (jsonIoTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null) {
                totalJsonRead += jsonIoTestInfo.nanos;
                totalGsonRead += gsonTestInfo.nanos;
                totalJacksonRead += jacksonTestInfo.nanos;
            } else {
                if (jsonIoTestInfo.t != null) {
                    jsonIoReadFails++;
                }
                if (gsonTestInfo.t != null) {
                    gsonReadFails++;
                }
                if (jacksonTestInfo.t != null) {
                    jacksonReadFails++;
                }
            }

            if (jsonIoTestInfo.t != null) {
                try {
                    throw jsonIoTestInfo.t;
                } catch (Throwable t) {
                    throw (RuntimeException) t;
                }
            }

            return (T) jsonIoTestInfo.obj;
        }
    }

    /**
     * Read JSON using JsonIo with generic type.
     */
    private static <T> TestInfo readJsonIoAsType(String json, ReadOptions options, TypeHolder<T> typeHolder) {
        TestInfo testInfo = new TestInfo();
        try {
            long start = System.nanoTime();
            testInfo.obj = JsonIo.toJava(json, options).asType(typeHolder);
            testInfo.nanos = System.nanoTime() - start;
        } catch (Exception e) {
            testInfo.t = e;
        }
        return testInfo;
    }

    /**
     * Parse JSON into typed Java objects. Returns a builder for completing the conversion.
     * Tests json-io, GSON, and Jackson for performance comparison.
     *
     * Use this for Java Object Mode (requires classes on classpath).
     */
    public static JavaTestBuilder toJava(String json, ReadOptions readOptions) {
        return new JavaTestBuilder(json, readOptions);
    }

    /**
     * Parse JSON into Map graph without requiring Java classes. Returns a builder for completing the conversion.
     * Tests json-io, GSON, and Jackson for performance comparison.
     *
     * Use this for Map Mode (no classes required).
     */
    public static JavaTestBuilder toMaps(String json, ReadOptions readOptions) {
        ReadOptions mapOptions = new ReadOptionsBuilder(readOptions)
                .returnAsJsonObjects()
                .build();
        return new JavaTestBuilder(json, mapOptions);
    }

    public static int count(CharSequence content, CharSequence token) {
        if (content == null || token == null) {
            return 0;
        }

        String source = content.toString();
        if (source.isEmpty()) {
            return 0;
        }
        String sub = token.toString();
        if (sub.isEmpty()) {
            return 0;
        }

        int answer = 0;
        int idx = 0;

        while (true) {
            idx = source.indexOf(sub, idx);
            if (idx == -1) {
                return answer;
            }
            ++answer;
            ++idx;
        }
    }

    private static long totalJsonWrite;
    private static long totalGsonWrite;
    private static long totalJacksonWrite;
    private static long totalJsonRead;
    private static long totalGsonRead;
    private static long totalJacksonRead;
    private static long jsonIoWriteFails;
    private static long gsonWriteFails;
    private static long jacksonWriteFails;
    private static long jsonIoReadFails;
    private static long gsonReadFails;
    private static long jacksonReadFails;
    private static long totalReads;
    private static long totalWrites;
    private static final boolean debug = false;

}
