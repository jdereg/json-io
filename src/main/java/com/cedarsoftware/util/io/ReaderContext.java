package com.cedarsoftware.util.io;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

// not hung up on this name, but I think this will apply to both serialization and deserialization, thus the conversion name
public class ReaderContext {

    private static ThreadLocal<ReaderContext> conversionContext = ThreadLocal.withInitial(ReaderContext::new);

    @Getter
    private final ReferenceTracker referenceTracker;

    @Getter
    @Setter
    private Resolver resolver;


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

        public JsonObject get(Long id) {
            JsonObject target = references.get(id);
            if (target == null) {
                throw new IllegalStateException("The JSON input had an @ref to an object that does not exist.");
            }
            return get(target);
        }
    }
}
