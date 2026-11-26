package com.cedarsoftware.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoIgnore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * java-util 4.119.0 removed ReflectionUtils' optional security switches ({@code reflectionutils.security.enabled}
 * and five more system properties); they are now ignored.
 * <p>
 * What that means here: with {@code reflectionutils.sensitive.field.validation.enabled} on, java-util refused the
 * whole class for one field whose name contains a listed word ({@code password}, {@code private}, ...), and json-io,
 * given no fields, wrote the object as <code>{}</code> with no error and threw reading one. An application that set
 * those properties now gets the object written and read in full, so a field that must stay out of JSON has to be
 * left out by json-io's own means. This test holds both halves: the properties change nothing, and
 * {@code @IoIgnore}, {@code addExcludedFields()} and {@code addNotImportedField()} do the leaving out.
 */
class ReflectionUtilsSwitchesIgnoredTest {
    private static final String[] SWITCHES = {
            "reflectionutils.security.enabled",
            "reflectionutils.dangerous.class.validation.enabled",
            "reflectionutils.sensitive.field.validation.enabled",
            "reflectionutils.log.obfuscation.enabled",
    };

    public static class Account {
        public String user = "ada";
        public String password = "s3cret";
    }

    public static class Note {
        public String title = "minutes";
        public boolean isPrivate = true;
        public String body = "text";
    }

    public static class Login {
        public String user = "ada";
        @IoIgnore
        public String password = "s3cret";
    }

    private final Map<String, String> before = new LinkedHashMap<>();

    @BeforeEach
    void switchEverythingOn() {
        for (String name : SWITCHES) {
            before.put(name, System.getProperty(name));
            System.setProperty(name, "true");
        }
    }

    @AfterEach
    void restore() {
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void anObjectWithAFieldNamedLikeASecretIsWrittenAndReadInFull() {
        WriteOptions noTypes = new WriteOptionsBuilder().showTypeInfoNever().build();

        assertEquals("{\"user\":\"ada\",\"password\":\"s3cret\"}", JsonIo.toJson(new Account(), noTypes));
        assertEquals("{\"title\":\"minutes\",\"isPrivate\":true,\"body\":\"text\"}", JsonIo.toJson(new Note(), noTypes));

        Account account = JsonIo.toJava("{\"user\":\"grace\",\"password\":\"hopper\"}", new ReadOptionsBuilder().build())
                .asClass(Account.class);
        assertEquals("grace", account.user);
        assertEquals("hopper", account.password);

        Note note = JsonIo.toJava("{\"title\":\"x\",\"isPrivate\":false,\"body\":\"y\"}", new ReadOptionsBuilder().build())
                .asClass(Note.class);
        assertEquals("x", note.title);
        assertFalse(note.isPrivate);
        assertEquals("y", note.body);
    }

    @Test
    void aFieldIsKeptOutOfJsonByJsonIosOwnMeans() {
        // On the field itself
        assertEquals("{\"user\":\"ada\"}",
                JsonIo.toJson(new Login(), new WriteOptionsBuilder().showTypeInfoNever().build()));

        // Or named in the options, for a class that cannot be annotated
        WriteOptions withoutPassword = new WriteOptionsBuilder().showTypeInfoNever()
                .addExcludedFields(Account.class, Collections.singletonList("password")).build();
        assertEquals("{\"user\":\"ada\"}", JsonIo.toJson(new Account(), withoutPassword));

        // And on the way in: a value sent for the field is not taken
        ReadOptions notImported = new ReadOptionsBuilder().addNotImportedField(Account.class, "password").build();
        Account account = JsonIo.toJava("{\"user\":\"grace\",\"password\":\"hopper\"}", notImported)
                .asClass(Account.class);
        assertEquals("grace", account.user);
        assertEquals("s3cret", account.password, "the field keeps what the constructor gave it");

        Login login = JsonIo.toJava("{\"user\":\"grace\",\"password\":\"hopper\"}", new ReadOptionsBuilder().build())
                .asClass(Login.class);
        assertEquals("grace", login.user);
        assertEquals("s3cret", login.password, "an @IoIgnore field is not read either");
    }
}
