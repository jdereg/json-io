package com.cedarsoftware.io;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

import com.cedarsoftware.io.annotation.IoSetter;
import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a field's value is stored when an object is read. By default the field itself is assigned, so no code of the
 * class runs while it is being read and the object comes back in the state that was written.
 * {@code ReadOptionsBuilder.useSetters(true)} calls the class's public setter instead, when it has one.
 *
 * <p>json-io 4.55.0 through 4.113.0 called the setter without being asked. A setter that does more than assign then
 * ran on an object that was still being built: a value could be dropped, or an entry added twice, with no error.
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
class UseSettersTest {

    private static final ReadOptions FIELDS = new ReadOptionsBuilder().build();
    private static final ReadOptions SETTERS = new ReadOptionsBuilder().useSetters(true).build();

    /** A setter that is not a plain setter: it changes the value only for an object some registry knows. */
    public static class Owner {
        String id;
        boolean active = true;
        transient int setterCalls;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            setterCalls++;
            // An object that is still being read is registered nowhere yet, so the value is dropped.
        }
    }

    /** A two-way link that the setter keeps in step. */
    public static class Parent {
        public String name;
        public List<Child> children = new ArrayList<>();
    }

    public static class Child {
        public String name;
        public Parent parent;

        public void setParent(Parent parent) {
            this.parent = parent;
            if (parent != null && parent.children != null) {
                parent.children.add(this);
            }
        }
    }

    /** A setter that keeps its own copy of the list it is given. */
    public static class Roster {
        private List<String> members = new ArrayList<>();

        public void setMembers(List<String> members) {
            this.members = new ArrayList<>(members);
        }
    }

    public static class Validated {
        int age = 1;

        public void setAge(int age) {
            throw new IllegalStateException("not ready");
        }
    }

    public static class Shapes {
        int packageSetter = 1;
        int fluent = 1;
        int boxed = 1;
        int plain = 1;
        transient String called = "";

        void setPackageSetter(int v) {
            called += "packageSetter ";
        }

        public Shapes setFluent(int v) {
            called += "fluent ";
            return this;
        }

        public void setBoxed(Integer v) {
            called += "boxed ";
        }

        public void setPlain(int v) {
            called += "plain ";
            plain = v;
        }
    }

    static class NotPublic {
        int a = 1;
        transient int setterCalls;

        public void setA(int a) {
            setterCalls++;
        }
    }

    public static class Base {
        int a = 1;
        transient String called = "";

        public void setA(int a) {
            called += "Base.setA ";
            this.a = a;
        }
    }

    public static class Derived extends Base {
        int b = 1;

        @Override
        public void setA(int a) {
            called += "Derived.setA ";
            this.a = a * 10;
        }
    }

    /** The setter is named for the field, so it is used whatever the option says. */
    public static class Named {
        String code = "none";
        transient int setterCalls;

        @IoSetter("code")
        public void applyCode(String code) {
            setterCalls++;
            this.code = code.toUpperCase();
        }
    }

    /** Its setter is registered with addPermanentNonStandardSetter: the way around a busy setter before 4.114.0. */
    public static class Registered {
        String state = "none";
        transient String called = "";

        public void setState(String state) {
            called += "setState ";
        }

        public void loadState(String state) {
            called += "loadState ";
            this.state = state;
        }
    }

    /** A class with its own way in, which only an InjectorFactory the caller adds knows about. */
    public static class Odd {
        String label = "none";
        transient int calls;

        public void receiveLabel(String label) {
            calls++;
            this.label = "<" + label + ">";
        }
    }

    private static String typed(Class<?> c, String fields) {
        return "{\"@type\":\"" + c.getName() + "\"," + fields + "}";
    }

    @Test
    void byDefaultTheFieldIsAssignedAndNoSetterRuns() {
        String json = typed(Owner.class, "\"id\":\"abc-123\",\"active\":false");
        Owner owner = JsonIo.toJava(json, FIELDS).asClass(Owner.class);

        assertFalse(owner.isActive(), "the JSON says false");
        assertEquals("abc-123", owner.id);
        assertEquals(0, owner.setterCalls);
        assertFalse(FIELDS.isUseSetters());
    }

    @Test
    void withUseSettersThePublicSetterRuns() {
        String json = typed(Owner.class, "\"id\":\"abc-123\",\"active\":false");
        Owner owner = JsonIo.toJava(json, SETTERS).asClass(Owner.class);

        assertEquals(1, owner.setterCalls);
        assertTrue(owner.isActive(), "this setter drops the value: that is what the caller asked to run");
        assertEquals("abc-123", owner.id, "a field with no setter is still assigned");
        assertTrue(SETTERS.isUseSetters());
    }

    @Test
    void theSameHoldsForToon() {
        assertFalse(JsonIo.fromToon("id: abc-123\nactive: false", FIELDS).asClass(Owner.class).isActive());
        assertEquals(1, JsonIo.fromToon("id: abc-123\nactive: false", SETTERS).asClass(Owner.class).setterCalls);
    }

    @Test
    void aTwoWayLinkReadsBackAsItWasWritten() {
        Parent parent = new Parent();
        parent.name = "p";
        for (String name : new String[]{"c1", "c2"}) {
            Child child = new Child();
            child.name = name;
            child.setParent(parent);
        }
        String json = JsonIo.toJson(parent, null);

        Parent byFields = JsonIo.toJava(json, FIELDS).asClass(Parent.class);
        assertEquals(2, byFields.children.size());
        assertEquals("c1", byFields.children.get(0).name);
        assertEquals("c2", byFields.children.get(1).name);
        assertTrue(byFields.children.get(0).parent == byFields);

        // With setters, each child is in the list that was read and is added to it once more by setParent.
        assertEquals(4, JsonIo.toJava(json, SETTERS).asClass(Parent.class).children.size());
    }

    /**
     * What a setter is given has not been filled yet: json-io sets the list into the object first and reads its
     * items afterwards. A setter that copies its argument copies it empty. The field holds the list itself, so
     * assigning the field loses nothing.
     */
    @Test
    void aSetterIsHandedAListThatIsNotFilledYet() {
        String json = typed(Roster.class, "\"members\":[\"Ada\",\"Bob\"]");

        assertEquals(2, JsonIo.toJava(json, FIELDS).asClass(Roster.class).members.size());
        assertEquals(0, JsonIo.toJava(json, SETTERS).asClass(Roster.class).members.size());
    }

    @Test
    void aSetterThatThrowsOnlyMattersWhenSettersAreUsed() {
        assertEquals(30, JsonIo.toJava(typed(Validated.class, "\"age\":30"), FIELDS).asClass(Validated.class).age);

        assertThatThrownBy(() -> JsonIo.toJava(typed(Validated.class, "\"age\":30"), SETTERS).asClass(Validated.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unable to set field: age using setAge");
    }

    /** Public class, public method, void, and a parameter of exactly the field's type: nothing else is a setter. */
    @Test
    void whichMethodsCountAsSetters() {
        String json = typed(Shapes.class, "\"packageSetter\":5,\"fluent\":6,\"boxed\":7,\"plain\":8");

        Shapes shapes = JsonIo.toJava(json, SETTERS).asClass(Shapes.class);
        assertEquals("plain ", shapes.called);
        assertEquals(5, shapes.packageSetter);
        assertEquals(6, shapes.fluent);
        assertEquals(7, shapes.boxed);
        assertEquals(8, shapes.plain);

        NotPublic notPublic = JsonIo.toJava(typed(NotPublic.class, "\"a\":5"), SETTERS).asClass(NotPublic.class);
        assertEquals(0, notPublic.setterCalls, "the class is not public");
        assertEquals(5, notPublic.a);

        assertEquals("", JsonIo.toJava(json, FIELDS).asClass(Shapes.class).called);
    }

    @Test
    void anInheritedSetterIsCalledAndAnOverrideWins() {
        Derived derived = JsonIo.toJava(typed(Derived.class, "\"a\":5,\"b\":6"), SETTERS).asClass(Derived.class);
        assertEquals("Derived.setA ", derived.called);
        assertEquals(50, derived.a);
        assertEquals(6, derived.b);

        Derived byFields = JsonIo.toJava(typed(Derived.class, "\"a\":5,\"b\":6"), FIELDS).asClass(Derived.class);
        assertEquals("", byFields.called);
        assertEquals(5, byFields.a);
    }

    @Test
    void aSetterNamedForAFieldIsUsedInBothModes() {
        for (ReadOptions options : new ReadOptions[]{FIELDS, SETTERS}) {
            Named named = JsonIo.toJava(typed(Named.class, "\"code\":\"abc\""), options).asClass(Named.class);

            assertEquals(1, named.setterCalls);
            assertEquals("ABC", named.code);
        }
    }

    /** A non-standard setter is named for its field, so it is used in both modes, ahead of setState. */
    @Test
    void aNonStandardSetterIsUsedInBothModes() {
        ReadOptionsBuilder.addPermanentNonStandardSetter(Registered.class, "state", "loadState");

        for (boolean useSetters : new boolean[]{false, true}) {
            ReadOptions options = new ReadOptionsBuilder().useSetters(useSetters).build();
            Registered registered = JsonIo.toJava(typed(Registered.class, "\"state\":\"ready\""), options)
                    .asClass(Registered.class);

            assertEquals("loadState ", registered.called);
            assertEquals("ready", registered.state);
        }
    }

    /** An InjectorFactory the caller adds is the caller's own instruction, so it is asked in both modes. */
    @Test
    void anInjectorFactoryTheCallerAddsIsAskedInBothModes() {
        InjectorFactory oddOnly = (field, nonStandardNames, uniqueName) ->
                field.getDeclaringClass() == Odd.class && field.getName().equals("label")
                        ? Injector.create(field, "receiveLabel", uniqueName) : null;

        for (boolean useSetters : new boolean[]{false, true}) {
            ReadOptions options = new ReadOptionsBuilder().addInjectorFactory(oddOnly).useSetters(useSetters).build();
            Odd odd = JsonIo.toJava(typed(Odd.class, "\"label\":\"abc\""), options).asClass(Odd.class);

            assertEquals(1, odd.calls);
            assertEquals("<abc>", odd.label);
        }
    }

    /**
     * A JDK class keeps its fields closed (JDK 17 and later), so its public setters are the only way in: they are
     * used even though setters were not asked for.
     */
    @Test
    void aFieldThatCannotBeReachedIsSetThroughItsSetter() {
        String json = typed(ParsePosition.class, "\"index\":7,\"errorIndex\":3");

        for (ReadOptions options : new ReadOptions[]{FIELDS, SETTERS}) {
            ParsePosition position = JsonIo.toJava(json, options).asClass(ParsePosition.class);

            assertEquals(7, position.getIndex());
            assertEquals(3, position.getErrorIndex());
        }
    }

    /** On every JDK, also where the JDK's fields could be opened (JDK 8): a JDK class is set through its setters. */
    @Test
    void aJdkClassIsSetThroughItsSettersOnEveryJdk() {
        assertEquals("setIndex", FIELDS.getDeepInjectorMap(ParsePosition.class).get("index").getDisplayName());
        assertEquals("setErrorIndex",
                FIELDS.getDeepInjectorMap(ParsePosition.class).get("errorIndex").getDisplayName());
    }

    @Test
    void theOptionIsCopiedWithTheOptions() {
        assertTrue(new ReadOptionsBuilder(SETTERS).build().isUseSetters());
        assertFalse(new ReadOptionsBuilder(FIELDS).build().isUseSetters());
        assertFalse(new ReadOptionsBuilder(SETTERS).useSetters(false).build().isUseSetters());
    }

    @Test
    void thePermanentSettingIsTheDefaultOfEveryNewBuilder() {
        try {
            ReadOptionsBuilder.addPermanentUseSetters(true);
            ReadOptions options = new ReadOptionsBuilder().build();
            assertTrue(options.isUseSetters());
            String json = typed(Owner.class, "\"id\":\"abc-123\",\"active\":false");
            assertEquals(1, JsonIo.toJava(json, options).asClass(Owner.class).setterCalls);
            assertFalse(new ReadOptionsBuilder().useSetters(false).build().isUseSetters());
        } finally {
            ReadOptionsBuilder.addPermanentUseSetters(false);
        }
        assertFalse(new ReadOptionsBuilder().build().isUseSetters());
    }
}
