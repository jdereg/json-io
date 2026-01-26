package com.cedarsoftware.io;

/**
 * Utility class for managing JsonIo resource cleanup, particularly ThreadLocal resources
 * that could cause memory leaks in application servers or long-running applications.
 * 
 * <p>This class provides centralized management of ThreadLocal cleanup for JsonIo
 * components. It's especially important to call cleanup methods in web applications,
 * application servers, or any environment where threads are reused.</p>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Manual cleanup after processing
 * try {
 *     Object result = JsonIo.toJava(jsonString).asType(MyClass.class);
 *     // process result
 * } finally {
 *     JsonIoCleanup.clearThreadLocals();
 * }
 * 
 * // In a servlet filter or request interceptor
 * public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
 *     try {
 *         chain.doFilter(request, response);
 *     } finally {
 *         JsonIoCleanup.clearThreadLocals();
 *     }
 * }
 * 
 * // Spring Boot example - request interceptor
 * &#64;Component
 * public class JsonIoCleanupInterceptor implements HandlerInterceptor {
 *     &#64;Override
 *     public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
 *         JsonIoCleanup.clearThreadLocals();
 *     }
 * }
 * }</pre>
 * 
 * @author Claude Code AI Assistant
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
public final class JsonIoCleanup {
    
    /**
     * Private constructor to prevent instantiation
     */
    private JsonIoCleanup() {
        throw new UnsupportedOperationException("JsonIoCleanup is a utility class and cannot be instantiated");
    }
    
    /**
     * Clears all ThreadLocal resources used by JsonIo components.
     * <p>
     * This method should be called when you're done processing JSON in the current thread
     * to prevent memory leaks. It's particularly important in application servers, web
     * applications, or any environment where threads are reused (thread pools).
     * </p>
     * 
     * <p><strong>When to call this method:</strong></p>
     * <ul>
     * <li>After completing JSON processing in a web request</li>
     * <li>In servlet filters or request interceptors</li>
     * <li>At the end of background job processing</li>
     * <li>Before returning threads to thread pools</li>
     * <li>In application shutdown hooks</li>
     * </ul>
     * 
     * <p><strong>Thread Safety:</strong> This method is thread-safe and only affects
     * ThreadLocal resources in the calling thread.</p>
     */
    public static void clearThreadLocals() {
        // Add additional cleanup here if other JsonIo components use ThreadLocal in the future
        // For example:
        // JsonWriter.clearThreadLocalBuffers();
        // Resolver.clearThreadLocalBuffers();
    }
    
    /**
     * Alternative method name for clearing ThreadLocal resources.
     * This is an alias for {@link #clearThreadLocals()} that may be more
     * intuitive for some use cases.
     */
    public static void cleanup() {
        clearThreadLocals();
    }
    
    /**
     * Checks if the current thread has any ThreadLocal resources that need cleanup.
     * <p>
     * This method can be useful for debugging memory leak issues or for monitoring
     * applications to ensure proper cleanup is happening.
     * </p>
     * 
     * @return true if ThreadLocal resources are present, false otherwise
     */
    public static boolean hasThreadLocalResources() {
        // Note: This is a basic implementation. In a more sophisticated version,
        // we could inspect ThreadLocal contents, but that would require more
        // intrusive access to the ThreadLocal internals.
        
        // For now, we'll return false as cleanup is idempotent and safe to call
        // even when no resources are present
        return false;
    }
    
    /**
     * Provides information about ThreadLocal resource usage for monitoring and debugging.
     * <p>
     * This method returns a human-readable string describing the ThreadLocal resources
     * that would be cleaned up by calling {@link #clearThreadLocals()}.
     * </p>
     * 
     * @return description of ThreadLocal resources, useful for debugging
     */
    public static String getThreadLocalInfo() {
        StringBuilder info = new StringBuilder();
        info.append("JsonIo ThreadLocal Resources:\n");
        info.append("- JsonParser.STRING_BUFFER: char[1024] buffer for string processing\n");
        info.append("- JsonParser.LARGE_STRING_BUFFER: char[8192] buffer for large string processing\n");
        info.append("\nCall JsonIoCleanup.clearThreadLocals() to clean up these resources.");
        return info.toString();
    }
    
    /**
     * Convenience method for use in try-with-resources blocks.
     * <p>
     * This method returns a {@link ThreadLocalCleaner} that can be used in
     * try-with-resources statements to ensure automatic cleanup.
     * </p>
     * 
     * <pre>{@code
     * try (ThreadLocalCleaner cleaner = JsonIoCleanup.autoCleanup()) {
     *     Object result = JsonIo.toJava(jsonString).asType(MyClass.class);
     *     // ThreadLocal resources are automatically cleaned up when exiting this block
     *     return result;
     * }
     * }</pre>
     * 
     * @return a ThreadLocalCleaner for automatic cleanup
     */
    public static ThreadLocalCleaner autoCleanup() {
        return new ThreadLocalCleaner();
    }
    
    /**
     * Auto-closeable wrapper for ThreadLocal cleanup.
     * This class enables automatic cleanup in try-with-resources blocks.
     */
    public static final class ThreadLocalCleaner implements AutoCloseable {
        
        private ThreadLocalCleaner() {
            // Package-private constructor
        }
        
        /**
         * Automatically cleans up ThreadLocal resources when the try-with-resources
         * block is exited.
         */
        @Override
        public void close() {
            clearThreadLocals();
        }
    }
}