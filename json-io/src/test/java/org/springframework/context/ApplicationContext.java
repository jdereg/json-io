package org.springframework.context;

/**
 * Stand-in for Spring's {@code ApplicationContext} so
 * {@link com.cedarsoftware.io.ConstructorInjectionSecurityTest} can reproduce the reported
 * remote-code-execution payload without json-io's core module taking a dependency on Spring.
 * <p>
 * java-util blocks this family by fully-qualified name, matched against every supertype, so a class
 * declared here reproduces the real classpath condition exactly: nothing loads Spring, the name is
 * simply recognized.
 */
public interface ApplicationContext {
}
