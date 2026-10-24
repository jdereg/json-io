package com.cedarsoftware.io;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code @ref} to an id no {@code @id} in the document defines is the error that says so ("Forward reference
 * @ref: 9, but no object defined (@id) with that value") wherever it is: an {@code Optional}'s value and the root
 * threw {@code NullPointerException} ("Cannot read field "isFinished" because "rootObj" is null"). json-io 4.117.0
 * wrote such a reference for an {@code @IoValue} object held twice. A reference to an object the document defines
 * reads as that object, in an {@code Optional} too.
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
class DanglingReferenceTest {
    public static class Address {
        public String street;
    }

    public static class Holder {
        public Address addr;
        public Optional<Address> opt;
        public Object any;
        public List<Address> list;
        public Address[] arr;
    }

    private static void assertDangling(Executable read) {
        JsonIoException e = assertThrows(JsonIoException.class, read);
        assertTrue(e.getMessage().contains("Forward reference @ref: 9, but no object defined (@id) with that value"),
                e.getMessage());
    }

    @Test
    void aReferenceToNothingSaysSo() {
        String[] docs = {"{\"opt\":{\"@type\":\"Optional\",\"present\":true,\"value\":{\"@ref\":9}}}",
                "{\"any\":{\"@ref\":9}}", "{\"list\":[{\"@ref\":9}]}", "{\"addr\":{\"@ref\":9}}",
                "{\"arr\":[{\"@ref\":9}]}"};
        for (String doc : docs) {
            assertDangling(() -> JsonIo.toJava(doc, null).asClass(Holder.class));
        }
        assertDangling(() -> JsonIo.toJava("{\"@ref\":9}", null).asClass(null));
        assertDangling(() -> JsonIo.toJava("[{\"@ref\":9}]", null).asClass(null));
        assertDangling(() -> JsonIo.toMaps("{\"@ref\":9}", null).asClass(null));
    }

    /** One the document defines: the same object, in an Optional too, whichever comes first. */
    @Test
    void aReferenceToAnObjectReadsAsIt() {
        Holder h = JsonIo.toJava("{\"addr\":{\"@id\":1,\"street\":\"Main\"},"
                + "\"opt\":{\"@type\":\"Optional\",\"present\":true,\"value\":{\"@ref\":1}}}", null)
                .asClass(Holder.class);
        assertSame(h.addr, h.opt.get());
        h = JsonIo.toJava("{\"opt\":{\"@type\":\"Optional\",\"present\":true,"
                + "\"value\":{\"@id\":1,\"street\":\"Main\"}},\"addr\":{\"@ref\":1}}", null).asClass(Holder.class);
        assertSame(h.addr, h.opt.get());
    }
}
