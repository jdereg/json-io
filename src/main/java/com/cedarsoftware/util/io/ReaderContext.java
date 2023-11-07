package com.cedarsoftware.util.io;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class ReaderContext {

    private static ThreadLocal<ReaderContext> conversionContext = ThreadLocal.withInitial(ReaderContext::new);

    @Getter
    private final ReferenceTracker referenceTracker;

    @Getter
    private JsonReader reader;

    @Getter
    private ReadOptions readOptions;


    public static ReaderContext instance() {
        return conversionContext.get();
    }


    /**
     * ThreadLocal Conversion Context that holds onto members that are valid during the serialization / deserialization
     */
    private ReaderContext() {
        this.referenceTracker = new ReferenceTrackerImpl();
    }

    public void clearAll() {
        this.referenceTracker.clear();
    }

    public void initialize(ReadOptions readOptions, JsonReader reader) {
        this.referenceTracker.clear();
        this.reader = reader;
        this.readOptions = readOptions;
    }

    /**
     * Implementation of ReferenceTracker
     */
    private class ReferenceTrackerImpl implements ReferenceTracker {

        final Map<Long, JsonObject> references = new HashMap<>();

        public JsonObject put(Long l, JsonObject o) {
            return this.references.put(l, o);
        }

        public void clear() {
            this.references.clear();
        }

        public int size() {
            return this.references.size();
        }

        public JsonObject get(JsonObject jObj) {
            if (!jObj.isReference()) {
                return jObj;
            }

            return get(jObj.getReferenceId());
        }

        public JsonObject get(Long id)
        {
            JsonObject target = references.get(id);
            if (target == null)
            {
                throw new JsonIoException("Forward reference @ref: " + id + ", but no object defined (@id) with that value");
            }

            while (target.isReference())
            {
                id = target.getReferenceId();
                target = references.get(id);
                if (target == null)
                {
                    throw new JsonIoException("Forward reference @ref: " + id + ", but no object defined (@id) with that value");
                }
            }

            return target;
        }
    }

    public Resolver getResolver() {
        return this.reader.getResolver();
    }
}
