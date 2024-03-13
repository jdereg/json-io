package com.cedarsoftware.io.writers;

import java.time.ZoneId;

import com.cedarsoftware.io.Writers;

public class ZoneIdWriter extends Writers.PrimitiveUtf8StringWriter {

    @Override
    public String extractString(Object o) {
        return ((ZoneId) o).getId();
    }
}