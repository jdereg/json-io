package com.cedarsoftware.io;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The options json-io uses for a caller that passes none: built once, and built again when a permanent setting
 * has been made since ({@code WriteOptionsBuilder.addPermanent...}, {@code ReadOptionsBuilder.addPermanent...}).
 *
 * <p>Through 4.113.0 these were built when the builder class was loaded -- before any permanent setting could have
 * been made -- and never again, so no permanent setting reached a call such as {@code JsonIo.toJson(obj, null)}.</p>
 *
 * <p>Each builder counts the permanent settings made on it. The options are stamped with the count read
 * <i>before</i> they were built: a setting made while they were being built leaves the stamp behind, and the next
 * caller builds them again. Nothing is locked; two callers may both build, and either result is right.</p>
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
final class DefaultOptionsCache<T> {
    private static final class Built<T> {
        final int version;
        final T options;

        Built(int version, T options) {
            this.version = version;
            this.options = options;
        }
    }

    private final IntSupplier permanentVersion;
    private final Supplier<T> builder;
    private volatile Built<T> built;

    /**
     * @param permanentVersion how many permanent settings have been made on the builder these options come from
     * @param builder          builds the options from the builder's permanent settings as they are now
     */
    DefaultOptionsCache(IntSupplier permanentVersion, Supplier<T> builder) {
        this.permanentVersion = permanentVersion;
        this.builder = builder;
    }

    T get() {
        Built<T> current = built;
        int version = permanentVersion.getAsInt();
        if (current == null || current.version != version) {
            current = new Built<>(version, builder.get());
            built = current;
        }
        return current.options;
    }
}
