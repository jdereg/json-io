package com.cedarsoftware.util.io;

import java.util.HashMap;
import java.util.Map;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
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
