package com.cedarsoftware.io.spring.ai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for json-io Spring AI TOON integration.
 *
 * <pre>
 * spring:
 *   json-io:
 *     ai:
 *       tool-call:
 *         key-folding: true
 *       output:
 *         strict-toon: false
 * </pre>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 */
@ConfigurationProperties(prefix = "spring.json-io.ai")
public class JsonIoAiProperties {

    private final ToolCall toolCall = new ToolCall();
    private final Output output = new Output();

    public ToolCall getToolCall() {
        return toolCall;
    }

    public Output getOutput() {
        return output;
    }

    /**
     * Configuration for ToonToolCallResultConverter.
     */
    public static class ToolCall {

        /** Enable TOON key folding for more compact output. Default: true. */
        private boolean keyFolding = true;

        public boolean isKeyFolding() {
            return keyFolding;
        }

        public void setKeyFolding(boolean keyFolding) {
            this.keyFolding = keyFolding;
        }
    }

    /**
     * Configuration for ToonBeanOutputConverter.
     */
    public static class Output {

        /** Enable strict TOON parsing for LLM responses. Default: false (permissive). */
        private boolean strictToon = false;

        public boolean isStrictToon() {
            return strictToon;
        }

        public void setStrictToon(boolean strictToon) {
            this.strictToon = strictToon;
        }
    }
}
