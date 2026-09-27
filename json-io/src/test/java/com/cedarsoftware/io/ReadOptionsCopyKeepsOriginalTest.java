package com.cedarsoftware.io;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Locale;

import com.cedarsoftware.util.convert.ConverterOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A builder copied from a ReadOptions ({@code new ReadOptionsBuilder(existing)}) must not change that ReadOptions. Its
 * conversion settings (zone, locale, charset, class loader, true/false characters, converter overrides) were the
 * original's own object, so setting one on the copy set it on the original -- on the default ReadOptions, for every
 * read in the JVM that passes null -- and adding a converter override to the copy threw
 * UnsupportedOperationException (the original's overrides are unmodifiable once built). The copy now shares them only
 * until it changes one.
 */
class ReadOptionsCopyKeepsOriginalTest {

    @Test
    void changingACopysConversionSettingsLeavesTheOriginalAlone() throws Exception {
        ReadOptions original = new ReadOptionsBuilder().build();
        ConverterOptions before = original.getConverterOptions();
        ZoneId zone = before.getZoneId();
        Locale locale = before.getLocale();
        ClassLoader loader = before.getClassLoader();
        Character trueChar = before.trueChar();
        Character falseChar = before.falseChar();
        ZoneId otherZone = ZoneId.of(zone.getId().equals("Asia/Tokyo") ? "Europe/Paris" : "Asia/Tokyo");
        Locale otherLocale = locale.equals(Locale.GERMANY) ? Locale.JAPAN : Locale.GERMANY;

        try (URLClassLoader otherLoader = new URLClassLoader(new URL[0])) {
            ReadOptions copy = new ReadOptionsBuilder(original)
                    .setZoneId(otherZone)
                    .setLocale(otherLocale)
                    .setCharset(StandardCharsets.UTF_16)
                    .classLoader(otherLoader)
                    .setTrueCharacter('Y')
                    .setFalseCharacter('N')
                    .build();

            ConverterOptions after = original.getConverterOptions();
            assertEquals(zone, after.getZoneId());
            assertEquals(locale, after.getLocale());
            assertEquals(StandardCharsets.UTF_8, after.getCharset());
            assertSame(loader, after.getClassLoader());
            assertEquals(trueChar, after.trueChar());
            assertEquals(falseChar, after.falseChar());

            ConverterOptions copied = copy.getConverterOptions();
            assertEquals(otherZone, copied.getZoneId());
            assertEquals(otherLocale, copied.getLocale());
            assertEquals(StandardCharsets.UTF_16, copied.getCharset());
            assertSame(otherLoader, copied.getClassLoader());
            assertEquals(Character.valueOf('Y'), copied.trueChar());
            assertEquals(Character.valueOf('N'), copied.falseChar());
        }
    }

    @Test
    void aCopyOfTheDefaultReadOptionsDoesNotChangeTheDefault() {
        ReadOptions defaults = ReadOptionsBuilder.getDefaultReadOptions();
        ZoneId zone = defaults.getConverterOptions().getZoneId();
        ZoneId otherZone = ZoneId.of(zone.getId().equals("Asia/Tokyo") ? "Europe/Paris" : "Asia/Tokyo");

        new ReadOptionsBuilder(defaults).setZoneId(otherZone);     // not even built
        assertEquals(zone, defaults.getConverterOptions().getZoneId());
        assertEquals(zone, ReadOptionsBuilder.getDefaultReadOptions().getConverterOptions().getZoneId());
    }

    @Test
    void aCopyCanAddConverterOverridesOfItsOwn() {
        ReadOptions original = new ReadOptionsBuilder()
                .addConverterOverride(String.class, Long.class, (from, converter) -> 7L)
                .build();
        ReadOptions copy = new ReadOptionsBuilder(original)
                .addConverterOverride(String.class, Integer.class, (from, converter) -> 9)
                .build();

        assertEquals(1, original.getConverterOptions().getConverterOverrides().size());
        assertEquals(2, copy.getConverterOptions().getConverterOverrides().size());
        assertEquals(7L, JsonIo.toJava("\"5\"", copy).asClass(Long.class), "the original's override carries over");
        assertEquals(9, JsonIo.toJava("\"5\"", copy).asClass(Integer.class));
        assertEquals(5, JsonIo.toJava("\"5\"", original).asClass(Integer.class), "the copy's override stays with it");
    }

    @Test
    void aCopyThatChangesNoConversionSettingSharesThemAndStillSeesThem() {
        ReadOptions original = new ReadOptionsBuilder()
                .setZoneId(ZoneId.of("Asia/Tokyo"))
                .addConverterOverride(String.class, Long.class, (from, converter) -> 7L)
                .build();
        ReadOptions copy = new ReadOptionsBuilder(original).maxDepth(50).build();
        assertSame(original.getConverterOptions(), copy.getConverterOptions(), "nothing to copy");
        assertEquals(7L, JsonIo.toJava("\"5\"", copy).asClass(Long.class));

        // building a copy of the copy, and changing it, still leaves both alone
        ReadOptions third = new ReadOptionsBuilder(copy).setZoneId(ZoneId.of("Europe/Paris")).build();
        assertNotEquals(original.getConverterOptions().getZoneId(), third.getConverterOptions().getZoneId());
        assertEquals(ZoneId.of("Asia/Tokyo"), copy.getConverterOptions().getZoneId());
        assertTrue(third.getConverterOptions().getConverterOverrides().size() == 1);
    }
}
