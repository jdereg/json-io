package com.cedarsoftware.io.spring.ai.converter;

import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.TypeHolder;

import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.lang.Nullable;

/**
 * A {@link StructuredOutputConverter} that instructs the LLM to respond in TOON format
 * and parses the TOON response back to a typed Java object using json-io.
 *
 * <p>This converter replaces the default Jackson-based {@code BeanOutputConverter}.
 * It provides format instructions via {@link #getFormat()} that teach the LLM to produce
 * TOON output, and parses that output back to Java via {@link #convert(String)}.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
 *
 * // Use with ChatClient:
 * Person person = chatClient.prompt()
 *     .user("Get info about John")
 *     .call()
 *     .entity(converter);
 * }</pre>
 *
 * <p>For generic types:</p>
 * <pre>{@code
 * ToonBeanOutputConverter<List<Person>> converter =
 *     new ToonBeanOutputConverter<>(new TypeHolder<List<Person>>() {});
 * }</pre>
 *
 * @param <T> the target type to convert TOON responses into
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
public class ToonBeanOutputConverter<T> implements StructuredOutputConverter<T> {

    private static final String FORMAT_INSTRUCTIONS = """
            Your response should be in TOON (Token-Oriented Object Notation) format.
            TOON is an indentation-based format with these rules:
            - Object fields use "key: value" syntax, one per line
            - Nested objects are indented by 2 spaces
            - Arrays use a count prefix "[N]:" where N is the number of elements
            - Each array element is prefixed with "- " (hyphen space) on its own indented line
            - Strings do not need quotes unless they contain special characters (colon, newline)
            - No braces, brackets, or trailing commas

            Example TOON for a person object:
            name: John Smith
            age: 30
            address:
              street: 123 Main St
              city: Springfield

            Example TOON for a list of items:
            [2]:
              - name: Item A
                price: 9.99
              - name: Item B
                price: 19.99

            Example TOON for a simple list:
            [3]:
              - apples
              - bananas
              - cherries

            Do not include any explanation or markdown formatting. Respond only with the raw TOON content.""";

    private final Type targetType;
    private final ReadOptions readOptions;

    /**
     * Create a converter for the given target class with default ReadOptions.
     *
     * @param targetClass the class to deserialize TOON responses into
     */
    public ToonBeanOutputConverter(Class<T> targetClass) {
        this(targetClass, null);
    }

    /**
     * Create a converter for the given target class with custom ReadOptions.
     *
     * @param targetClass the class to deserialize TOON responses into
     * @param readOptions custom read options; if null, defaults with permissive TOON parsing
     */
    public ToonBeanOutputConverter(Class<T> targetClass, @Nullable ReadOptions readOptions) {
        this.targetType = targetClass;
        this.readOptions = readOptions != null ? readOptions : defaultReadOptions();
    }

    /**
     * Create a converter for a generic type (e.g., {@code List<Person>}) with default ReadOptions.
     *
     * @param typeHolder captures the full generic type information
     */
    public ToonBeanOutputConverter(TypeHolder<T> typeHolder) {
        this(typeHolder, null);
    }

    /**
     * Create a converter for a generic type with custom ReadOptions.
     *
     * @param typeHolder  captures the full generic type information
     * @param readOptions custom read options; if null, defaults with permissive TOON parsing
     */
    public ToonBeanOutputConverter(TypeHolder<T> typeHolder, @Nullable ReadOptions readOptions) {
        this.targetType = typeHolder.getType();
        this.readOptions = readOptions != null ? readOptions : defaultReadOptions();
    }

    @Override
    public String getFormat() {
        return FORMAT_INSTRUCTIONS;
    }

    @Override
    public T convert(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = stripCodeFences(text);
        return JsonIo.fromToon(cleaned, readOptions)
                .asType(TypeHolder.forType(targetType));
    }

    /**
     * Strip markdown code fences ({@code ```toon ... ```} or {@code ``` ... ```}) if present.
     * LLMs occasionally wrap their response in code blocks even when told not to.
     */
    private static String stripCodeFences(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
            }
        }
        return trimmed;
    }

    private static ReadOptions defaultReadOptions() {
        return new ReadOptionsBuilder()
                .strictToon(false)
                .build();
    }
}
