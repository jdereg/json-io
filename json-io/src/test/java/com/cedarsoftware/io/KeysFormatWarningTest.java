package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-time warning that a map with keys the Converter can stringify is written with @keys/@items names the key's
 * class: an enum constant with a body by its enum. Its own class's simple name is empty, and the warning read
 * "(e.g., )".
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
class KeysFormatWarningTest {
    /** X has a body: its class is a subclass of the enum, with no simple name. */
    public enum Body {
        X {
            @Override
            int weight() {
                return 1;
            }
        },
        Y;

        int weight() {
            return 0;
        }
    }

    /** The warning a write of {@code key}'s map logs, its once-a-JVM flag cleared first and restored after. */
    private static String warningFor(Object key) throws Exception {
        Field flag = JsonWriter.class.getDeclaredField("stringifyMapKeysWarned");     // static: plain reflection
        flag.setAccessible(true);
        AtomicBoolean warned = (AtomicBoolean) flag.get(null);
        boolean before = warned.get();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger log = Logger.getLogger(JsonWriter.class.getName());
        log.addHandler(handler);
        try {
            warned.set(false);
            Map<Object, String> map = new LinkedHashMap<>();
            map.put(key, "v");
            JsonIo.toJson(map, null);
        } finally {
            log.removeHandler(handler);
            warned.set(before);
        }
        assertEquals(1, messages.size(), messages.toString());
        return messages.get(0);
    }

    @Test
    void theKeysClassIsNamed() throws Exception {
        assertTrue(warningFor(Body.X).contains("(e.g., Body)"));
        assertTrue(warningFor(Body.Y).contains("(e.g., Body)"));
        assertTrue(warningFor(42L).contains("(e.g., Long)"));
    }
}
