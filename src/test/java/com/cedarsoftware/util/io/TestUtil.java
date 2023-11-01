package com.cedarsoftware.util.io;

import com.google.gson.Gson;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Useful utilities for use in unit testing.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class TestUtil
{
    final static Logger logger = LoggerFactory.getLogger(TestUtil.class);

    public static boolean isDebug()
    {
        return debug;
    }

    public static String fetchResource(String name)
    {
        try
        {
            URL url = TestUtil.class.getResource("/" + name);
            Path resPath = Paths.get(url.toURI());
            return new String(Files.readAllBytes(resPath));
        }
        catch (Exception e)
        {
            throw new JsonIoException(e);
        }
    }

    public static <T> T serializeDeserialize(T initial)
    {
        String json = toJson(initial);
        return toJava(json);
    }

    private static class TestInfo
    {
        long nanos;
        Throwable t;
        String json;
        Object obj;
    }

    private static TestInfo writeJsonIo(Object obj, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            long start = System.nanoTime();
            testInfo.json = JsonWriter.objectToJson(obj, args);
            testInfo.nanos = System.nanoTime() - start;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo writeJDK(Object obj, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream());
            long start = System.nanoTime();
            out.writeObject(obj);
            out.flush();
            out.close();
            testInfo.nanos = System.nanoTime() - start;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo writeGSON(Object obj, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            Gson gson = new Gson();
            long start = System.nanoTime();
            String json = gson.toJson(obj);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.json = json;
        }
        catch (Throwable t)
        {
            testInfo.t = t;
        }
        return testInfo;
    }

    private static TestInfo writeJackson(Object obj, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            ObjectMapper objectMapper = new ObjectMapper();
            long start = System.nanoTime();
            testInfo.json = objectMapper.writeValueAsString(obj);
            testInfo.nanos = System.nanoTime() - start;
        }
        catch (Throwable t)
        {
            testInfo.t = t;
        }
        return testInfo;
    }

    public static String toJson(Object obj)
    {
        return toJson(obj, new LinkedHashMap<>());
    }

    /**
     * Generally, use this API to write JSON.  It will do so using json-io and other serializers, so that
     * timing statistics can be measured.
     */
    public static String toJson(Object obj, Map<String, Object> args)
    {
        totalWrites++;
        // json-io
        TestInfo jsonIoTestInfo = writeJsonIo(obj, args);
        TestInfo jdkTestInfo = writeJDK(obj, args);
        TestInfo gsonTestInfo = writeGSON(obj, args);
        TestInfo jacksonTestInfo = writeJackson(obj, args);

        if (jsonIoTestInfo.json != null)
        {
            printLine(jsonIoTestInfo.json);
        }

        if (jsonIoTestInfo.t == null && jdkTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null)
        {   // Only add times when all parsers succeeded
            totalJsonWrite += jsonIoTestInfo.nanos;
            totalJdkWrite += jdkTestInfo.nanos;
            totalGsonWrite += gsonTestInfo.nanos;
            totalJacksonWrite += jacksonTestInfo.nanos;
        }
        else
        {
            if (jsonIoTestInfo.t != null)
            {
                jsonIoWriteFails++;
            }
            if (jdkTestInfo.t != null)
            {
                jdkWriteFails++;
            }
            if (gsonTestInfo.t != null)
            {
                gsonWriteFails++;
            }
            if (jacksonTestInfo.t != null)
            {
                jacksonWriteFails++;
            }
        }

        if (jsonIoTestInfo.t != null)
        {
            try
            {
                throw jsonIoTestInfo.t;
            }
            catch (Throwable t)
            {
                throw (RuntimeException) t;
            }
        }
        return jsonIoTestInfo.json;
    }

    private static TestInfo readJsonIo(String json, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            long start = System.nanoTime();
            testInfo.obj = JsonReader.jsonToJava(json, args);
            testInfo.nanos = System.nanoTime() - start;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo readJdk(String json, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            // Must convert to Java Maps, then write out in JDK binary, and then start timer and read in JDK binary
            Object o = JsonReader.jsonToMaps(json, args);  // Get the JSON into Java Object for JDK serializer to write out
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(o);
            out.flush();
            out.close();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
            long start = System.nanoTime();
            testInfo.obj = in.readObject();
            assert testInfo.obj instanceof JsonObject;
            in.close();
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = o;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo readGson(String json, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            Gson gson = new Gson();
            long start = System.nanoTime();
            Map<?, ?> map = gson.fromJson(json, Map.class);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = map;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    private static TestInfo readJackson(String json, Map<String, Object> args)
    {
        TestInfo testInfo = new TestInfo();
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            long start = System.nanoTime();
            Map<?, ?> map = mapper.readValue(json, Map.class);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = map;
        }
        catch (Exception e)
        {
            testInfo.t = e;
        }
        return testInfo;
    }

    /**
     * Generally, use this API to read JSON.  It will do so using json-io and other serializers, so that
     * timing statistics can be measured.  This version is the simple (no build options version).
     */
    public static <T> T toJava(String json)
    {
        return toJava(json, new LinkedHashMap<>());
    }

    /**
     * Generally, use this API to read JSON.  It will do so using json-io and other serializers, so that
     * timing statistics can be measured.  This version is more capable, as it supports build options.
     */
    public static <T> T toJava(final String json, Map<String, Object> args)
    {
        if (null == json || "".equals(json.trim()))
        {
            return null;
        }
        totalReads++;

        TestInfo jsonIoTestInfo = readJsonIo(json, args);
        TestInfo jdkTestInfo = readJdk(json, args);
        TestInfo gsonTestInfo = readGson(json, args);
        TestInfo jacksonTestInfo = readJackson(json, args);

        if (jsonIoTestInfo.t == null && jdkTestInfo.t == null && gsonTestInfo.t == null && jacksonTestInfo.t == null)
        {   // only add times when all parsers succeeded
            totalJsonRead += jsonIoTestInfo.nanos;
            totalJdkRead += jdkTestInfo.nanos;
            totalGsonRead += gsonTestInfo.nanos;
            totalJacksonRead += jacksonTestInfo.nanos;
        }
        else
        {
            if (jsonIoTestInfo.t != null)
            {
                jsonIoReadFails++;
            }
            if (jdkTestInfo.t != null)
            {
                jdkReadFails++;
            }
            if (gsonTestInfo.t != null)
            {
                gsonReadFails++;
            }
            if (jacksonTestInfo.t != null)
            {
                jacksonReadFails++;
            }
        }

        if (jsonIoTestInfo.t != null)
        {
            try
            {
                throw jsonIoTestInfo.t;
            }
            catch (Throwable t)
            {
                throw (RuntimeException) t;
            }
        }
        
        return (T) jsonIoTestInfo.obj;
    }

    public static void printLine(String s)
    {
        if (debug)
        {
            System.out.println(s);
        }
    }

    public static void writeTimings()
    {
        logger.info("Write JSON");
        logger.info("  json-io: " + (totalJsonWrite / 1000000.0) + " ms");
        logger.info("  JDK binary: " + (totalJdkWrite / 1000000.0) + " ms");
        logger.info("  GSON: " + (totalGsonWrite / 1000000.0) + " ms");
        logger.info("  Jackson: " + (totalJacksonWrite / 1000000.0) + " ms");
        logger.info("Read JSON");
        logger.info("  json-io: " + (totalJsonRead / 1000000.0) + " ms");
        logger.info("  JDK binary: " + (totalJdkRead / 1000000.0) + " ms");
        logger.info("  GSON: " + (totalGsonRead / 1000000.0) + " ms");
        logger.info("  Jackson: " + (totalJacksonRead / 1000000.0) + " ms");
        logger.info("Write Fails:");
        logger.info("  json-io: " + jsonIoWriteFails + " / " + totalWrites);
        logger.info("  JDK: " + jdkWriteFails + " / " + totalWrites);
        logger.info("  GSON: " + gsonWriteFails + " / " + totalWrites);
        logger.info("  Jackson: " + jacksonWriteFails + " / " + totalWrites);
        logger.info("Read Fails");
        logger.info("  json-io: " + jsonIoReadFails + " / " + totalReads);
        logger.info("  JDK: " + jdkReadFails + " / " + totalReads);
        logger.info("  GSON: " + gsonReadFails + " / " + totalReads);
        logger.info("  Jackson: " + jacksonReadFails + " / " + totalReads);
    }

    public static int count(CharSequence content, CharSequence token)
    {
        if (content == null || token == null)
        {
            return 0;
        }

        String source = content.toString();
        if (source.isEmpty())
        {
            return 0;
        }
        String sub = token.toString();
        if (sub.isEmpty())
        {
            return 0;
        }

        int answer = 0;
        int idx = 0;

        while (true)
        {
            idx = source.indexOf(sub, idx);
            if (idx < answer)
            {
                return answer;
            }
            answer = ++answer;
            idx = ++idx;
        }
    }

    private static long totalJsonWrite;
    private static long totalGsonWrite;
    private static long totalJacksonWrite;
    private static long totalJdkWrite;
    private static long totalJsonRead;
    private static long totalGsonRead;
    private static long totalJacksonRead;
    private static long totalJdkRead;
    private static long jsonIoWriteFails;
    private static long gsonWriteFails;
    private static long jacksonWriteFails;
    private static long jdkWriteFails;
    private static long jsonIoReadFails;
    private static long gsonReadFails;
    private static long jacksonReadFails;
    private static long jdkReadFails;
    private static long totalReads;
    private static long totalWrites;
    private static boolean debug = false;
}
