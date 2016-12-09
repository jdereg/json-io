package com.cedarsoftware.util.io;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@State(Scope.Benchmark)
public class JmhSimpleJson {

    static class SimpleClass {
        int a;
        long b;
        Date c;
        String text;
        double d;
        float f;
        SimpleClass inner;
    }

    private String json;

    @Setup
    public void setup()
    {
        SimpleClass inner = new SimpleClass();
        inner.a = 10;
        inner.b = 12;
        inner.c = new Date();
        inner.d = 14d;
        inner.f = 22f;
        inner.text = inner.toString();
        SimpleClass simpleClass = new SimpleClass();
        simpleClass.a = Integer.MAX_VALUE;
        simpleClass.b = Long.MIN_VALUE;
        simpleClass.c = new Date();
        simpleClass.d = 14d;
        simpleClass.f = 22f;
        simpleClass.inner = inner;
        simpleClass.text = "A long time ago";

        this.json = JsonWriter.objectToJson(simpleClass);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testJsonToJava(Blackhole bh)
    {
        bh.consume(JsonReader.jsonToJava(json));
    }
}
