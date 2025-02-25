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
        String json = toJson(initial);
        return toObjects(json, null);
    }

    public static <T> Object serializeDeserializeAsMaps(T initial) {
        String json = toJson(initial, new WriteOptionsBuilder().showTypeInfoNever().build());
        return toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
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

    /**
     * Generally, use this API to read JSON. It will do so using json-io and other serializers, so that
     * timing statistics can be measured. This version is the simple (no build options version).
     */
    public static <T> T toObjects(String json, Class<T> root) {
        return toObjects(json, new ReadOptionsBuilder().build(), root);
    }

    /**
     * Generally, use this API to read JSON. It will do so using json-io and other serializers, so that
     * timing statistics can be measured. This version is more capable, as it supports build options.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toObjects(final String json, ReadOptions readOptions, Class<T> root) {
        totalReads++;

        TestInfo jsonIoTestInfo = readJsonIo(json, readOptions, root);
        TestInfo gsonTestInfo = readGson(json);
        TestInfo jacksonTestInfo = readJackson(json);

        if (jsonIoTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null) { // only add times when all parsers succeeded
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

    public static JsonObject toObjects(InputStream in, ReadOptions readOptions) {
        return JsonIo.toObjects(in, readOptions, null);
    }

    public static void printLine(String s) {
        if (debug) {
            System.out.println(s);
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

    /**
     * Ensure that the passed in source contains all the Strings passed in the 'contains' parameter AND
     * that they appear in the order they are passed in. This method asserts the presence and order
     * of the specified strings, ignoring case.
     *
     * @param source   Source string to test.
     * @param contains Strings that must appear in the source string in the specified order.
     */
    public static void assertContainsIgnoreCase(String source, String... contains) {
        String lowerSource = source.toLowerCase();
        for (String contain : contains) {
            int idx = lowerSource.indexOf(contain.toLowerCase());
            String msg = "'" + contain + "' not found in '" + lowerSource + "'";
            assert idx >= 0 : msg;
            lowerSource = lowerSource.substring(idx);
        }
    }

    /**
     * Checks whether the source string contains all specified substrings in the given order, ignoring case.
     *
     * @param source   Source string to test.
     * @param contains Strings that must appear in the source string in the specified order.
     * @return True if all specified strings are found in order; otherwise, false.
     */
    public static boolean checkContainsIgnoreCase(String source, String... contains) {
        String lowerSource = source.toLowerCase();
        for (String contain : contains) {
            int idx = lowerSource.indexOf(contain.toLowerCase());
            if (idx == -1) {
                return false;
            }
            lowerSource = lowerSource.substring(idx);
        }
        return true;
    }
}
