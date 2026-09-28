package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An ArrayBlockingQueue reads back with its items. It has no no-argument constructor (it needs a capacity), so the read
 * failed with "Unable to instantiate" on every JDK, where Jackson reads it. Its capacity is not written; the queue is
 * made as large as its items (at least 1), as Jackson does.
 */
class ArrayBlockingQueueRoundTripTest {

    public static class Item {
        public String id;
        public Item self;
    }

    public static class Holder {
        public ArrayBlockingQueue<Item> items;
        public BlockingQueue<String> declaredAsInterface;
    }

    @Test
    void itsItemsComeBackAndItHoldsThem() {
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(4, false, Arrays.asList("a", "b"));
        @SuppressWarnings("unchecked")
        ArrayBlockingQueue<Object> read = JsonIo.toJava(JsonIo.toJson(queue, null), null).asClass(ArrayBlockingQueue.class);
        assertThat(read).containsExactly("a", "b");
        assertThat(read.remainingCapacity()).isZero();

        ArrayBlockingQueue<?> empty = JsonIo.toJava(JsonIo.toJson(new ArrayBlockingQueue<>(5), null), null)
                .asClass(ArrayBlockingQueue.class);
        assertThat(empty).isEmpty();
        assertThat(empty.remainingCapacity()).isEqualTo(1);
    }

    @Test
    void inFieldsWithSharedAndCyclicItems() {
        Item item = new Item();
        item.id = "x";
        item.self = item;
        Holder holder = new Holder();
        holder.items = new ArrayBlockingQueue<>(3);
        holder.items.add(item);
        holder.items.add(item);
        holder.declaredAsInterface = new ArrayBlockingQueue<>(2, false, Arrays.asList("s"));

        String json = JsonIo.toJson(holder, new WriteOptionsBuilder().cycleSupport(true).build());
        Holder read = JsonIo.toJava(json, null).asClass(Holder.class);
        Item[] items = read.items.toArray(new Item[0]);
        assertThat(items).hasSize(2);
        assertThat(items[1]).isSameAs(items[0]);
        assertThat(items[0].self).isSameAs(items[0]);
        assertThat(read.declaredAsInterface).isInstanceOf(ArrayBlockingQueue.class).containsExactly("s");
    }
}
