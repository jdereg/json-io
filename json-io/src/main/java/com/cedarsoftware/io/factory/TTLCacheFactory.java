package com.cedarsoftware.io.factory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.TTLCache;

/**
 * Builds a {@link TTLCache}. json-io writes one as its settings and no entries (see
 * {@link com.cedarsoftware.io.writers.TTLCacheWriter}): it is built empty, configured as it was. A TTLCache written as
 * the map of its entries -- by json-io before 4.113.0, by TOON without type info, or plain JSON read as one -- has no
 * settings: it keeps those entries, which do not expire, and holds at most as many as it was given (at least
 * {@value LRUCacheFactory#MIN_CAPACITY}). Before, no TTLCache could be read back: no constructor json-io could call
 * ("TTL must be at least 1 millisecond"), and plain JSON read as one took its first value for the TTL.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class TTLCacheFactory implements ClassFactory {
    // ttlMillis/maxSize/cleanupIntervalMillis, as TTLCacheWriter writes it. Anything else under "config" is an entry.
    private static final Pattern CONFIG = Pattern.compile("(\\d+)/(-?\\d+)/(\\d+)");

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object config = jObj.get("config");
        Matcher m = config instanceof String ? CONFIG.matcher((String) config) : null;
        TTLCache<Object, Object> cache;
        if (m != null && m.matches()) {
            cache = new TTLCache<>(Long.parseLong(m.group(1)), Integer.parseInt(m.group(2)),
                    Long.parseLong(m.group(3)));
            jObj.remove("config");
        } else {
            cache = new TTLCache<>(Long.MAX_VALUE, Math.max(jObj.size(), LRUCacheFactory.MIN_CAPACITY));
        }
        jObj.setTarget(cache);
        return cache;
    }
}
