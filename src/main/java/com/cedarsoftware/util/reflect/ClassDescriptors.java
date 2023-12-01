package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.reflect.factories.BooleanAccessorFactory;
import com.cedarsoftware.util.reflect.factories.EnumNameAccessorFactory;
import com.cedarsoftware.util.reflect.factories.MappedMethodAccessorFactory;
import com.cedarsoftware.util.reflect.factories.MappedMethodInjectorFactory;
import com.cedarsoftware.util.reflect.factories.ZoneRegionAccessorFactory;
import com.cedarsoftware.util.reflect.filters.EnumFilter;
import com.cedarsoftware.util.reflect.filters.GroovyFilter;
import com.cedarsoftware.util.reflect.filters.StaticFilter;

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

    private final List<AccessorFactory> accessorFactories;

    private final List<InjectorFactory> injectorFactories;

    private final Map<Class<?>, Map<String, Accessor>> deepAccessors;
    private final Map<Class<?>, Map<String, Injector>> deepInjectors;

    private static final ClassDescriptors instance = new ClassDescriptors();

    private ClassDescriptors() {
        this.fieldFilters = new ArrayList<>();
        this.fieldFilters.add(new StaticFilter());
        this.fieldFilters.add(new GroovyFilter());
        this.fieldFilters.add(new EnumFilter());

        this.accessorFactories = new ArrayList<>();
        this.accessorFactories.add(new ZoneRegionAccessorFactory());
        this.accessorFactories.add(new MappedMethodAccessorFactory());
        this.accessorFactories.add(new BooleanAccessorFactory());
        this.accessorFactories.add(new EnumNameAccessorFactory());

        this.injectorFactories = new ArrayList<>();
        this.injectorFactories.add(new MappedMethodInjectorFactory());

        this.deepAccessors = new ConcurrentHashMap<>();
        this.deepInjectors = new ConcurrentHashMap<>();
    }

    public static ClassDescriptors instance() {
        return instance;
    }

    public Map<String, Accessor> getDeepAccessorMap(Class<?> classToTraverse) {
        return this.deepAccessors.computeIfAbsent(classToTraverse, this::buildDeepAccessors);
    }

    public Collection<Accessor> getDeepAccessors(Class<?> c) {
        return this.getDeepAccessorMap(c).values();
    }

    public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {
        return this.deepInjectors.computeIfAbsent(classToTraverse, this::buildDeepInjectors);
    }

    private Map<String, Accessor> buildDeepAccessors(Class<?> c) {
        final Map<String, Field> deepDeclaredFields = MetaUtils.getDeepDeclaredFields(c);
        final Map<String, Method> possibleMethods = ReflectionUtils.buildDeepAccessorMethods(c);

        return this.buildAccessors(deepDeclaredFields, possibleMethods);
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
        deepAccessors.clear();
    }

    private Map<String, Accessor> buildAccessors(Map<String, Field> deepDeclaredFields, Map<String, Method> possibleMethods) {
        Map<String, Accessor> accessorMap = new LinkedHashMap<>();

        for (Map.Entry<String, Field> entry : deepDeclaredFields.entrySet()) {

            final Field field = entry.getValue();
            boolean isKnownFilter = KnownFilteredFields.instance().isFieldFiltered(field);

            if (isKnownFilter || fieldFilters.stream().anyMatch(f -> f.filter(field))) {
                continue;
            }

            Optional<Accessor> accessor = this.accessorFactories.stream()
                    .map(factory -> {
                        try {
                            return factory.createAccessor(field, possibleMethods);
                        } catch (Throwable t) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst();


            String fieldName = entry.getKey();
            String key = accessorMap.containsKey(fieldName) ? field.getDeclaringClass().getSimpleName() + '.' + fieldName : fieldName;

            //  If no accessor was found, let's use the default tried and true field accessor.
            accessorMap.put(key, accessor.orElseGet(() -> {
                try {
                    return new Accessor(field);
                } catch (Throwable t) {
                    return null;
                }
            }));
        }

        return accessorMap;
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

    public boolean addFilter(FieldFilter filter) {
        clearDescriptorCache();
        return this.fieldFilters.add(filter);
    }

    public boolean removeFilter(FieldFilter filter) {
        clearDescriptorCache();
        return this.fieldFilters.remove(filter);
    }
}
