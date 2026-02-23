package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.cedarsoftware.io.ClassFactory;

/**
 * Class-level annotation that specifies a {@link ClassFactory} implementation to use
 * when deserializing instances of this class.
 * <p>
 * This allows factory registration to be co-located with the class it serves, rather than
 * requiring programmatic registration via {@code ReadOptionsBuilder.addClassFactory()}.
 * <p>
 * <b>Priority:</b> Programmatic {@code ReadOptionsBuilder.addClassFactory()} registration
 * takes precedence over this annotation. The annotation serves as a default/fallback.
 * <p>
 * The specified factory class must have a no-arg constructor.
 *
 * <pre>{@code
 * @IoClassFactory(WidgetFactory.class)
 * public class Widget {
 *     private String name;
 *     private int size;
 *     // ...
 * }
 *
 * public class WidgetFactory implements ClassFactory {
 *     public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
 *         String name = (String) jObj.get("name");
 *         int size = ((Number) jObj.get("size")).intValue();
 *         return new Widget(name, size);
 *     }
 *
 *     public boolean isObjectFinal() {
 *         return true;
 *     }
 * }
 * }</pre>
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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoClassFactory {
    /**
     * The {@link ClassFactory} implementation class to use for deserializing
     * instances of this class. Must have a no-arg constructor.
     */
    Class<? extends ClassFactory> value();
}
