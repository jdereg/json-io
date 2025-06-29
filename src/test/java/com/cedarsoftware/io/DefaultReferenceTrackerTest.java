package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JsonReader.DefaultReferenceTracker.
 */
class DefaultReferenceTrackerTest {

    @Test
    void sizeReturnsNumberOfTrackedReferences() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        JsonReader.DefaultReferenceTracker tracker = new JsonReader.DefaultReferenceTracker(readOptions);

        assertEquals(0, tracker.size());

        tracker.put(1L, new JsonObject());
        assertEquals(1, tracker.size());

        tracker.put(2L, new JsonObject());
        assertEquals(2, tracker.size());

        tracker.clear();
        assertEquals(0, tracker.size());
    }
}
