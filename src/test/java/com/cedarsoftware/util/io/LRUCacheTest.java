package com.cedarsoftware.util.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LRUCacheTest {

    private LRUCache<Integer, String> lruCache;

    @BeforeEach
    void setUp() {
        lruCache = new LRUCache<>(3);
    }

    @Test
    void shouldRetrieveExistingItem() {
        lruCache.put(1, "Item1");
        assertEquals("Item1", lruCache.get(1), "Retrieved item should match the stored item.");
    }

    @Test
    void shouldReturnNullForNonExistingItem() {
        assertNull(lruCache.get(99), "Retrieving a non-existing item should return null.");
    }

    @Test
    void shouldInsertNewItem() {
        lruCache.put(2, "Item2");
        assertEquals("Item2", lruCache.get(2), "The inserted item should be retrievable.");
    }

    @Test
    void shouldUpdateExistingItem() {
        lruCache.put(1, "Item1");
        lruCache.put(1, "NewItem1");
        assertEquals("NewItem1", lruCache.get(1), "The updated item should have the new value.");
    }

    @Test
    void shouldRemoveOldestItemWhenCapacityIsExceeded() {
        lruCache.put(1, "Item1");
        lruCache.put(2, "Item2");
        lruCache.put(3, "Item3");
        lruCache.put(4, "Item4"); // This should evict key 1
        assertNull(lruCache.get(1), "The oldest item should be evicted when capacity is exceeded.");
    }

    @Test
    void shouldUpdateRecentlyUsedStatusOnRetrieval() {
        lruCache.put(1, "Item1");
        lruCache.put(2, "Item2");
        lruCache.put(3, "Item3");
        lruCache.get(1); // This should make key 1 the most recently used
        lruCache.put(4, "Item4"); // This should evict key 2
        assertNull(lruCache.get(2), "Least recently used item should be evicted.");
        assertNotNull(lruCache.get(1), "Recently accessed item should not be evicted.");
    }

    @Test
    void shouldInsertItemsUpToCapacity() {
        lruCache.put(1, "Item1");
        lruCache.put(2, "Item2");
        lruCache.put(3, "Item3");
        assertNotNull(lruCache.get(1));
        assertNotNull(lruCache.get(2));
        assertNotNull(lruCache.get(3));
    }

    @Test
    void shouldInsertItemsBeyondCapacity() {
        lruCache.put(1, "Item1");
        lruCache.put(2, "Item2");
        lruCache.put(3, "Item3");
        lruCache.put(4, "Item4");
        assertNull(lruCache.get(1), "The first item should be evicted when a fourth is inserted.");
    }

    @Test
    void shouldAllowNullKeysAndValuesIfSupported() {
        lruCache.put(null, "NullKeyItem");
        assertEquals("NullKeyItem", lruCache.get(null), "Should retrieve item with null key.");
        lruCache.put(5, null);
        assertNull(lruCache.get(5), "Should allow null values.");
    }

    // Additional tests could include:
    // - Testing for concurrent modifications
    // - Ensuring the cache behaves correctly after many insertions and retrievals
    // - Verifying the internal structure of the cache (like the order of the doubly linked list)
}
