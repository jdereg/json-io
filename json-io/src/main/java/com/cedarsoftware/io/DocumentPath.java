package com.cedarsoftware.io;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where in a document a part stands, as a JSONPath ({@code $.lines[1].qty}): found from the document's root when a read
 * of that part fails, and only then. A field or a map's key is {@code .name}, or {@code ["name"]} where it is not a
 * plain name; an element is {@code [i]}; a map written as {@code @keys}/{@code @items} is reached through those.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
 *         limitations under the License.
 */
final class DocumentPath {
    private static final String ITEMS = "@items";
    private static final String KEYS = "@keys";

    private DocumentPath() {
    }

    /**
     * The path to {@code steps} within {@code failing}, from {@code root}; null where {@code failing} is not found. A
     * map's keys or values read as an array for their declared type hold the map's own array: an index into its values
     * is then the map's key at that index.
     */
    static String of(JsonObject root, JsonObject failing, List<Object> steps) {
        Object[] failingItems = failing.getItems();
        Object failingTarget = failing.getTarget();
        Map<Object, Object[]> cameFrom = new IdentityHashMap<>();   // a node: {the node it was reached from, the step}
        Deque<Object> todo = new ArrayDeque<>();
        cameFrom.put(root, null);
        todo.add(root);
        while (!todo.isEmpty()) {
            Object node = todo.poll();
            if (node == failing || failingItems != null && node == failingItems
                    || failingTarget != null && node == failingTarget) {    // a slot that holds what it is being read into
                return render(trail(cameFrom, node), steps);
            }
            if (node instanceof JsonObject) {
                JsonObject obj = (JsonObject) node;
                if (failingItems != null && obj.getKeys() == null && obj.fastEntryCount() > 0) {
                    Map.Entry<Object[], Object[]> lite = obj.asTwoArrays();
                    if (lite.getValue() == failingItems) {      // the map's values, read as an array
                        return render(trail(cameFrom, node), keyed(steps, lite.getKey()));
                    }
                }
                Object[] keys = obj.getKeys();
                Object[] items = obj.getItems();
                if (keys == null) {     // a map written as @keys/@items is reached through them
                    for (int i = 0, len = obj.fastEntryCount(); i < len; i++) {
                        visit(cameFrom, todo, failingTarget, obj.fastValueAt(i), node, obj.fastKeyAt(i));
                    }
                }
                if (keys != null) {
                    visit(cameFrom, todo, failingTarget, keys, node, KEYS);
                    visit(cameFrom, todo, failingTarget, items, node, ITEMS);
                } else if (items != null) {
                    visitElements(cameFrom, todo, failingTarget, items, node);
                } else if (failingTarget != null && obj.getTarget() != null && obj.getTarget() != failingTarget) {
                    // An array read and released: its elements are its target's
                    int at = indexOf(obj.getTarget(), failingTarget);
                    if (at >= 0) {
                        List<Object> trail = trail(cameFrom, node);
                        trail.add(at);
                        return render(trail, steps);
                    }
                }
            } else if (node instanceof Object[]) {
                visitElements(cameFrom, todo, failingTarget, (Object[]) node, node);
            } else if (node instanceof List) {
                List<?> list = (List<?>) node;
                for (int i = 0; i < list.size(); i++) {
                    visit(cameFrom, todo, failingTarget, list.get(i), node, i);
                }
            }
        }
        return null;
    }

    private static void visitElements(Map<Object, Object[]> cameFrom, Deque<Object> todo, Object sought, Object[] elements,
                                      Object from) {
        for (int i = 0; i < elements.length; i++) {
            visit(cameFrom, todo, sought, elements[i], from, i);
        }
    }

    /** {@code child} reached from {@code from} by {@code step}: queued where it is a container, or what is sought. */
    private static void visit(Map<Object, Object[]> cameFrom, Deque<Object> todo, Object sought, Object child,
                              Object from, Object step) {
        if ((child instanceof JsonObject || child instanceof Object[] || child instanceof List
                || child != null && child == sought) && !cameFrom.containsKey(child)) {
            cameFrom.put(child, new Object[] {from, step});
            todo.add(child);
        }
    }

    /** Where {@code target} stands in a Java array or collection, by identity; -1 where it does not. */
    private static int indexOf(Object container, Object target) {
        if (container instanceof Object[]) {
            Object[] array = (Object[]) container;
            for (int i = 0; i < array.length; i++) {
                if (array[i] == target) {
                    return i;
                }
            }
        } else if (container instanceof Collection) {
            int i = 0;
            for (Object element : (Collection<?>) container) {
                if (element == target) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    /** The steps from the root to {@code node}. */
    private static List<Object> trail(Map<Object, Object[]> cameFrom, Object node) {
        List<Object> steps = new ArrayList<>();
        for (Object[] link = cameFrom.get(node); link != null; link = cameFrom.get(link[0])) {
            steps.add(link[1]);
        }
        Collections.reverse(steps);
        return steps;
    }

    /** {@code steps} into a map's values read as an array: its first, an index, as the map's key there. */
    private static List<Object> keyed(List<Object> steps, Object[] keys) {
        List<Object> keyedSteps = new ArrayList<>(steps);
        if (!keyedSteps.isEmpty() && keyedSteps.get(0) instanceof Integer) {
            int at = (Integer) keyedSteps.get(0);
            if (at < keys.length && keys[at] != null) {
                keyedSteps.set(0, String.valueOf(keys[at]));
            }
        }
        return keyedSteps;
    }

    private static String render(List<Object> trail, List<Object> steps) {
        StringBuilder path = new StringBuilder("$");
        for (Object step : trail) {
            append(path, step);
        }
        for (Object step : steps) {
            append(path, step);
        }
        return path.toString();
    }

    private static void append(StringBuilder path, Object step) {
        if (step instanceof Integer) {
            path.append('[').append(step).append(']');
            return;
        }
        String name = String.valueOf(step);
        if (isPlainName(name)) {
            path.append('.').append(name);
        } else {
            path.append("[\"");
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '"' || c == '\\') {
                    path.append('\\');
                }
                path.append(c);
            }
            path.append("\"]");
        }
    }

    private static boolean isPlainName(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
