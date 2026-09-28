package com.cedarsoftware.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A permanent (JVM lifetime) setting of the options builders -- one of their {@code BASE_*} tables -- and the
 * read-only snapshot of it that every builder starts from. A builder shares the snapshot until it changes that setting,
 * and copies it then, so building options copies none of the settings the builder leaves alone. (Every builder copied
 * all of them: about 9 KB of a ReadOptions' 11.9 KB, and 7.7 KB of a WriteOptions' 9.2 KB.)
 * <p>
 * The snapshot is made on first use and dropped by a change, both under this object's lock, so a builder never starts
 * from a snapshot older than a change that finished before it began. It is deep -- the sets and maps a table holds per
 * class are copied too, and read-only -- so a later permanent change reaches the builders created after it, and never
 * one already started or the options it built.
 *
 * @param <T> the table: a Map or a Set
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
final class PermanentSetting<T> {
    private final T permanent;
    private final UnaryOperator<T> snapshotOf;
    private volatile T snapshot;

    PermanentSetting(T permanent, UnaryOperator<T> snapshotOf) {
        this.permanent = permanent;
        this.snapshotOf = snapshotOf;
    }

    /** The read-only snapshot builders start from, made on first use after a change. */
    T snapshot() {
        T s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = snapshotOf.apply(permanent);
                    snapshot = s;
                }
            }
        }
        return s;
    }

    /** Changes the permanent setting; builders created from now on start from it. */
    void change(Consumer<? super T> change) {
        synchronized (this) {
            change.accept(permanent);
            snapshot = null;
        }
    }

    static <K, V> UnaryOperator<Map<K, V>> mapSnapshot() {
        return map -> Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    static <E> UnaryOperator<Set<E>> setSnapshot() {
        return set -> Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    /** A map of per-class field-name sets, the sets copied too. */
    static <K> UnaryOperator<Map<K, Set<String>>> mapOfSetsSnapshot() {
        return map -> {
            Map<K, Set<String>> copy = new LinkedHashMap<>(map.size() * 4 / 3 + 1);
            map.forEach((k, set) -> copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(set))));
            return Collections.unmodifiableMap(copy);
        };
    }

    /** A map of per-class name maps, the inner maps copied too. */
    static <K> UnaryOperator<Map<K, Map<String, String>>> mapOfMapsSnapshot() {
        return map -> {
            Map<K, Map<String, String>> copy = new LinkedHashMap<>(map.size() * 4 / 3 + 1);
            map.forEach((k, inner) -> copy.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(inner))));
            return Collections.unmodifiableMap(copy);
        };
    }
}
