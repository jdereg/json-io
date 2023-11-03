package com.cedarsoftware.util.io;

import lombok.Getter;

// not hung up on this name, but I think this will apply to both serialization and deserialization, thus the conversion name
public class WriterContext {

    private static ThreadLocal<WriterContext> writerContext = ThreadLocal.withInitial(WriterContext::new);

    public static WriterContext instance() {
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
    private WriterContext() {
    }

    public void initialize(WriteOptions writeOptions, JsonWriter writer) {
        this.writer = writer;
        this.writeOptions = writeOptions;
    }
}
