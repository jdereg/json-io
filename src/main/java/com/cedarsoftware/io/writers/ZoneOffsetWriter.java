package com.cedarsoftware.io.writers;

import java.time.ZoneOffset;

import com.cedarsoftware.io.Writers;

public class ZoneOffsetWriter extends Writers.PrimitiveUtf8StringWriter {

    @Override
    public String extractString(Object o) {
        return ((ZoneOffset) o).getId();
    }
}