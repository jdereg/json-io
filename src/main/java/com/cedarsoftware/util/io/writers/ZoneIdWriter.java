package com.cedarsoftware.util.io.writers;

import java.time.ZoneId;

import com.cedarsoftware.util.io.Writers;

public class ZoneIdWriter extends Writers.PrimitiveUtf8StringWriter {

    @Override
    public String extractString(Object o) {
        return ((ZoneId) o).getId();
    }
}