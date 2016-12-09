package com.cedarsoftware.util.io;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 *
 */
@State(Scope.Benchmark)
public class JmhSimpleJson {

    private String json;

    @Setup
    public void setup()
    {
        this.json = TestUtil.fetchResource("forwardRefNegId.json");
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testJsonToJava(Blackhole bh)
    {
        bh.consume(JsonReader.jsonToJava(json));
    }
}
