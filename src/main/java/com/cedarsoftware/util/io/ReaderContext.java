package com.cedarsoftware.util.io;

import lombok.Getter;

public class ReaderContext {

    private static ThreadLocal<ReaderContext> readerContext = ThreadLocal.withInitial(ReaderContext::new);

    @Getter
    private JsonReader reader;

    public static ReaderContext instance() {
        return readerContext.get();
    }


    /**
     * ThreadLocal Conversion Context that holds onto members that are valid during the serialization / deserialization
     */
    private ReaderContext() {
    }

    public void initialize(JsonReader reader) {
        this.reader = reader;
    }

    public ReferenceTracker getReferenceTracker() {
        return this.reader.getResolver().getReferences();
    }
}
