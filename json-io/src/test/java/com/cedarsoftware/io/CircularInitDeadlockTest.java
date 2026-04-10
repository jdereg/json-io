package com.cedarsoftware.io;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that ReadOptionsBuilder and WriteOptionsBuilder can be concurrently
 * initialized from separate threads without deadlock.
 *
 * Background: Both builders' static initializers previously referenced each other,
 * creating a circular class initialization dependency. When two threads concurrently
 * triggered class loading (e.g., one calling JsonIo.toJson() and another calling
 * JsonIo.toJava()), the JVM's class initialization locks would deadlock:
 *   - Thread 1 holds ReadOptionsBuilder init lock, waits for WriteOptionsBuilder
 *   - Thread 2 holds WriteOptionsBuilder init lock, waits for ReadOptionsBuilder
 *
 * This test forks a fresh JVM (where neither class has been loaded) to reproduce
 * the race condition. A deadlock manifests as the forked process hanging until
 * the timeout is reached.
 */
class CircularInitDeadlockTest {

    /**
     * Entry point for the forked JVM. Concurrently initializes both builder classes.
     * Exit codes: 0 = success, 1 = deadlock (timeout), 2 = unexpected error.
     */
    public static void main(String[] args) throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                // Trigger ReadOptionsBuilder class initialization
                ReadOptionsBuilder.getDefaultReadOptions();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                doneLatch.countDown();
            }
        }, "init-read");

        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                // Trigger WriteOptionsBuilder class initialization
                WriteOptionsBuilder.getDefaultWriteOptions();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                doneLatch.countDown();
            }
        }, "init-write");

        t1.start();
        t2.start();
        // Release both threads simultaneously to maximize chance of concurrent init
        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        if (!completed) {
            System.err.println("DEADLOCK DETECTED - Thread states:");
            System.err.println("  init-read:  " + t1.getState());
            System.err.println("  init-write: " + t2.getState());
            System.exit(1);
        }
        if (error.get() != null) {
            error.get().printStackTrace(System.err);
            System.exit(2);
        }
        System.exit(0);
    }

    @Test
    void testNoConcurrentClassInitDeadlock() throws Exception {
        String classpath = System.getProperty("java.class.path");
        String javaBin = System.getProperty("java.home") + "/bin/java";

        // Run multiple attempts — each forks a fresh JVM where classes haven't
        // been loaded yet. Multiple runs increase the chance of hitting the
        // timing window where both threads enter their static initializers.
        int attempts = 5;
        int deadlocks = 0;
        StringBuilder details = new StringBuilder();

        for (int i = 0; i < attempts; i++) {
            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classpath,
                    "com.cedarsoftware.io.CircularInitDeadlockTest"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Drain output to prevent buffer blocking
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                deadlocks++;
                details.append("  Attempt ").append(i + 1).append(": DEADLOCK (timed out after 15s)\n");
            } else if (process.exitValue() == 1) {
                deadlocks++;
                details.append("  Attempt ").append(i + 1).append(": DEADLOCK (self-detected)\n");
            } else if (process.exitValue() != 0) {
                deadlocks++;
                details.append("  Attempt ").append(i + 1).append(": ERROR (exit code ").append(process.exitValue()).append(")\n");
                details.append("    Output: ").append(output).append("\n");
            }
        }

        assertEquals(0, deadlocks,
                deadlocks + " of " + attempts + " attempts deadlocked during concurrent " +
                        "ReadOptionsBuilder/WriteOptionsBuilder class initialization:\n" + details);
    }
}
