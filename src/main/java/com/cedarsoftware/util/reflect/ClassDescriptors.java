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

import com.cedarsoftware.util.reflect.accessors.FieldAccessor;
import com.cedarsoftware.util.reflect.factories.BooleanAccessorFactory;
import com.cedarsoftware.util.reflect.factories.EnumNameAccessorFactory;
import com.cedarsoftware.util.reflect.factories.MappedMethodAccessorFactory;
import com.cedarsoftware.util.reflect.factories.MappedMethodInjectorFactory;
import com.cedarsoftware.util.reflect.filters.EnumFilter;
import com.cedarsoftware.util.reflect.filters.GroovyFilter;
import com.cedarsoftware.util.reflect.filters.StaticFilter;
import com.cedarsoftware.util.reflect.injectors.FieldInjector;
import lombok.Getter;

public class ClassDescriptors {

    private final List<FieldFilter> fieldFilters;

    private final List<AccessorFactory> accessorFactories;

    private final List<InjectorFactory> injectorFactories;

    private final Map<Class<?>, ClassDescriptor> descriptors;

    private static final ClassDescriptors instance = new ClassDescriptors();

    private ClassDescriptors() {
        this.fieldFilters = new ArrayList<>();
        this.fieldFilters.add(new StaticFilter());
        this.fieldFilters.add(new GroovyFilter());
        this.fieldFilters.add(new EnumFilter());

        this.accessorFactories = new ArrayList<>();
        this.accessorFactories.add(new MappedMethodAccessorFactory());
        this.accessorFactories.add(new BooleanAccessorFactory());
        this.accessorFactories.add(new EnumNameAccessorFactory());

        this.injectorFactories = new ArrayList<>();
        this.injectorFactories.add(new MappedMethodInjectorFactory());

        this.descriptors = new ConcurrentHashMap<>();
    }

    public static ClassDescriptors instance() {
        return instance;
    }

    public Map<String, Accessor> getDeepAccessorMap(Class<?> classToTraverse) {

        Map<String, Accessor> accessors = new LinkedHashMap<>();
        Class<?> c = classToTraverse;

        while (c != null) {
            for (Map.Entry<String, Accessor> entry : this.getClassDescriptor(c).getAccessors().entrySet()) {
                String key = accessors.containsKey(entry.getKey()) ? c.getSimpleName() + '.' + entry.getKey() : entry.getKey();
                accessors.put(key, entry.getValue());
            }

            c = c.getSuperclass();
        }

        return accessors;
    }

    public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {

        Map<String, Injector> injectors = new LinkedHashMap<>();
        Class<?> c = classToTraverse;

        while (c != null) {
            for (Map.Entry<String, Injector> entry : this.getClassDescriptor(c).getInjectors().entrySet()) {
                String key = injectors.containsKey(entry.getKey()) ? c.getSimpleName() + '.' + entry.getKey() : entry.getKey();
                injectors.put(key, entry.getValue());
            }

            c = c.getSuperclass();
        }

        return injectors;
    }

    public Collection<Accessor> getDeepAccessorsForClass(Class<?> c) {
        return getDeepAccessorMap(c).values();
    }

    public ClassDescriptor getClassDescriptor(Class<?> c) {
        return descriptors.computeIfAbsent(c, this::buildDescriptor);
    }

    public void clearDescriptorCache() {
        descriptors.clear();
    }

    private ClassDescriptor buildDescriptor(Class<?> c) {
        final Map<String, Method> possibleAccessors = ReflectionUtils.buildAccessorMap(c);
        final Map<String, Method> possibleInjectors = ReflectionUtils.buildInjectorMap(c);
        final ClassDescriptorImpl descriptor = new ClassDescriptorImpl();
        final Field[] declaredFields = c.getDeclaredFields();

        for (Field field : declaredFields) {

            boolean isKnownFilter = KnownFilteredFields.instance().isFieldFiltered(field);

            if (isKnownFilter || fieldFilters.stream().anyMatch(f -> f.filter(field))) {
                continue;
            }

            Optional<Accessor> accessor = this.accessorFactories.stream()
                    .map(factory -> factory.createAccessor(field, possibleAccessors))
                    .filter(Objects::nonNull)
                    .findFirst();

            //  If no accessor was found, let's use the default tried and true field accessor.
            descriptor.addAccessor(field.getName(), accessor.orElseGet(() -> new FieldAccessor(field)));


            boolean isInjectionFiltered = KnownFilteredFields.instance().isInjectionFiltered(field);

            if (isInjectionFiltered) {
                continue;
            }

            Optional<Injector> injector = this.injectorFactories.stream()
                    .map(factory -> factory.createInjector(field, possibleInjectors))
                    .filter(Objects::nonNull)
                    .findFirst();

            descriptor.addInjector(field.getName(), injector.orElseGet(() -> new FieldInjector(field)));
        }

        return descriptor;
    }

    public boolean addFilter(FieldFilter filter) {
        clearDescriptorCache();
        return this.fieldFilters.add(filter);
    }

    public boolean removeFilter(FieldFilter filter) {
        clearDescriptorCache();
        return this.fieldFilters.remove(filter);
    }

    public class ClassDescriptorImpl implements ClassDescriptor {

        @Getter
        private final Map<String, Accessor> accessors;
        @Getter
        private final Map<String, Injector> injectors;

        public ClassDescriptorImpl() {
            this.accessors = new LinkedHashMap<>();
            this.injectors = new LinkedHashMap<>();
        }

        public void addAccessor(String name, Accessor accessor) {
            this.accessors.put(name, accessor);
        }

        public void addInjector(String name, Injector injector) {
            this.injectors.put(name, injector);
        }
    }
}
