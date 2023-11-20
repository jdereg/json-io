package com.cedarsoftware.util.io;

import lombok.Getter;

/**
 * This class will be removed when we clean up the Writers to use
 * the new WriterContext methods for writing instead of the ThreadLocal access
 * to the actual Writer and WriteOptions
 */
@Deprecated
public class TheadLocalWriterContext {

    private static ThreadLocal<TheadLocalWriterContext> writerContext = ThreadLocal.withInitial(TheadLocalWriterContext::new);

    @Deprecated
    public static TheadLocalWriterContext instance() {
        return writerContext.get();
    }

    /**
     * @return WriteOptions created by the WriteOptionsBuilder
     */
    @Getter
    private WriteOptions writeOptions;

    /**
     * @return The current JsonWriter
     */
    @Getter
    private JsonWriter writer;

    /**
     * ThreadLocal Conversion Context that holds onto members that are valid during the serialization / deserialization
     */
    private TheadLocalWriterContext() {
    }

    @Deprecated
    public void initialize(WriteOptions writeOptions, JsonWriter writer) {
        this.writer = writer;
        this.writeOptions = writeOptions;
    }
}
