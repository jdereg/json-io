package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every ReadOptionsBuilder and WriteOptionsBuilder starts from the permanent aliases (700 of them). Each copied them;
 * they now share one unmodifiable snapshot and copy it only when a builder changes its aliases, which makes building
 * options several times cheaper. What each options instance sees is unchanged: its own changes stay its own, a
 * permanent alias reaches the builders created after it (not one already started), built options refuse changes, and
 * a copy starts from the original's aliases without changing them.
 */
class AliasesSharedUntilChangedTest {

    public static class AliasedOne {
    }

    public static class AliasedTwo {
    }

    private static final String ONE = AliasedOne.class.getName();
    private static final String TWO = AliasedTwo.class.getName();

    @Test
    void aBuildersAliasChangesAreItsOwn() {
        WriteOptions plain = new WriteOptionsBuilder().build();
        WriteOptions added = new WriteOptionsBuilder().aliasTypeName(AliasedOne.class, "One").build();
        WriteOptions removed = new WriteOptionsBuilder().removeAliasTypeNamesMatching("java.util.ArrayList").build();
        assertEquals("One", added.getTypeNameAlias(ONE));
        assertEquals(ONE, plain.getTypeNameAlias(ONE));
        assertEquals("java.util.ArrayList", removed.getTypeNameAlias("java.util.ArrayList"));
        assertEquals("ArrayList", plain.getTypeNameAlias("java.util.ArrayList"));
        assertEquals("ArrayList", added.getTypeNameAlias("java.util.ArrayList"));
        assertEquals("ArrayList", new WriteOptionsBuilder().build().getTypeNameAlias("java.util.ArrayList"));

        ReadOptions readPlain = new ReadOptionsBuilder().build();
        ReadOptions readAdded = new ReadOptionsBuilder().aliasTypeName(AliasedOne.class, "One").build();
        ReadOptions readRemoved = new ReadOptionsBuilder().removeAliasTypeNameMatching("java.util.ArrayList").build();
        assertEquals(ONE, readAdded.getTypeNameAlias("One"));
        assertEquals("One", readPlain.getTypeNameAlias("One"));
        assertEquals("ArrayList", readRemoved.getTypeNameAlias("ArrayList"));
        assertEquals("java.util.ArrayList", readPlain.getTypeNameAlias("ArrayList"));
        assertEquals("java.util.ArrayList", new ReadOptionsBuilder().build().getTypeNameAlias("ArrayList"));
    }

    @Test
    void aPermanentAliasReachesTheBuildersCreatedAfterIt() {
        WriteOptionsBuilder writeBefore = new WriteOptionsBuilder();
        ReadOptionsBuilder readBefore = new ReadOptionsBuilder();
        try {
            WriteOptionsBuilder.addPermanentAlias(AliasedTwo.class, "Two");
            ReadOptionsBuilder.addPermanentAlias(AliasedTwo.class, "Two");
            assertEquals(TWO, writeBefore.build().getTypeNameAlias(TWO), "a builder already started");
            assertEquals("Two", readBefore.build().getTypeNameAlias("Two"), "a builder already started");
            assertEquals("Two", new WriteOptionsBuilder().build().getTypeNameAlias(TWO));
            assertEquals(TWO, new ReadOptionsBuilder().build().getTypeNameAlias("Two"));
        } finally {
            WriteOptionsBuilder.removePermanentAliasTypeNamesMatching("*AliasedTwo");
            ReadOptionsBuilder.removePermanentAliasTypeNamesMatching("Two");
        }
        assertEquals(TWO, new WriteOptionsBuilder().build().getTypeNameAlias(TWO), "after it was removed");
        assertEquals("Two", new ReadOptionsBuilder().build().getTypeNameAlias("Two"), "after it was removed");
    }

    @Test
    void builtOptionsRefuseAliasChanges() {
        WriteOptionsBuilder write = new WriteOptionsBuilder();
        write.build();
        assertThrows(UnsupportedOperationException.class, () -> write.aliasTypeName(AliasedOne.class, "One"));
        ReadOptionsBuilder read = new ReadOptionsBuilder();
        read.build();
        assertThrows(UnsupportedOperationException.class, () -> read.aliasTypeName(AliasedOne.class, "One"));
    }

    @Test
    void aCopyStartsFromTheOriginalsAliasesAndChangesOnlyItsOwn() {
        WriteOptions original = new WriteOptionsBuilder().aliasTypeName(AliasedOne.class, "One").build();
        WriteOptions copy = new WriteOptionsBuilder(original).aliasTypeName(AliasedTwo.class, "Two").build();
        assertEquals("One", copy.getTypeNameAlias(ONE));
        assertEquals("Two", copy.getTypeNameAlias(TWO));
        assertEquals(TWO, original.getTypeNameAlias(TWO));
        assertEquals("One", new WriteOptionsBuilder(original).build().getTypeNameAlias(ONE));

        ReadOptions readOriginal = new ReadOptionsBuilder().aliasTypeName(AliasedOne.class, "One").build();
        ReadOptions readCopy = new ReadOptionsBuilder(readOriginal).aliasTypeName(AliasedTwo.class, "Two").build();
        assertEquals(ONE, readCopy.getTypeNameAlias("One"));
        assertEquals(TWO, readCopy.getTypeNameAlias("Two"));
        assertEquals("Two", readOriginal.getTypeNameAlias("Two"));
    }
}
