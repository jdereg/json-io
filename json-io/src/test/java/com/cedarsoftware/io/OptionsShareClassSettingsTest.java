package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.writers.ZoneIdWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Builders share the permanent class settings -- custom readers and writers, class factories, coerced types,
 * non-referenceable and not-custom classes, excluded / not-imported / included fields, non-standard getters, field and
 * method filters, accessor factories -- as read-only snapshots, and copy one only when they change it. Each builder
 * copied every one: about 9 KB of a ReadOptions' 11.9 KB, and 7.7 KB of a WriteOptions' 9.2 KB (now about 1.7 KB and
 * 1.1 KB). What each options object sees is unchanged, as with the shared aliases: its changes stay its own, a
 * permanent change reaches the builders created after it, built options refuse changes, and a copy starts from the
 * original's settings without changing them.
 */
class OptionsShareClassSettingsTest {

    private static final List<String> READ_SETTINGS = Arrays.asList("coercedTypes", "notCustomReadClasses",
            "customReaderClasses", "classFactoryMap", "nonRefClasses", "excludedFieldNames", "fieldsNotImported");
    private static final List<String> WRITE_SETTINGS = Arrays.asList("includedFieldNames", "nonStandardGetters",
            "notCustomWrittenClasses", "nonRefClasses", "excludedFieldNames", "fieldFilters", "methodFilters",
            "accessorFactories", "customWrittenClasses");

    public static class Mine {
        public String name;
    }

    public static class PermanentlyNonRef {
    }

    public static class PermanentlyNonRefWrite {
    }

    private static Object setting(Object options, String name) throws Exception {
        Field field = options.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(options);
    }

    @Test
    void builtOptionsShareTheSettingsTheirBuildersLeftAlone() throws Exception {
        ReadOptions read1 = new ReadOptionsBuilder().build();
        ReadOptions read2 = new ReadOptionsBuilder().maxDepth(50).build();
        for (String name : READ_SETTINGS) {
            assertThat(setting(read2, name)).as(name).isSameAs(setting(read1, name));
        }
        WriteOptions write1 = new WriteOptionsBuilder().build();
        WriteOptions write2 = new WriteOptionsBuilder().prettyPrint(true).build();
        for (String name : WRITE_SETTINGS) {
            assertThat(setting(write2, name)).as(name).isSameAs(setting(write1, name));
        }

        // A change copies that setting only
        ReadOptions changed = new ReadOptionsBuilder().addNonReferenceableClass(Mine.class).build();
        assertThat(setting(changed, "nonRefClasses")).isNotSameAs(setting(read1, "nonRefClasses"));
        assertThat(setting(changed, "classFactoryMap")).isSameAs(setting(read1, "classFactoryMap"));
    }

    @Test
    void aBuildersChangesAreItsOwn() {
        ClassFactory factory = new ClassFactory() { };
        ReadOptions read = new ReadOptionsBuilder()
                .addClassFactory(Mine.class, factory)
                .addNonReferenceableClass(Mine.class)
                .addNotCustomReaderClass(Mine.class)
                .coerceClass(Mine.class.getName(), Object.class)
                .build();
        ReadOptions plainRead = new ReadOptionsBuilder().build();
        assertThat(read.getClassFactory(Mine.class)).isSameAs(factory);
        assertThat(read.isNonReferenceableClass(Mine.class)).isTrue();
        assertThat(read.isNotCustomReaderClass(Mine.class)).isTrue();
        assertThat(read.getCoercedClass(Mine.class)).isSameAs(Object.class);
        assertThat(plainRead.getClassFactory(Mine.class)).isNull();
        assertThat(plainRead.isNonReferenceableClass(Mine.class)).isFalse();
        assertThat(plainRead.isNotCustomReaderClass(Mine.class)).isFalse();
        assertThat(plainRead.isClassCoerced(Mine.class)).isFalse();

        WriteOptions write = new WriteOptionsBuilder()
                .addCustomWrittenClass(Mine.class, new ZoneIdWriter())
                .addNotCustomWrittenClass(Mine.class)
                .addNonReferenceableClass(Mine.class)
                .addExcludedField(Mine.class, "name")
                .build();
        WriteOptions plainWrite = new WriteOptionsBuilder().build();
        assertThat(write.isCustomWrittenClass(Mine.class)).isTrue();
        assertThat(write.isNotCustomWrittenClass(Mine.class)).isTrue();
        assertThat(write.isNonReferenceableClass(Mine.class)).isTrue();
        assertThat(write.getExcludedFields(Mine.class)).containsExactly("name");
        assertThat(plainWrite.isCustomWrittenClass(Mine.class)).isFalse();
        assertThat(plainWrite.isNotCustomWrittenClass(Mine.class)).isFalse();
        assertThat(plainWrite.isNonReferenceableClass(Mine.class)).isFalse();
        assertThat(plainWrite.getExcludedFields(Mine.class)).isEmpty();
    }

