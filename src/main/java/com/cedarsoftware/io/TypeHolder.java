package com.cedarsoftware.io;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * TypeHolder captures a generic Type (including parameterized types) at runtime.
 * It is typically used via anonymous subclassing to capture generic type information.
 * However, when you already have a Type (such as a raw Class or a fully parameterized type), you can
 * use the static {@code forType()} and {@code forClass()} methods to create a TypeHolder instance.
 *
 * <p>Example usage via anonymous subclassing:</p>
 * <pre>
 *     TypeHolder&lt;List&lt;Point&gt;&gt; holder = new TypeHolder&lt;List&lt;Point&gt;&gt;() {};
 *     Type captured = holder.getType();
 * </pre>
 *
 * <p>Example usage using the {@code of()} methods:</p>
 * <pre>
 *     // With a raw class:
 *     TypeHolder&lt;Point&gt; holder1 = TypeHolder.forClass(Point.class);
 *
 *     // With a parameterized type (if you already have one):
 *     Type type = new TypeReference&lt;List&lt;Point&gt;&gt;() {}.getType();
 *     TypeHolder&lt;List&lt;Point&gt;&gt; holder2 = TypeHolder.forType(type);
 * </pre>
 *
 * @param <T> the type that is being captured
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
public class TypeHolder<T> {
    private final Type type;

    /**
     * Default constructor that uses anonymous subclassing to capture the type parameter.
     */
    protected TypeHolder() {
        // The anonymous subclass's generic superclass is a ParameterizedType,
        // from which we can extract the actual type argument.
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) superClass;
            // We assume the type parameter T is the first argument.
            this.type = pt.getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("TypeHolder must be created with a type parameter.");
        }
    }

    /**
     * Constructor used to explicitly set the type.
     *
     * @param type the Type to be held; must not be null.
     */
    protected TypeHolder(Type type) {
        this.type = type;
    }

    /**
     * Returns the captured Type, which may be a raw Class, a ParameterizedType,
     * a GenericArrayType, or another Type.
     *
     * @return the captured Type
     */
    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return type == null ? "" : type.toString();
    }

    /**
     * Creates a TypeHolder instance that wraps the given Type.
     * This factory method is useful when you already have a Type and wish to use the generic API
     * without anonymous subclassing.
     *
     * <p>Example usage:</p>
     * <pre>
     *     // For a parameterized type:
     *     Type type = new TypeReference&lt;List&lt;Point&gt;&gt;() {}.getType();
     *     TypeHolder&lt;List&lt;Point&gt;&gt; holder = TypeHolder.forType(type);
     * </pre>
     *
     * @param type the Type to wrap in a TypeHolder
     * @param <T> the type parameter
     * @return a TypeHolder instance that returns the given type via {@link #getType()}
     */
    public static <T> TypeHolder<T> forType(Type type) {
        return new TypeHolder<T>(type) {};
    }

    /**
     * Creates a TypeHolder instance that wraps the given Class.
     * This overload is provided for convenience when you have a Class instance.
     *
     * <p>Example usage:</p>
     * <pre>
     *     TypeHolder&lt;Point&gt; holder = TypeHolder.forClass(Point.class);
     * </pre>
     *
     * @param clazz the Class to wrap in a TypeHolder
     * @param <T> the type parameter
     * @return a TypeHolder instance that returns the given Class as a Type via {@link #getType()}
     */
    public static <T> TypeHolder<T> forClass(Class<T> clazz) {
        return new TypeHolder<T>(clazz) {};
    }
}
