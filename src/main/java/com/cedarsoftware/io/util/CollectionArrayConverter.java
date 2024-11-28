package com.cedarsoftware.io.util;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import java.util.function.Supplier;

import com.cedarsoftware.util.convert.Converter;

public class CollectionArrayConverter {
    private static final Class<?>[] PRIMITIVE_ARRAY_TYPES = {
            byte[].class, short[].class, int[].class, long[].class,
            float[].class, double[].class, boolean[].class, char[].class
    };

    private final Set<Class<?>> supportedArrayTypes;

    public CollectionArrayConverter(Converter converter) {
        // Initialize supportedArrayTypes with primitive array types
        supportedArrayTypes = new LinkedHashSet<>();
        supportedArrayTypes.addAll(Arrays.asList(PRIMITIVE_ARRAY_TYPES));

        // Add array versions of all types supported by converter
        Map<Class<?>, Set<Class<?>>> conversions = converter.allSupportedConversions();
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : conversions.entrySet()) {
            Class<?> sourceType = entry.getKey();
            if (!sourceType.isPrimitive()) {  // Skip primitives as they're handled above
                supportedArrayTypes.add(Array.newInstance(sourceType, 0).getClass());
            }

            // Also add array types for all target types
            for (Class<?> targetType : entry.getValue()) {
                if (!targetType.isPrimitive()) {  // Skip primitives as they're handled above
                    supportedArrayTypes.add(Array.newInstance(targetType, 0).getClass());
                }
            }
        }

        // **Explicitly add Object[].class to support generic object arrays**
        supportedArrayTypes.add(Object[].class);
    }

    public void setupConversions(Converter convo) {
        // Setup Array to Collection conversions
        for (Class<?> arrayType : supportedArrayTypes) {
            // General Collection conversion
            convo.addConversion(arrayType, Collection.class, CollectionArrayConverter::arrayToCollection);

            // Specific Collection conversions in correct order
            convo.addConversion(arrayType, NavigableSet.class, CollectionArrayConverter::arrayToNavigableSet);
            convo.addConversion(arrayType, SortedSet.class, CollectionArrayConverter::arrayToSortedSet);
            convo.addConversion(arrayType, Set.class, CollectionArrayConverter::arrayToSet);
            convo.addConversion(arrayType, Deque.class, CollectionArrayConverter::arrayToDeque);
            convo.addConversion(arrayType, Queue.class, CollectionArrayConverter::arrayToQueue);
            convo.addConversion(arrayType, List.class, CollectionArrayConverter::arrayToList);
        }

        // Setup Collection to Array conversions
        for (Class<?> arrayType : supportedArrayTypes) {
            convo.addConversion(Collection.class, arrayType,
                    (from, targetClass) -> collectionToArray(from, arrayType, convo));
        }
    }

    /**
     * Generic helper method to convert an array to a specific collection type.
     *
     * @param from               The source array object.
     * @param converter          The Converter instance for handling conversions.
     * @param collectionSupplier A Supplier that provides an instance of the target collection.
     * @param <C>                The type of the target collection.
     * @return The populated collection.
     */
    private static <C extends Collection<Object>> C convertArray(
            Object from,
            Converter converter,
            Supplier<C> collectionSupplier) {
        int length = Array.getLength(from);
        C collection = collectionSupplier.get();
        for (int i = 0; i < length; i++) {
            Object element = Array.get(from, i);
            if (element != null && element.getClass().isArray()) {
                // Determine the appropriate conversion based on the target collection type
                if (collection instanceof NavigableSet) {
                    element = convertArray(element, converter, TreeSet::new);
                } else if (collection instanceof SortedSet) {
                    element = convertArray(element, converter, TreeSet::new);
                } else if (collection instanceof Set) {
                    element = convertArray(element, converter, LinkedHashSet::new);
                } else if (collection instanceof Deque) {
                    element = convertArray(element, converter, ArrayDeque::new);
                } else if (collection instanceof Queue) {
                    element = convertArray(element, converter, LinkedList::new);
                } else if (collection instanceof List) {
                    element = convertArray(element, converter, ArrayList::new);
                } else {
                    // Default to ArrayList if the collection type is unrecognized
                    element = convertArray(element, converter, ArrayList::new);
                }
            }
            collection.add(element);
        }
        return collection;
    }

    /**
     * Converts an array to a Collection (ArrayList).
     */
    private static Object arrayToCollection(Object from, Converter converter) {
        return convertArray(from, converter, ArrayList::new);
    }

    /**
     * Converts an array to a Set (LinkedHashSet).
     */
    private static Object arrayToSet(Object from, Converter converter) {
        return convertArray(from, converter, LinkedHashSet::new);
    }

    /**
     * Converts an array to a SortedSet (TreeSet).
     */
    private static Object arrayToSortedSet(Object from, Converter converter) {
        return convertArray(from, converter, TreeSet::new);
    }

    /**
     * Converts an array to a NavigableSet (TreeSet).
     */
    private static Object arrayToNavigableSet(Object from, Converter converter) {
        return convertArray(from, converter, TreeSet::new);
    }

    /**
     * Converts an array to a List (ArrayList).
     */
    private static Object arrayToList(Object from, Converter converter) {
        return convertArray(from, converter, ArrayList::new);
    }

    /**
     * Converts an array to a Queue (LinkedList).
     */
    private static Object arrayToQueue(Object from, Converter converter) {
        return convertArray(from, converter, LinkedList::new);
    }

    /**
     * Converts an array to a Deque (ArrayDeque).
     */
    private static Object arrayToDeque(Object from, Converter converter) {
        return convertArray(from, converter, ArrayDeque::new);
    }

    /**
     * Converts a Collection to an Array of the specified type.
     */
    private static Object collectionToArray(Object from, Class<?> targetClass, Converter converter) {
        Collection<?> col = (Collection<?>) from;
        Class<?> componentType = targetClass.getComponentType();
        Object array = Array.newInstance(componentType, col.size());

        if (componentType.isPrimitive()) {
            // Handle primitive arrays
            int i = 0;
            for (Object item : col) {
                Array.set(array, i++, converter.convert(item, componentType));
            }
        } else {
            // For object arrays, optimize if no conversion is needed
            int i = 0;
            for (Object item : col) {
                if (item == null || componentType.isAssignableFrom(item.getClass())) {
                    Array.set(array, i++, item);
                } else if (converter.isConversionSupportedFor(item.getClass(), componentType)) {
                    Array.set(array, i++, converter.convert(item, componentType));
                } else {
                    throw new IllegalArgumentException(
                            "Cannot convert " + item.getClass().getName() +
                                    " to " + componentType.getName());
                }
            }
        }

        return array;
    }
}