package com.cedarsoftware.util.io.writers;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.cedarsoftware.util.io.Writers;

public class InstantWriter extends Writers.TemporalWriter<Instant> {

    public InstantWriter(DateTimeFormatter formatter) {
        super(formatter);
    }

    public InstantWriter() {
        this(DateTimeFormatter.ISO_INSTANT);
    }
}
