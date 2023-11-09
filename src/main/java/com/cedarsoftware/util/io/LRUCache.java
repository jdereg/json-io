package com.cedarsoftware.util.io;
import java.util.HashMap;
import java.util.Map;

public class LRUCache<K, V> {
    private final Map<K, Node<K, V>> cacheMap;
    private final DoublyLinkedList<K, V> doublyLinkedList;
    private final int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cacheMap = new HashMap<>();
        this.doublyLinkedList = new DoublyLinkedList<>();
    }

    public V get(K key) {
        if (cacheMap.containsKey(key)) {
            Node<K, V> node = cacheMap.get(key);
            doublyLinkedList.moveToHead(node);
            return node.value;
        }
        return null;
    }

    public void put(K key, V value) {
        if (cacheMap.containsKey(key)) {
            Node<K, V> node = cacheMap.get(key);
            node.value = value;
            doublyLinkedList.moveToHead(node);
        } else {
            if (cacheMap.size() == capacity) {
                K removedKey = doublyLinkedList.removeTail();
                cacheMap.remove(removedKey);
            }
            Node<K, V> newNode = new Node<>(key, value);
            cacheMap.put(key, newNode);
            doublyLinkedList.addToHead(newNode);
        }
    }

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class DoublyLinkedList<K, V> {
        private final Node<K, V> head;
        private final Node<K, V> tail;

        DoublyLinkedList() {
            head = new Node<>(null, null);
            tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        void moveToHead(Node<K, V> node) {
            removeNode(node);
            addToHead(node);
        }

        void addToHead(Node<K, V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }

        void removeNode(Node<K, V> node) {
            Node<K, V> prevNode = node.prev;
            Node<K, V> nextNode = node.next;
            prevNode.next = nextNode;
            nextNode.prev = prevNode;
        }

        K removeTail() {
            if (tail.prev == head) {
                return null;
            }
            Node<K, V> tailItem = tail.prev;
            removeNode(tailItem);
            return tailItem.key;
        }
    }
}
