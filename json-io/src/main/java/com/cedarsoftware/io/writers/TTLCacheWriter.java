package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.TTLCache;

/**
 * Writes a {@link TTLCache} as its settings and no entries -- {@code "config":"ttlMillis/maxSize/cleanupIntervalMillis"}
 * -- as Guava and Caffeine serialize their caches. A cache is rebuilt, not restored: the reader cannot know how old
 * an entry is, so one read back could outlive its TTL. {@link com.cedarsoftware.io.factory.TTLCacheFactory} builds
 * it back empty, configured as it was; a cache that ends up in JSON by accident costs a few bytes, not its contents.
 * TOON without type info writes a map as its entries, this one included.
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
public class TTLCacheWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        gen.writeFieldName("config");
        gen.writeString(configOf((TTLCache<?, ?>) obj));
    }

    @Override
    public Object toData(Object obj) {
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("config", configOf((TTLCache<?, ?>) obj));
        return data;
    }

    private static String configOf(TTLCache<?, ?> cache) {
        return cache.getTtlMillis() + "/" + cache.getMaxSize() + "/" + cache.getCleanupIntervalMillis();
    }

    @Override
    public String getTypeName(Object o) {
        return "com.cedarsoftware.util.TTLCache";
    }
}
