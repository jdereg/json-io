package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code maxObjectGraphDepth} counts a graph's depth alike with and without {@code cycleSupport}: every object and
 * every container it is held in is a level. Without cycleSupport, JSON writes a list's (or map's) objects past the
 * path it counts (a short cut for speed), so a graph through lists counted half the depth it does with a trace: a
 * chain of six objects in lists was written under a limit of six that refuses it with cycleSupport.
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
class GraphDepthCountedAlikeTest {
    public static class Node {
        public Node next;
        public List<Node> kids = new ArrayList<>();
        public Node[] arr;
        public Map<String, Node> map;
        public Object any;
    }

    private enum Holder { FIELD, LIST, ARRAY, MAP, OBJECT_LIST, LIST_THEN_FIELDS }

    private static Node chain(int length, Holder holder) {
        Node head = new Node();
        Node cur = head;
        for (int i = 0; i < length; i++) {
            Node next = new Node();
            switch (holder) {
                case FIELD:
                    cur.next = next;
                    break;
                case LIST:
                    cur.kids.add(next);
                    break;
                case ARRAY:
                    cur.arr = new Node[] {next};
                    break;
                case MAP:
                    cur.map = new LinkedHashMap<>(Collections.singletonMap("k", next));
                    break;
                case LIST_THEN_FIELDS:
                    // in a list first, then each in a field: below the list, counted by the path again
                    if (i == 0) {
                        cur.kids.add(next);
                    } else {
                        cur.next = next;
                    }
                    break;
                default:
                    cur.any = new ArrayList<>(Collections.singletonList(next));
                    break;
            }
            cur = next;
        }
        return head;
    }

    /** The shortest chain a limit of 6 refuses. */
    private static int refusedAt(Holder holder, boolean cycleSupport) {
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(cycleSupport).maxObjectGraphDepth(6).build();
        for (int length = 1; length < 40; length++) {
            try {
                JsonIo.toJson(chain(length, holder), options);
            } catch (JsonIoException e) {
                return length;
            }
        }
        return -1;
    }

    @Test
    void withAndWithoutCycleSupport() {
        for (Holder holder : Holder.values()) {
            // An object and its container: two levels a link
            int expected = holder == Holder.FIELD ? 6 : holder == Holder.LIST_THEN_FIELDS ? 5 : 3;
            assertEquals(expected, refusedAt(holder, true), holder + " with cycleSupport");
            assertEquals(expected, refusedAt(holder, false), holder + " without cycleSupport");
        }
    }

    /** A writer that refused a graph writes the next from the top: the count starts again. */
    @Test
    void aWriterUsedAgainAfterARefusal() throws Exception {
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(false).maxObjectGraphDepth(6).build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(out, options);
        try {
            writer.write(chain(10, Holder.LIST));
        } catch (JsonIoException expected) {
            // refused, within the third list
        }
        writer.write(chain(2, Holder.LIST));
        writer.close();
    }

    /** The count follows the path: a wide list of short chains is as deep as one of them. */
    @Test
    void wideIsNotDeep() {
        Node root = new Node();
        for (int i = 0; i < 1000; i++) {
            root.kids.add(chain(1, Holder.LIST));
        }
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(false).maxObjectGraphDepth(6).build();
        JsonIo.toJson(root, options);
    }
}
