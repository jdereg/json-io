package com.cedarsoftware.util.io;

import java.util.HashMap;
import java.util.Map;

public class ReferenceTracker {

    private static final ThreadLocal<ReferenceTracker> referenceTracker = ThreadLocal.withInitial(ReferenceTracker::new);

    private final Map<Long, JsonObject> references = new HashMap<>();

    public static ReferenceTracker instance() {
        return referenceTracker.get();
    }

    public JsonObject put(Long id, JsonObject target) {
        return this.references.put(id, target);
    }

    public void clear() {
        references.clear();
    }

    public JsonObject getRef(Long id)
    {
        return getRefTarget(references.get(id));
    }

    public JsonObject getRefTarget(JsonObject jObj)
    {
        while (jObj != null && jObj.isReference())
        {
            jObj = references.get(jObj.getReferenceId());
        }
        if (jObj == null)
        {
            throw new IllegalStateException("The JSON input had an @ref to an object that does not exist.");
        }
        return jObj;
    }

}
