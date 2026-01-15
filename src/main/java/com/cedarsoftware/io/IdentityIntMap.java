package com.cedarsoftware.io;

/**
 * A lightweight identity-based map from Object to int.
 * Uses open addressing with linear probing for minimal overhead.
 *
 * Key features:
 * - Uses object identity (==) not equals() for key comparison
 * - Primitive int values (no boxing)
 * - No Entry objects (parallel arrays)
 * - Single identityHashCode call per operation
 *
 * Optimized for the reference-tracking use case in JsonWriter where:
 * - Keys are arbitrary objects
 * - Values are small sequential integers (IDs)
 * - Most objects are only seen once (get returns default)
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         Copyright (c) Cedar Software LLC
 *         Licensed under the Apache License, Version 2.0
 */
final class IdentityIntMap {
    private static final int DEFAULT_CAPACITY = 256;
    private static final float LOAD_FACTOR = 0.5f;  // Keep load low for fast probing

    private Object[] keys;
    private int[] values;
    private int size;
    private int threshold;
    private int mask;

    // Sentinel value meaning "not found" - caller must know this
    public static final int NOT_FOUND = 0;

    IdentityIntMap() {
        this(DEFAULT_CAPACITY);
    }

    IdentityIntMap(int initialCapacity) {
        // Round up to power of 2
        int capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1;
        }
        keys = new Object[capacity];
        values = new int[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
    }

    /**
     * Get the value for a key, or NOT_FOUND (0) if not present.
     * Uses identity comparison (==).
     */
    int get(Object key) {
        final int hash = System.identityHashCode(key);
        int index = hash & mask;
        final Object[] k = keys;

        // Linear probe
        while (true) {
            Object existing = k[index];
            if (existing == null) {
                return NOT_FOUND;
            }
            if (existing == key) {  // Identity comparison
                return values[index];
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * Check if a key is present.
     * Uses identity comparison (==).
     */
    boolean containsKey(Object key) {
        final int hash = System.identityHashCode(key);
        int index = hash & mask;
        final Object[] k = keys;

        while (true) {
            Object existing = k[index];
            if (existing == null) {
                return false;
            }
            if (existing == key) {
                return true;
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * Put a key-value pair. Returns the previous value or NOT_FOUND.
     * Uses identity comparison (==).
     */
    int put(Object key, int value) {
        if (size >= threshold) {
            resize();
        }
        return putInternal(key, value);
    }

    private int putInternal(Object key, int value) {
        final int hash = System.identityHashCode(key);
        int index = hash & mask;
        final Object[] k = keys;

        while (true) {
            Object existing = k[index];
            if (existing == null) {
                k[index] = key;
                values[index] = value;
                size++;
                return NOT_FOUND;
            }
            if (existing == key) {
                int old = values[index];
                values[index] = value;
                return old;
            }
            index = (index + 1) & mask;
        }
    }

    private void resize() {
        final Object[] oldKeys = keys;
        final int[] oldValues = values;
        final int oldCapacity = oldKeys.length;

        final int newCapacity = oldCapacity << 1;
        keys = new Object[newCapacity];
        values = new int[newCapacity];
        mask = newCapacity - 1;
        threshold = (int) (newCapacity * LOAD_FACTOR);
        size = 0;

        for (int i = 0; i < oldCapacity; i++) {
            Object key = oldKeys[i];
            if (key != null) {
                putInternal(key, oldValues[i]);
            }
        }
    }

    void clear() {
        final Object[] k = keys;
        for (int i = 0; i < k.length; i++) {
            k[i] = null;
        }
        size = 0;
    }

    int size() {
        return size;
    }
}
