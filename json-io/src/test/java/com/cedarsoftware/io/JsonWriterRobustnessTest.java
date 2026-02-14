package com.cedarsoftware.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonWriterRobustnessTest {

    private static final class InspectableJsonWriter extends JsonWriter {
        InspectableJsonWriter(OutputStream out, WriteOptions writeOptions) {
            super(out, writeOptions);
        }

        int visitedSize() {
            return getObjVisited().size();
        }

        int referencedSize() {
            return getObjsReferenced().size();
        }
    }

    @Test
    void writeFailureClearsReferenceTrackingState() {
        OutputStream failingOutput = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("intentional failure");
            }
        };

        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        InspectableJsonWriter writer = new InspectableJsonWriter(failingOutput, writeOptions);

        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);

        assertThrows(JsonIoException.class, () -> writer.write(cyclic));
        assertEquals(0, writer.visitedSize());
        assertEquals(0, writer.referencedSize());
    }

    @Test
    void maxObjectCountAllowsExactlyConfiguredCount() {
        // Root list + 9 nested lists = 10 referenceable objects
        List<Object> root = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            root.add(new ArrayList<>());
        }

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectCount(10)
                .build();

        JsonIo.toJson(root, writeOptions);
    }
}
