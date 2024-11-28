package com.cedarsoftware.io.util;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import com.cedarsoftware.util.ConcurrentList;
import com.cedarsoftware.util.ConcurrentSet;
import com.cedarsoftware.util.convert.Converter;

public class CollectionArrayConverter {
    private final Set<Class<?>> supportedArrayTypes;
    private final CollectionMappings collectionMappings;
    private static final Class<?>[] PRIMITIVE_ARRAY_TYPES = {
            byte[].class, short[].class, int[].class, long[].class,
            float[].class, double[].class, boolean[].class, char[].class
    };

    private static class CollectionMappings {
        private final List<Map.Entry<Class<?>, Function<Integer, Collection<?>>>> mappings = new ArrayList<>();

        CollectionMappings() {
            // Order matters - most specific to most general - this is validated
            add(ConcurrentSkipListSet.class, size -> new ConcurrentSkipListSet<>());
            add(ConcurrentSet.class, size -> new ConcurrentSet<>());
            add(NavigableSet.class, size -> new TreeSet<>());
            add(SortedSet.class, size -> new TreeSet<>());
            add(Set.class, size -> new LinkedHashSet<>(Math.max(size, 16)));
            add(BlockingDeque.class, size -> new LinkedBlockingDeque<>(size));
            add(Deque.class, size -> new ArrayDeque<>(size));
            add(ConcurrentLinkedQueue.class, size -> new ConcurrentLinkedQueue<>());
            add(BlockingQueue.class, size -> new LinkedBlockingQueue<>(size));
            add(Queue.class, size -> new LinkedList<>());
            add(CopyOnWriteArrayList.class, size -> new CopyOnWriteArrayList<>());
            add(ConcurrentList.class, size -> new ConcurrentList<>(size));
            add(List.class, size -> new ArrayList<>(size));
            add(Collection.class, size -> new ArrayList<>(size));        }

        void add(Class<?> type, Function<Integer, Collection<?>> factory) {
            mappings.add(new AbstractMap.SimpleEntry<>(type, factory));
        }

        List<Map.Entry<Class<?>, Function<Integer, Collection<?>>>> getMappings() {
            return mappings;
        }

        Function<Integer, Collection<?>> getFactory(Class<?> collectionType) {
            for (Map.Entry<Class<?>, Function<Integer, Collection<?>>> entry : mappings) {
                if (entry.getKey().isAssignableFrom(collectionType)) {
                    return entry.getValue();
                }
            }
            return ArrayList::new;
        }
    }

    public CollectionArrayConverter(Converter converter) {
        this.supportedArrayTypes = initializeSupportedArrayTypes(converter);
        this.collectionMappings = new CollectionMappings();
        validateMappings();
    }

    /**
     * Validates the order of mappings to ensure that more specific interfaces are
     * checked before more general ones.
     */
    private void validateMappings() {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Map.Entry<Class<?>, Function<Integer, Collection<?>>> entry : collectionMappings.getMappings()) {
            interfaces.add(entry.getKey());
        }

        for (int i = 0; i < interfaces.size(); i++) {
            Class<?> current = interfaces.get(i);
            for (int j = i + 1; j < interfaces.size(); j++) {
                Class<?> next = interfaces.get(j);
                if (current != next && current.isAssignableFrom(next)) {  // Add current != next check
                    throw new IllegalStateException("Mapping order error: " + next.getName() + " should come before " + current.getName());
                }
            }
        }
    }
    
    private Set<Class<?>> initializeSupportedArrayTypes(Converter converter) {
        // Add primitive array types
        Set<Class<?>> types = new LinkedHashSet<>(Arrays.asList(PRIMITIVE_ARRAY_TYPES));

        // Add array types for converter-supported types
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : converter.allSupportedConversions().entrySet()) {
            Class<?> sourceType = entry.getKey();
            if (!sourceType.isPrimitive()) {
                types.add(Array.newInstance(sourceType, 0).getClass());
            }

            for (Class<?> targetType : entry.getValue()) {
                if (!targetType.isPrimitive()) {
                    types.add(Array.newInstance(targetType, 0).getClass());
                }
            }
        }

        types.add(Object[].class);
        return Collections.unmodifiableSet(types);
    }

    private Collection<?> convertArray(Object array, Class<?> targetType) {
        int length = Array.getLength(array);
        Collection<?> collection = collectionMappings.getFactory(targetType).apply(length);
        convertArraySequential(array, collection, length, targetType);  // Pass targetType
        return collection;
    }

    private void convertArraySequential(Object array, Collection<?> collection, int length, Class<?> targetType) {
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            if (element != null && element.getClass().isArray()) {
                element = convertArray(element, targetType);  // Use targetType instead of collection.getClass()
            }
            ((Collection<Object>)collection).add(element);
        }
    }
    
    public void setupConversions(Converter converter) {
        // Setup array to collection conversions
        for (Class<?> arrayType : supportedArrayTypes) {
            for (Map.Entry<Class<?>, Function<Integer, Collection<?>>> entry : collectionMappings.getMappings()) {
                Class<?> collectionType = entry.getKey();
                converter.addConversion(arrayType, collectionType,
                        (from, targetClass) -> convertArray(from, collectionType));
            }
        }

        // Setup collection to array conversions
        for (Class<?> arrayType : supportedArrayTypes) {
            converter.addConversion(Collection.class, arrayType,
                    (from, targetClass) -> optimizedCollectionToArray((Collection<?>) from, arrayType, converter));
        }
    }
    
    private Object optimizedCollectionToArray(Collection<?> collection, Class<?> arrayType, Converter converter) {
        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, collection.size());

        if (componentType.isPrimitive()) {
            convertToPrimitiveArray(collection, array, componentType, converter);
        } else {
            convertToObjectArray(collection, array, componentType, converter);
        }

        return array;
    }

    private void convertToPrimitiveArray(Collection<?> collection, Object array, Class<?> componentType, Converter converter) {
        int index = 0;
        for (Object item : collection) {
            Array.set(array, index++, converter.convert(item, componentType));
        }
    }

    private void convertToObjectArray(Collection<?> collection, Object array, Class<?> componentType, Converter converter) {
        int index = 0;
        for (Object item : collection) {
            if (item == null || componentType.isAssignableFrom(item.getClass())) {
                Array.set(array, index++, item);    // Performance optimization
            } else {
                Array.set(array, index++, converter.convert(item, componentType));
            }
        }
    }
}