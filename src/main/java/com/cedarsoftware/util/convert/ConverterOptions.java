package com.cedarsoftware.util.convert;

import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Locale;

public interface ConverterOptions {


    // TODO:  I added these as source and target zones.  what do you think?
    // TODO:  During conversions you could be going from a standard source to a target in a different zone.
    // TODO:  To make this a standard library we would have to support both.

    /**
     * @return zoneId to use for source conversion when on is not provided on the source (Date, Instant, etc.)
     */
    ZoneId getSourceZoneId();

    /**
     * @return zoneId expected on the target when finished (only for types that support ZoneId or TimeZone)
     */
    ZoneId getTargetZoneId();

    /**
     * @return Locale to use as source locale when converting between types that require a Locale
     */
    Locale getSourceLocale();

    /**
     * @return Locale to use as target when converting between types that require a Locale.
     */
    Locale getTargetLocale();

    /**
     * @return Charset to use as source CharSet on types that require a Charset during conversion (if required).
     */
    Charset getSourceCharset();

    /**
     * @return Charset to use os target Charset on types that require a Charset during conversion (if required).
     */
    Charset getTargetCharset();

    /**
     * @return custom option
     */
    <T> T getCustomOption(String name);
}
