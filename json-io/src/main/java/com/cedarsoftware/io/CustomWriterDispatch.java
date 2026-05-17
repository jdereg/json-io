package com.cedarsoftware.io;

import java.io.Writer;
import java.lang.reflect.Method;

import com.cedarsoftware.util.ClassValueMap;

/**
 * Dispatch detection for {@link JsonClassWriter} implementations during the
 * 4.x transition between the legacy {@link Writer}-based API and the new
 * {@link JsonGenerator}-based API.
 *
 * <p>Each registered custom writer's class is inspected once (via reflection,
 * walking up the class hierarchy) to determine whether it overrides the new
 * {@link JsonGenerator}-taking forms of {@code write} and
 * {@code writePrimitiveForm}. The decision is cached in a {@link ClassValueMap}
 * keyed by the writer's runtime class so the dispatch cost on the hot path is
 * a single map lookup.
 *
 * <h3>Dispatch rule</h3>
 *
 * <p>For each of the two methods independently, the framework walks the writer
 * class's superclass chain (most-derived to least-derived, stopping above
 * {@link Object}) and finds the first class that declares either the new or
 * the deprecated form of that method. If that class declares the new form
 * (with or without also declaring the deprecated form), the framework
 * dispatches to the new method; if it declares only the deprecated form, the
 * framework dispatches to the deprecated method. If the class declares
 * <i>both</i> forms, the new form wins — a built-in writer that needs to
 * support {@code super.write*()} chaining from user subclasses written
 * against the deprecated API will deliberately override both: the new
 * method has the real logic, the deprecated method delegates to it via a
 * bridge generator. This preserves the subclass's view that calling
 * {@code super.write(o, output, context)} runs the built-in writer's
 * behaviour.
 *
 * <p>If neither method is declared anywhere in the hierarchy (impossible
 * unless someone subclasses {@link JsonClassWriter}'s default no-ops without
 * any override), the result is irrelevant — both methods are no-ops — and we
 * arbitrarily return the new-method dispatch decision.
 *
 * <h3>Why {@link ClassValueMap}?</h3>
 *
 * <p>{@link ClassValueMap} backs the lookup with the JVM's per-Class side
 * channel ({@code ClassValue}), so cache entries are unloaded with their
 * classes — no leak under custom classloaders. The {@code computeIfAbsent}
 * surface is friendlier than raw {@code ClassValue} (no anonymous subclass
 * for each cache).
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
final class CustomWriterDispatch {

    /**
     * Immutable per-class dispatch decision. {@code useNewWrite} controls how
     * {@link JsonClassWriter#write} is invoked; {@code useNewPrimitive}
     * controls {@link JsonClassWriter#writePrimitiveForm}. The two are
     * independent because a writer can legitimately migrate one method
     * without the other.
     */
    static final class Info {
        final boolean useNewWrite;
        final boolean useNewPrimitive;

        Info(boolean useNewWrite, boolean useNewPrimitive) {
            this.useNewWrite = useNewWrite;
            this.useNewPrimitive = useNewPrimitive;
        }
    }

    private static final ClassValueMap<Info> CACHE = new ClassValueMap<>();

    private CustomWriterDispatch() {
    }

    /**
     * Returns the cached dispatch info for the given writer's runtime class,
     * computing it on first lookup. Safe to call from any thread.
     */
    static Info forWriter(JsonClassWriter<?> writer) {
        return CACHE.computeIfAbsent(writer.getClass(), CustomWriterDispatch::detect);
    }

    /**
     * Inspects the class hierarchy to determine which form of each method
     * the writer has overridden. See class-level Javadoc for the rule.
     */
    private static Info detect(Class<?> writerClass) {
        return new Info(
                resolvesToNew(writerClass, "write",
                        new Class<?>[]{Object.class, boolean.class, JsonGenerator.class, WriterContext.class},
                        new Class<?>[]{Object.class, boolean.class, Writer.class, WriterContext.class}),
                resolvesToNew(writerClass, "writePrimitiveForm",
                        new Class<?>[]{Object.class, JsonGenerator.class, WriterContext.class},
                        new Class<?>[]{Object.class, Writer.class, WriterContext.class})
        );
    }

    /**
     * Walk the class hierarchy from most-derived to (excluding) Object, looking
     * for the first declaration of either the new or the deprecated form of
     * {@code methodName}. Return true if the first class to declare either
     * form declares the new form (or both); false if it declares only the
     * deprecated form. If no class in the hierarchy declares either form,
     * default to true (the new-method default no-op is identical to the
     * deprecated-method default no-op, so the choice is observationally
     * neutral).
     */
    private static boolean resolvesToNew(Class<?> writerClass,
                                         String methodName,
                                         Class<?>[] newSig,
                                         Class<?>[] oldSig) {
        Class<?> c = writerClass;
        while (c != null && c != Object.class) {
            boolean hasNew = declares(c, methodName, newSig);
            boolean hasOld = declares(c, methodName, oldSig);
            if (hasNew) {
                return true;  // class overrides new (with or without old); new wins
            }
            if (hasOld) {
                return false;  // class overrides only the deprecated form
            }
            c = c.getSuperclass();
        }
        return true;
    }

    private static boolean declares(Class<?> c, String name, Class<?>[] paramTypes) {
        try {
            Method m = c.getDeclaredMethod(name, paramTypes);
            // Exclude synthetic bridge methods only if they're the ONLY declaration.
            // For our purposes a bridge method does count as an override (the
            // erasure-based dispatch will route through it), so we accept any
            // declared method including synthetic bridges.
            return m != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
