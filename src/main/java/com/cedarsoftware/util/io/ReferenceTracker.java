package com.cedarsoftware.util.io;

import java.util.HashMap;
import java.util.Map;

public class ReferenceTracker {

    private static ThreadLocal<ReferenceTracker> referenceTracker = ThreadLocal.withInitial(ReferenceTracker::new);

    private final Map<Long, JsonObject> references = new HashMap<>();

    public static ReferenceTracker instance() {
        return referenceTracker.get();
    }

    public JsonObject put(Long l, JsonObject o) {
        return this.references.put(l, o);
    }

    public void clear() {
        this.references.clear();
    }

    public JsonObject getRef(Long id) {
        return this.references.get(id);
    }

    public JsonObject getRefTarget(JsonObject jObj) {
        if (!jObj.isReference()) {
            return jObj;
        }

        Long id = jObj.getReferenceId();
        JsonObject target = references.get(id);
        if (target == null) {
            throw new IllegalStateException("The JSON input had an @ref to an object that does not exist.");
        }
        return getRefTarget(target);
    }
}
