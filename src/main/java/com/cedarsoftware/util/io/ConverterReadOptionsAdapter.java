package com.cedarsoftware.util.io;

import com.cedarsoftware.util.convert.ConverterOptions;

import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Locale;

public class ConverterReadOptionsAdapter implements ConverterOptions {

    private ReadOptions readOptions;

    public ConverterReadOptionsAdapter(ReadOptions readOptions) {
        this.readOptions = readOptions;
    }

    public ZoneId getSourceZoneId() {
        return this.readOptions.getSourceZoneId();
    }

    public ZoneId getTargetZoneId() {
        return this.readOptions.getTargetZoneId();
    }

    public Locale getSourceLocale() {
        return this.readOptions.getSourceLocale();
    }

    public Locale getTargetLocale() {
        return this.readOptions.getTargetLocale();
    }

    public Charset getSourceCharset() {
        return this.readOptions.getSourceCharset();
    }

    public Charset getTargetCharset() {
        return this.readOptions.getTargetCharset();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCustomOption(String name) {
        return (T) this.readOptions.getCustomOption(name);
    }
}
