package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.reflect.factories.MethodInjectorFactory;
import com.cedarsoftware.util.reflect.filters.FieldFilter;
import com.cedarsoftware.util.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.util.reflect.filters.field.GroovyFieldFilter;
import com.cedarsoftware.util.reflect.filters.field.StaticFieldFilter;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
 *         limitations under the License.*
 */
public class ClassDescriptors {

    private final List<FieldFilter> fieldFilters;

    private final List<InjectorFactory> injectorFactories;

    private final Map<Class<?>, Map<String, Injector>> deepInjectors;

    private static final ClassDescriptors instance = new ClassDescriptors();

    private ClassDescriptors() {
        this.fieldFilters = new ArrayList<>();
        this.fieldFilters.add(new StaticFieldFilter());
        this.fieldFilters.add(new GroovyFieldFilter());
        this.fieldFilters.add(new EnumFieldFilter());

        this.injectorFactories = new ArrayList<>();
        this.injectorFactories.add(new MethodInjectorFactory());

        this.deepInjectors = new ConcurrentHashMap<>();
    }

    public static ClassDescriptors instance() {
        return instance;
    }

    public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {
        if (classToTraverse == null) {
            return Collections.emptyMap();
        }
        return this.deepInjectors.computeIfAbsent(classToTraverse, this::buildDeepInjectors);
    }


    private Map<String, Injector> buildDeepInjectors(Class<?> classToTraverse) {
        Map<String, Injector> injectorMap = new LinkedHashMap<>();
        Class<?> c = classToTraverse;

        while (c != null) {
            this.buildInjectors(c, injectorMap);
            c = c.getSuperclass();
        }

        return injectorMap;
    }

    public void clearDescriptorCache() {
        deepInjectors.clear();
    }


    private void buildInjectors(Class<?> c, Map<String, Injector> injectorMap) {
        final Map<String, Method> possibleInjectors = ReflectionUtils.buildInjectorMap(c);

        final Field[] declaredFields = c.getDeclaredFields();

        for (Field field : declaredFields) {

            boolean isKnownFilter = KnownFilteredFields.instance().isFieldFiltered(field);
            boolean isInjectionFiltered = KnownFilteredFields.instance().isInjectionFiltered(field);

            if (isKnownFilter || isInjectionFiltered || fieldFilters.stream().anyMatch(f -> f.filter(field))) {
                continue;
            }

            Optional<Injector> injector = this.injectorFactories.stream()
                    .map(factory -> {
                        try {
                            return factory.createInjector(field, possibleInjectors);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst();

            final String fieldName = field.getName();
            String key = injectorMap.containsKey(fieldName) ? c.getSimpleName() + '.' + fieldName : fieldName;

            injectorMap.put(key, injector.orElseGet(() -> {
                try {
                    return new Injector(field);
                } catch (Throwable t) {
                    return null;
                }
            }));
        }
    }
}
