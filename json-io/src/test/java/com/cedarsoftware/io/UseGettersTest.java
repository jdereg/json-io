package com.cedarsoftware.io;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cedarsoftware.io.annotation.IoGetter;
import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a field's value is obtained when an object is written. By default the field itself is read, so what is
 * written is the object's state and no code of the class runs. {@code WriteOptionsBuilder.useGetters(true)} calls the
 * class's public getter instead, when it has one.
 *
 * <p>Through json-io 4.113.0 the getter was always called. A getter that returns something other than its field - a
 * computed value, a copy, an unmodifiable view - then put that into the JSON, and reading the JSON back gave an object
 * that was not the one written.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class UseGettersTest {

    private static final WriteOptions FIELDS = new WriteOptionsBuilder().showTypeInfoNever().build();
    private static final WriteOptions GETTERS = new WriteOptionsBuilder().showTypeInfoNever().useGetters(true).build();

    /** Every getter returns something other than its field. */
    public static class Reading {
        private int value = 1;
        private boolean ready = false;
        private Boolean checked = Boolean.FALSE;
        private String label = "raw";
        transient int getterCalls;

        public int getValue() {
            getterCalls++;
            return 99;
        }

        public boolean isReady() {
            getterCalls++;
            return true;
        }

        public Boolean isChecked() {
            getterCalls++;
            return Boolean.TRUE;
        }

        public String getLabel() {
            getterCalls++;
            return label.toUpperCase();
        }
    }

    /** The getter hands out a read-only view of the list, not the list. */
    public static class Team {
        private List<String> members = new ArrayList<>();
        transient int accessorCalls;

        public List<String> getMembers() {
            accessorCalls++;
            return Collections.unmodifiableList(members);
        }

        public void setMembers(List<String> members) {
            accessorCalls++;
            this.members = members;
        }
    }

    public static class Shapes {
        int packageGetter = 1;
        int wrongType = 1;
        int withArgument = 1;
        int plain = 1;

        int getPackageGetter() {
            return 91;
        }

        public Integer getWrongType() {
            return 92;
        }

        public int getWithArgument(int scale) {
            return 93;
        }

        public int getPlain() {
            return 94;
        }
    }

    static class NotPublic {
        int a = 1;

        public int getA() {
            return 95;
        }
    }

    /** The getter is named for the field, so it is used whatever the option says. */
    public static class Named {
        String code = "abc";

        @IoGetter("code")
        public String exportCode() {
            return code.toUpperCase();
        }
    }

    public static class Registered {
        String state = "raw";

        public String getState() {
            return "from getState";
        }

        public String exportState() {
            return "from exportState";
        }
    }

    public enum Lamp {
        ON, OFF;

        private boolean lit = true;
        private String label = "lamp";
        private int hidden = 5;

        public boolean isLit() {
            return lit;
        }

        public String getLabel() {
            return label;
        }
    }

    /** A class with its own way out, which only an AccessorFactory the caller adds knows about. */
    public static class Odd {
        String label = "abc";

        public String fetchLabel() {
            return "<" + label + ">";
        }
    }

    @Test
    void byDefaultTheFieldsAreWrittenAndNoGetterRuns() {
        Reading reading = new Reading();

        assertEquals("{\"value\":1,\"ready\":false,\"checked\":false,\"label\":\"raw\"}",
                JsonIo.toJson(reading, FIELDS));
        assertEquals(0, reading.getterCalls);
        assertFalse(FIELDS.isUseGetters());
    }

    @Test
    void withUseGettersThePublicGettersRun() {
        Reading reading = new Reading();

        assertEquals("{\"value\":99,\"ready\":true,\"checked\":true,\"label\":\"RAW\"}",
                JsonIo.toJson(reading, GETTERS));
        assertTrue(reading.getterCalls >= 4);
        assertTrue(GETTERS.isUseGetters());
    }

    @Test
    void theSameHoldsForToon() {
        Reading byFields = new Reading();
        assertEquals("value: 1\nready: false\nchecked: false\nlabel: raw", JsonIo.toToon(byFields, FIELDS));
        assertEquals(0, byFields.getterCalls);

        assertEquals("value: 99\nready: true\nchecked: true\nlabel: RAW", JsonIo.toToon(new Reading(), GETTERS));
    }

    /** Fields out and fields in: the object that comes back holds what the one written held, and no accessor ran. */
    @Test
    void whatIsWrittenIsWhatWasHeld() {
        Team team = new Team();
        team.members.add("Ada");
        team.members.add("Bob");

        String json = JsonIo.toJson(team, new WriteOptionsBuilder().build());
        Team copy = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Team.class);

        assertEquals(0, team.accessorCalls);
        assertEquals(0, copy.accessorCalls);
        assertSame(ArrayList.class, copy.members.getClass());
        assertEquals(team.members, copy.members);
    }

    /** The two options are independent: any pairing of them writes and reads. */
    @Test
    void theReadAndWriteOptionsMayDiffer() {
        Team team = new Team();
        team.members.add("Ada");

        for (boolean getters : new boolean[]{false, true}) {
            for (boolean setters : new boolean[]{false, true}) {
                team.accessorCalls = 0;
                String json = JsonIo.toJson(team, new WriteOptionsBuilder().useGetters(getters).build());
                ReadOptions readOptions = new ReadOptionsBuilder().useSetters(setters).build();
                Team copy = JsonIo.toJava(json, readOptions).asClass(Team.class);

                assertEquals(team.members, copy.members, "getters " + getters + ", setters " + setters);
                assertEquals(getters, team.accessorCalls > 0);
                assertEquals(setters, copy.accessorCalls > 0);
            }
        }
    }

    /** Public class, public method, no argument, and exactly the field's type: nothing else is a getter. */
    @Test
    void whichMethodsCountAsGetters() {
        assertEquals("{\"packageGetter\":1,\"wrongType\":1,\"withArgument\":1,\"plain\":94}",
                JsonIo.toJson(new Shapes(), GETTERS));
        assertEquals("{\"a\":1}", JsonIo.toJson(new NotPublic(), GETTERS), "the class is not public");

        assertEquals("{\"packageGetter\":1,\"wrongType\":1,\"withArgument\":1,\"plain\":1}",
                JsonIo.toJson(new Shapes(), FIELDS));
    }

    @Test
    void aGetterNamedForAFieldIsUsedInBothModes() {
        for (boolean useGetters : new boolean[]{false, true}) {
            WriteOptions annotated = new WriteOptionsBuilder().showTypeInfoNever().useGetters(useGetters).build();
            assertEquals("{\"code\":\"ABC\"}", JsonIo.toJson(new Named(), annotated));

            WriteOptions registered = new WriteOptionsBuilder().showTypeInfoNever().useGetters(useGetters)
                    .addNonStandardGetter(Registered.class, "state", "exportState").build();
            assertEquals("{\"state\":\"from exportState\"}", JsonIo.toJson(new Registered(), registered));
        }
    }

    @Test
    void aMethodFilterTakesAGetterOutOfUse() {
        WriteOptions filtered = new WriteOptionsBuilder().showTypeInfoNever().useGetters(true)
                .addNamedMethodFilter("no getState", Registered.class, "getState").build();

        assertEquals("{\"state\":\"raw\"}", JsonIo.toJson(new Registered(), filtered));
        assertEquals("{\"state\":\"from getState\"}", JsonIo.toJson(new Registered(), GETTERS));
    }

    /** An AccessorFactory the caller adds is the caller's own instruction, so it is asked in both modes. */
    @Test
    void anAccessorFactoryTheCallerAddsIsAskedInBothModes() {
        AccessorFactory oddOnly = (field, nonStandardGetters, uniqueFieldName) ->
                field.getDeclaringClass() == Odd.class && field.getName().equals("label")
                        ? Accessor.createMethodAccessor(field, "fetchLabel", uniqueFieldName) : null;

        for (boolean useGetters : new boolean[]{false, true}) {
            WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().useGetters(useGetters)
                    .addAccessorFactory("odd", oddOnly).build();

            assertEquals("{\"label\":\"<abc>\"}", JsonIo.toJson(new Odd(), options));
        }
    }

    /**
     * A JDK class keeps its fields closed (JDK 17 and later), so its public getters are the only way to the values:
     * they are used even though getters were not asked for.
     */
    @Test
    void aFieldThatCannotBeReachedIsReadThroughItsGetter() {
        ParsePosition position = new ParsePosition(7);
        position.setErrorIndex(3);

        for (WriteOptions options : new WriteOptions[]{FIELDS, GETTERS}) {
            assertEquals("{\"index\":7,\"errorIndex\":3}", JsonIo.toJson(position, options));
        }
    }

    /**
     * An enum written as an object shows its public fields only, by default. A private field with a public getter
     * has always counted as public there, and still does now that its value is read from the field.
     */
    @Test
    void anEnumFieldWithAPublicGetterStillCountsAsPublic() {
        WriteOptions asObject = new WriteOptionsBuilder().showTypeInfoNever().writeEnumAsJsonObject(true).build();

        assertEquals("{\"lit\":true,\"label\":\"lamp\",\"name\":\"ON\"}", JsonIo.toJson(Lamp.ON, asObject));
    }

    /**
     * The JDK's own classes are read through their getters on every JDK, also where their fields could be opened
     * (JDK 8): their fields are an implementation detail. A Throwable without a cause holds itself in "cause", and
     * getCause() is what says there is none.
     */
    @Test
    void aJdkClassIsReadThroughItsGettersOnEveryJdk() {
        for (Accessor accessor : FIELDS.getAccessorsForClass(ParsePosition.class)) {
            assertTrue(accessor.isMethod(), accessor.getUniqueFieldName());
        }

        String json = JsonIo.toJson(new IllegalStateException("no cause"), FIELDS);
        assertTrue(json.contains("\"detailMessage\":\"no cause\""), json);
        assertTrue(json.contains("\"cause\":null"), json);
    }

    @Test
    void whichAccessorIsBuilt() {
        for (Accessor accessor : FIELDS.getAccessorsForClass(Reading.class)) {
            assertFalse(accessor.isMethod(), accessor.getUniqueFieldName());
            assertEquals(accessor.getActualFieldName(), accessor.getFieldOrMethodName());
        }
        for (Accessor accessor : GETTERS.getAccessorsForClass(Reading.class)) {
            assertTrue(accessor.isMethod(), accessor.getUniqueFieldName());
        }
    }

    @Test
    void theOptionIsCopiedWithTheOptions() {
        assertTrue(new WriteOptionsBuilder(GETTERS).build().isUseGetters());
        assertFalse(new WriteOptionsBuilder(FIELDS).build().isUseGetters());
        assertFalse(new WriteOptionsBuilder(GETTERS).useGetters(false).build().isUseGetters());
    }

    @Test
    void thePermanentSettingIsTheDefaultOfEveryNewBuilder() {
        try {
            WriteOptionsBuilder.addPermanentUseGetters(true);
            WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
            assertTrue(options.isUseGetters());
            assertEquals("{\"value\":99,\"ready\":true,\"checked\":true,\"label\":\"RAW\"}",
                    JsonIo.toJson(new Reading(), options));
            assertFalse(new WriteOptionsBuilder().useGetters(false).build().isUseGetters());
        } finally {
            WriteOptionsBuilder.addPermanentUseGetters(false);
        }
        assertFalse(new WriteOptionsBuilder().build().isUseGetters());
    }
}
