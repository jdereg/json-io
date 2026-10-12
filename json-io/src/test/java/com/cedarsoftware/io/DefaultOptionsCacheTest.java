package com.cedarsoftware.io;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DefaultOptionsCache}: the options for callers that pass none are built once, and again when a permanent
 * setting has been made since.
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
class DefaultOptionsCacheTest {

    @Test
    void builtOnceWhileNoPermanentSettingIsMade() {
        AtomicInteger version = new AtomicInteger(7);
        AtomicInteger builds = new AtomicInteger();
        DefaultOptionsCache<Object> cache = new DefaultOptionsCache<>(version::get, () -> {
            builds.incrementAndGet();
            return new Object();
        });

        Object first = cache.get();

        assertSame(first, cache.get());
        assertSame(first, cache.get());
        assertEquals(1, builds.get());
    }

    @Test
    void builtAgainAfterAPermanentSetting() {
        AtomicInteger version = new AtomicInteger();
        AtomicInteger builds = new AtomicInteger();
        DefaultOptionsCache<String> cache = new DefaultOptionsCache<>(version::get,
                () -> "built " + builds.incrementAndGet() + " at version " + version.get());

        assertEquals("built 1 at version 0", cache.get());
        version.incrementAndGet();
        assertEquals("built 2 at version 1", cache.get());
        assertEquals("built 2 at version 1", cache.get());
        version.addAndGet(5);
        assertEquals("built 3 at version 6", cache.get());
        assertEquals(3, builds.get());
    }

    /**
     * A setting made while the options are being built may have been missed by that build. The options are stamped
     * with the count read before building, so the next caller builds them again.
     */
    @Test
    void aSettingMadeWhileBuildingIsNotLost() {
        AtomicInteger version = new AtomicInteger();
        AtomicInteger builds = new AtomicInteger();
        DefaultOptionsCache<Object> cache = new DefaultOptionsCache<>(version::get, () -> {
            if (builds.incrementAndGet() == 1) {
                version.incrementAndGet();      // another thread makes a permanent setting during the first build
            }
            return new Object();
        });

        Object duringTheSetting = cache.get();
        Object afterIt = cache.get();

        assertNotSame(duringTheSetting, afterIt);
        assertSame(afterIt, cache.get());
        assertEquals(2, builds.get());
    }
}
