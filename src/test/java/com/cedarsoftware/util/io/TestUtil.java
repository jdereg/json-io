package com.cedarsoftware.util.io;

import com.google.gson.Gson;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
            return new String(Files.readAllBytes(resPath), "UTF-8");
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

    public static String toJson(Object obj)
    {
        return toJson(obj, new LinkedHashMap<>());
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
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
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
            gson.toJson(obj);
            testInfo.json = JsonWriter.objectToJson(obj, args);
            testInfo.nanos = System.nanoTime() - start;
        }
        catch (Throwable t)
        {
            testInfo.t = t;
        }
        return testInfo;
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

        if (jsonIoTestInfo.json != null)
        {
            printLine(jsonIoTestInfo.json);
        }

        if (jsonIoTestInfo.t == null && jdkTestInfo.t == null && gsonTestInfo.t == null)
        {   // Only add times when all parsers succeeded
            totalJsonWrite += jsonIoTestInfo.nanos;
            totalJdkWrite += jdkTestInfo.nanos;
            totalGsonWrite += gsonTestInfo.nanos;
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
            Object o = JsonReader.jsonToJava(json, args);
            testInfo.nanos = System.nanoTime() - start;
            testInfo.obj = o;
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
            long start = System.nanoTime();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
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
            Map<String, Object> map = gson.fromJson(json, Map.class);
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
        
        if (jsonIoTestInfo.t == null && jdkTestInfo.t == null && gsonTestInfo.t == null)
        {   // only add times when all parsers succeeded
            totalJsonRead += jsonIoTestInfo.nanos;
            totalJdkRead += jdkTestInfo.nanos;
            totalGsonRead += gsonTestInfo.nanos;
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

    public static Map toMap(String json, Map<String, Object> args)
    {
        if (args == null)
        {
            args = ((Map<String, Object>) (new ArrayList<>()));
        }

        long startRead1 = System.nanoTime();
        ByteArrayInputStream ba;

        ba = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        args.putAll(new ReadOptionsBuilder().returnAsMaps().build());
        JsonReader jr = new JsonReader(ba, args);
        Object o = jr.readObject();
        jr.close();

        long endRead1 = System.nanoTime();

        try
        {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(o);
            out.flush();
            out.close();

            long startRead2 = System.nanoTime();
            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream input = new ObjectInputStream(bin);
            input.readObject();
            input.close();
            long endRead2 = System.nanoTime();

//            totalJsonRead += endRead1 - startRead1;
//            totalJdkRead += endRead2 - startRead2;
            double t1 = (double) (endRead1 - startRead1) / 1000000.0;
            double t2 = (double) (endRead2 - startRead2) / 1000000.0;
        }
        catch (Exception ignore)
        {
//            jdkReadFails++;
        }
        
        return ((Map) (o));
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
        System.out.println("Write JSON");
        System.out.println("  json-io: " + (totalJsonWrite / 1000000.0) + " ms");
        System.out.println("  JDK binary: " + (totalJdkWrite / 1000000.0) + " ms");
        System.out.println("  GSON: " + (totalGsonWrite / 1000000.0) + " ms");
        System.out.println("Read JSON");
        System.out.println("  json-io: " + (totalJsonRead / 1000000.0) + " ms");
        System.out.println("  JDK binary: " + (totalJdkRead / 1000000.0) + " ms");
        System.out.println("  GSON: " + (totalGsonRead / 1000000.0) + " ms");
        System.out.println("Write Fails:");
        System.out.println("  json-io: " + jsonIoWriteFails + " / " + totalWrites);
        System.out.println("  JDK: " + jdkWriteFails + " / " + totalWrites);
        System.out.println("  GSON: " + gsonWriteFails + " / " + totalWrites);
        System.out.println("Read Fails");
        System.out.println("  json-io: " + jsonIoReadFails + " / " + totalReads);
        System.out.println("  JDK: " + jdkReadFails + " / " + totalReads);
        System.out.println("  GSON: " + gsonReadFails + " / " + totalReads);
    }

    public static int count(CharSequence self, CharSequence text)
    {
        int answer = 0;
        int idx = 0;

        while (true)
        {
            idx = self.toString().indexOf(text.toString(), idx);
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
    private static long totalJdkWrite;
    private static long totalJsonRead;
    private static long totalGsonRead;
    private static long totalJdkRead;
    private static long jsonIoWriteFails;
    private static long gsonWriteFails;
    private static long jdkWriteFails;
    private static long jsonIoReadFails;
    private static long gsonReadFails;
    private static long jdkReadFails;
    private static long totalReads;
    private static long totalWrites;
    private static boolean debug = false;
}
