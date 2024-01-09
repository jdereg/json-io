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

    @Override
    public ZoneId getSourceZoneId() {
        return this.readOptions.getSourceZoneId();
    }

    @Override
    public ZoneId getTargetZoneId() {
        return this.readOptions.getTargetZoneId();
    }

    @Override
    public Locale getSourceLocale() {
        return this.readOptions.getSourceLocale();
    }

    @Override
    public Locale getTargetLocale() {
        return this.readOptions.getTargetLocale();
    }

    @Override
    public Charset getSourceCharset() {
        return this.readOptions.getSourceCharset();
    }

    @Override
    public Charset getTargetCharset() {
        return this.readOptions.getTargetCharset();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCustomOption(String name) {
        return (T) this.readOptions.getCustomOption(name);
    }
}