    @Test
    void aPermanentChangeReachesTheBuildersCreatedAfterIt() {
        ReadOptionsBuilder readBefore = new ReadOptionsBuilder();
        WriteOptionsBuilder writeBefore = new WriteOptionsBuilder();
        ReadOptions readBuiltBefore = new ReadOptionsBuilder().build();

        ReadOptionsBuilder.addPermanentNonReferenceableClass(PermanentlyNonRef.class);
        WriteOptionsBuilder.addPermanentNonRef(PermanentlyNonRefWrite.class);

        assertThat(readBefore.build().isNonReferenceableClass(PermanentlyNonRef.class)).isFalse();
        assertThat(readBuiltBefore.isNonReferenceableClass(PermanentlyNonRef.class)).isFalse();
        assertThat(writeBefore.build().isNonReferenceableClass(PermanentlyNonRefWrite.class)).isFalse();
        assertThat(new ReadOptionsBuilder().build().isNonReferenceableClass(PermanentlyNonRef.class)).isTrue();
        assertThat(new WriteOptionsBuilder().build().isNonReferenceableClass(PermanentlyNonRefWrite.class)).isTrue();
    }

    @Test
    void builtOptionsRefuseChangesThroughTheirBuilder() {
        ReadOptionsBuilder read = new ReadOptionsBuilder().addNonReferenceableClass(Mine.class);
        read.build();
        assertThatThrownBy(() -> read.addNonReferenceableClass(PermanentlyNonRef.class))   // a setting it copied
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> read.addClassFactory(Mine.class, new ClassFactory() { }))       // one it shared
                .isInstanceOf(UnsupportedOperationException.class);

        WriteOptionsBuilder write = new WriteOptionsBuilder();
        write.build();
        assertThatThrownBy(() -> write.addCustomWrittenClass(Mine.class, new ZoneIdWriter()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> write.addExcludedField(Mine.class, "name"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aCopyStartsFromTheOriginalWithoutChangingIt() throws Exception {
        ReadOptions original = new ReadOptionsBuilder().addNotCustomReaderClass(Mine.class).build();
        ReadOptions copy = new ReadOptionsBuilder(original).addNonReferenceableClass(Mine.class).build();
        assertThat(copy.isNotCustomReaderClass(Mine.class)).isTrue();
        assertThat(copy.isNonReferenceableClass(Mine.class)).isTrue();
        assertThat(original.isNonReferenceableClass(Mine.class)).isFalse();
        assertThat(setting(copy, "notCustomReadClasses")).isSameAs(setting(original, "notCustomReadClasses"));

        WriteOptions writeOriginal = new WriteOptionsBuilder().addExcludedField(Mine.class, "name").build();
        WriteOptions writeCopy = new WriteOptionsBuilder(writeOriginal).addNonReferenceableClass(Mine.class).build();
        assertThat(writeCopy.getExcludedFields(Mine.class)).containsExactly("name");
        assertThat(writeOriginal.isNonReferenceableClass(Mine.class)).isFalse();
        @SuppressWarnings("unchecked")
        Map<Class<?>, ?> excluded = (Map<Class<?>, ?>) setting(writeCopy, "excludedFieldNames");
        assertThat(excluded).isSameAs(setting(writeOriginal, "excludedFieldNames"));
    }
}
