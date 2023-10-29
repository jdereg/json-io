package com.cedarsoftware.util.io;

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

    public static String toJson(Object obj, Map<String, Object> args)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JsonWriter jsonWriter = new JsonWriter(bout, args);
        long startWrite1 = System.nanoTime();
        jsonWriter.write(obj);
        jsonWriter.flush();
        jsonWriter.close();
        long endWrite1 = System.nanoTime();
        String json = bout.toString(StandardCharsets.UTF_8);

        try
        {
            bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            long startWrite2 = System.nanoTime();
            out.writeObject(obj);
            out.flush();
            out.close();
            long endWrite2 = System.nanoTime();

            totalJsonWrite += endWrite1 - startWrite1;
            totalObjWrite += endWrite2 - startWrite2;
            double t1 = (double) (endWrite1 - startWrite1) / 1000000.0;
            double t2 = (double) (endWrite2 - startWrite2) / 1000000.0;
            if (debug)
            {
                System.out.println("JSON write time = " + t1 + " ms");
                System.out.println("ObjectOutputStream time = " + t2 + " ms");
            }
        }
        catch (Exception e)
        {
            outputStreamFailCount = outputStreamFailCount++;
        }

        printLine(json);
        return json;
    }

    public static <T> T toJava(String json)
    {
        return toJava(json, new LinkedHashMap<>());
    }

    public static <T> T toJava(String json, Map<String, Object> args)
    {
        if (null == json || "".equals(json.trim()))
        {
            return null;
        }

        long startRead1 = System.nanoTime();

        ByteArrayInputStream ba;
        ba = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

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

            totalJsonRead += endRead1 - startRead1;
            totalObjRead += endRead2 - startRead2;
            double t1 = (double) (endRead1 - startRead1) / 1000000.0;
            double t2 = (double) (endRead2 - startRead2) / 1000000.0;
            if (debug)
            {
                System.out.println("JSON  read time  = " + t1 + " ms");
                System.out.println("ObjectInputStream time = " + t2 + " ms");
            }
        }
        catch (Exception e)
        {
            outputStreamFailCount = outputStreamFailCount++;
        }

        return ((T) (o));
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

        args.put((JsonReader.USE_MAPS), true);
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

            totalJsonRead += endRead1 - startRead1;
            totalObjRead += endRead2 - startRead2;
            double t1 = (double) (endRead1 - startRead1) / 1000000.0;
            double t2 = (double) (endRead2 - startRead2) / 1000000.0;
            if (debug)
            {
                System.out.println("JSON  read time  = " + t1 + " ms");
                System.out.println("ObjectInputStream time = " + t2 + " ms");
            }
        }
        catch (Exception ignore)
        {
            outputStreamFailCount = outputStreamFailCount++;
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

    public static void getTimings()
    {
        System.out.println("Total json-io read  = " + (totalJsonRead / 1000000.0) + " ms");
        System.out.println("Total json-io write = " + (totalJsonWrite / 1000000.0) + " ms");
        System.out.println("Total ObjectStream read  = " + (totalObjRead / 1000000.0) + " ms");
        System.out.println("Total ObjectStream write = " + (totalObjWrite / 1000000.0) + " ms");
        System.out.println("JDK InputStream/OutputStream fail count = " + outputStreamFailCount);
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
    private static long totalObjWrite;
    private static long totalJsonRead;
    private static long totalObjRead;
    private static long outputStreamFailCount;
    private static boolean debug = false;
}
