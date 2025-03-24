package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cedarsoftware.io.factory.ThrowableFactory;
import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.StringUtilities;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonWriter.writeBasicString;
import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.CollectionUtilities.setOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class ExceptionSerializeTest
{
    public static class MyException extends RuntimeException {
        MyException(String message, Throwable cause, long val) {
            super(message, cause);
            recordNumber = val;
        }

        private Long recordNumber;

        public Long getRecordNumber() {
            return this.recordNumber;
        }

        public void setRecordNumber(Long recordNumber) {
            this.recordNumber = recordNumber;
        }
    }

    public static class ExceptionWithThrowableConstructor extends RuntimeException {
        public ExceptionWithThrowableConstructor(Throwable t) {
            super(t);
        }
    }

    public static class ExceptionWithStringConstructor extends RuntimeException {
        public ExceptionWithStringConstructor(String s) {
            super(s);
        }
    }

    public static class MyExceptionFactory extends ThrowableFactory
    {
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
        {
            String msg = (String) jObj.get("detailMessage");
            JsonObject jObjCause = (JsonObject) jObj.get("cause");
            List<Object> arguments = new ArrayList<>();

            Throwable cause = resolver.toJavaObjects(jObjCause, Throwable.class);

            if (cause != null) {
                arguments.add(cause);
            }

            gatherRemainingValues(resolver, jObj, arguments, setOf("detailMessage", "cause"));

            MyException myEx = (MyException) createException(msg, cause);
            Long rn = (Long) jObj.get("recordNumber");
            if (rn != null)
            {
                myEx.recordNumber = rn;
            }
            return myEx;
        }

        protected Throwable createException(String msg, Throwable cause)
        {
            return new MyException(msg, cause, 0);
        }
    }

    public static class MyExceptionWriter implements JsonWriter.JsonClassWriter
    {
        /**
         * Only serialize the 'detailMessage' and 'cause' field.  Serialize the cause as a String.
         * Do not write the stackTrace lines out.
         */
        public void write(Object obj, boolean showType, Writer output, WriterContext writerContext) throws IOException
        {
            MyException t = (MyException) obj;
            output.write("\"detailMessage\":");
            writeBasicString(output, t.getMessage());
            output.write(",\"cause\":");
            Throwable cause = t.getCause();
            String json = TestUtil.toJson(cause, null);
            output.write(json);
            output.write(",");
            output.write("\"recordNumber\":");
            output.write(String.valueOf(t.recordNumber));
        }

        public boolean hasPrimitiveForm(WriterContext writerContext) { return false; }
    }

    @Test
    void testIllegalArgumentException_withNoCause()
    {
        Throwable e1 = new Throwable("That argument did not taste well.", null);
        String json = TestUtil.toJson(e1);
        Throwable e2 = TestUtil.toObjects(json, null);

        assertThat(e1.getCause()).isNull();
        assertThat(e2.getCause()).isNull();

        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    void testIllegalArgumentException_thrown_usingStringConstructor() {
        Exception t1 = null;
        try {
            methodThatThrowsException();
        } catch (Exception e) {
            t1 = e;
        }

        String json = TestUtil.toJson(t1);
        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(t1).hasCause(null);

        assertThat(t2)
                .hasCause(null)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("foo");

        // stacktrace is built when exception is created,
        // will not match the original exception because we
        // are filterign these excweptions
        assertThat(t2.getStackTrace())
                .isNotNull()
                .isNotEqualTo(t1.getStackTrace());
    }

    @Test
    void testExceptionWithOnlyStringConstructor() {

        ExceptionWithStringConstructor t1 = new ExceptionWithStringConstructor("poo");

        String json = TestUtil.toJson(t1);
        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(json).doesNotContain("stackTrace");

        assertThat(t1).hasCause(null);

        assertThat(t2)
                .hasCause(null)
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("poo");

        // stacktrace is built when exception is created,
        // will not match the original exception because we
        // are filtering these exceptions
        assertThat(t2.getStackTrace())
                .isNotNull()
                .isNotEqualTo(t1.getStackTrace());
    }

    @Disabled
    @Test
    void testExceptionWithThrowableConstructor_andStackTracesIsNotFiltered() {
        ExceptionWithThrowableConstructor t1 = new ExceptionWithThrowableConstructor(new ExceptionWithStringConstructor("doo"));

        String json = TestUtil.toJson(t1, new WriteOptionsBuilder().addExcludedFields(Throwable.class, StringUtilities.commaSeparatedStringToSet("backtrace,depth,suppressedExceptions")).build());
        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(json).contains("stackTrace");

        assertThat(t1).hasCauseInstanceOf(ExceptionWithStringConstructor.class);

        assertThat(t2)
                .isInstanceOf(ExceptionWithThrowableConstructor.class)
                .hasMessage("com.cedarsoftware.io.ExceptionSerializeTest$ExceptionWithStringConstructor: doo");

        assertThat(t2.getCause())
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("doo");

        assertThat(t2.getStackTrace()).isEqualTo(t1.getStackTrace());
        assertThat(t2.getCause().getStackTrace()).isEqualTo(t1.getCause().getStackTrace());
    }

    @Test
    void testExceptionWithThrowableConstructor_andStackTracesOnSubclassIsFiltered() {
        ExceptionWithThrowableConstructor t1 = new ExceptionWithThrowableConstructor(new ExceptionWithStringConstructor("doo"));

    String json = TestUtil.toJson(t1, new WriteOptionsBuilder()
                .addExcludedFields(ExceptionWithThrowableConstructor.class, StringUtilities.commaSeparatedStringToSet("backtrace,depth,suppressedExceptions,stackTrace"))
                .addExcludedFields(Throwable.class, StringUtilities.commaSeparatedStringToSet("backtrace,depth,suppressedExceptions"))
                .build());

        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(json).doesNotContain("stackTrace");

        assertThat(t1).hasCauseInstanceOf(ExceptionWithStringConstructor.class);

        assertThat(t2)
                .isInstanceOf(ExceptionWithThrowableConstructor.class)
                .hasMessage("com.cedarsoftware.io.ExceptionSerializeTest$ExceptionWithStringConstructor: doo");

        assertThat(t2.getCause())
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("doo");
    }

    @Test
    void testExceptionWithThrowableConstructor_withNoStackTraces() {
        ExceptionWithThrowableConstructor t1 = new ExceptionWithThrowableConstructor(new ExceptionWithStringConstructor("doo"));
        WriteOptions options = new WriteOptionsBuilder().addExcludedField(Throwable.class, "stackTrace").build();

        String json = TestUtil.toJson(t1, options);
        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(t1).hasCauseInstanceOf(ExceptionWithStringConstructor.class);

        assertThat(t2)
                .isInstanceOf(ExceptionWithThrowableConstructor.class)
                .hasMessage("com.cedarsoftware.io.ExceptionSerializeTest$ExceptionWithStringConstructor: doo");

        assertThat(t2.getCause())
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("doo");


        assertThat(t2.getStackTrace()).isNotEqualTo(t1.getStackTrace());
        assertThat(t2.getCause().getStackTrace()).isNotEqualTo(t1.getCause().getStackTrace());
    }     

    @Test
    void testIllegalArgumentException_whenRethrown_usingThrowableConstructor() {
        Exception t1 = null;
        try {
            methodThatReThrowsException();
        } catch (Exception e) {
            t1 = e;
        }

        String json = TestUtil.toJson(t1);
        Throwable t2 = TestUtil.toObjects(json, null);

        assertThat(t2)
                .isInstanceOf(JsonIoException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessage("java.lang.IllegalArgumentException: foo");

        assertThat(t2.getMessage()).isEqualTo(t1.getMessage());

        assertThat(t2.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("foo");

        assertThat(t2.getStackTrace())
                .isNotNull()
                .isNotEqualTo(t1.getStackTrace());

        assertThat(t2.getCause().getCause()).isNull();
        assertThat(t1.getCause().getCause()).isNull();

        assertThat(t2.getMessage()).isEqualTo(t1.getMessage());
    }

    @Test
    void testExceptionWithNonStandardConstructors() {
        ExceptionWithAThousandCuts t1 = new ExceptionWithAThousandCuts(listOf(new StupidEmojis(":)"), new StupidEmojis("(:"), new StupidEmojis("())")));

        String json = TestUtil.toJson(t1);
        ExceptionWithAThousandCuts t2 = TestUtil.toObjects(json, null);

        assertThat(t2.getEmojis().size()).isEqualTo(3);
        assertThat(t2.getStackTrace()).isNotNull();
        assertThat(t2.getMessage()).isNotNull();
    }

    @Test
    void testMultiParameterExceptionWithNullFields() {
        List<StupidEmojis> emojis = listOf(new StupidEmojis(":)"), new StupidEmojis("(:"), new StupidEmojis(":o)"));
        MultipleParameterConstructor t1 = new MultipleParameterConstructor("foobar",
                "some random thoughts",
                null,
                emojis,
                null);

        String json = TestUtil.toJson(t1);
        MultipleParameterConstructor t2 = TestUtil.toObjects(json, null);

        // Here's the issue to verify - can the code that instantiates a derived exception class, get its
        // detail message up to the detailMessage field on Throwable?
        assertThat(t2).hasMessage(t1.getMessage())
                .hasNoCause();

        assertThat(t2.getRandomThoughts()).isEqualTo(t1.getRandomThoughts());
        assertThat(t2.getErrorCount()).isNull();
        assertThat(t2.getEmojis()).hasSameElementsAs(emojis);

        // we didn't send stack trace by default.
        assertThat(t2.getStackTrace()).isNotEqualTo(t1.getStackTrace());
    }

    @Test
    void testMultiParameterExceptionWithNullFields1() {
        List<StupidEmojis> emojis = listOf(new StupidEmojis(":)"), new StupidEmojis("(:"), new StupidEmojis(":o)"));
        MultipleParameterConstructor t1 = new MultipleParameterConstructor("some random thoughts",
                "message",
                null,
                emojis,
                null);

        String json = TestUtil.toJson(t1);
        MultipleParameterConstructor t2 = TestUtil.toObjects(json, null);

        // Here's the issue to verify - can the code that instantiates a derived exception class, get its
        // detail message up to the detailMessage field on Throwable?
        assertThat(t2).hasMessage(t1.getMessage())
                .hasNoCause();

        assertThat(t2.getRandomThoughts()).isEqualTo(t1.getRandomThoughts());
        assertThat(t2.getErrorCount()).isNull();
        assertThat(t2.getEmojis()).hasSameElementsAs(emojis);

        // we didn't send stack trace by default.
        assertThat(t2.getStackTrace()).isNotEqualTo(t1.getStackTrace());
    }

    @Test
    void testNestedException()
    {
        Throwable npe = new NullPointerException("you accessed a null with '.' fool.");
        Throwable ia = new IllegalArgumentException("That argument did not taste well.", npe);
        String json = TestUtil.toJson(ia);
        Throwable ia2 = TestUtil.toObjects(json, null);
        assert ia2.getCause() instanceof NullPointerException;
        assert ia2.getCause() != npe;
        assert ia.getMessage().equals(ia2.getMessage());
    }

    @Test
    void testSubclassedException()
    {
        Throwable npe = new NullPointerException("you accessed a null with '.' fool.");
        Throwable ia = new IllegalArgumentException("That argument did not taste well.", npe);
        Throwable q = new MyException("Subclassed exception with value field", ia, 16);
        String json = TestUtil.toJson(q);
        Throwable r = TestUtil.toObjects(json, null);
        assert q.getCause() == ia;
        assert r instanceof MyException;
        MyException my = (MyException) r;
        assert my.recordNumber == 16L;
    }

    @Test
    void testThrowableConstructors()
    {
        Throwable t1 = new Throwable("hello");
        Throwable t2 = new Throwable("goodbye", t1);
        Throwable t3 = new Throwable(t1);
        Throwable t4 = new Throwable();

        String json = TestUtil.toJson(t1);
        t1 = TestUtil.toObjects(json, null);

        json = TestUtil.toJson(t2);
        t2 = TestUtil.toObjects(json, null);

        json = TestUtil.toJson(t3);
        t3 = TestUtil.toObjects(json, null);

        json = TestUtil.toJson(t4);
        t4 = TestUtil.toObjects(json, null);

        assert t2.getMessage().equals("goodbye");
        assert t2.getCause().getMessage().equals("hello");

        assert t3.getMessage().equals("java.lang.Throwable: hello"); // Throwable took the causes message and back-copied it to the outer Throwable
        assert t3.getCause().getMessage().equals("hello");

        assert StringUtilities.isEmpty(t4.getMessage());
        assert t4.getCause() == null;
    }

    @Test
    void testInvalidCoordinateException_fullyPopulated() {
        Set<String> coordKeys = setOf("key1", "key2", "key3");
        Set<Integer> reqKeys = setOf(3, 1, 2);

        InvalidCoordinateException e1 = new InvalidCoordinateException(
                "Missing required coordinate key: key4",
                "testCube",
                coordKeys,
                reqKeys
        );

        String json = JsonIo.toJson(e1, null);
        Exception e2 = JsonIo.toObjects(json, new ReadOptionsBuilder().build(), InvalidCoordinateException.class);
        
        assertThat(e2)
                .isInstanceOf(InvalidCoordinateException.class)
                .hasMessage("Missing required coordinate key: key4");

        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(e1, e2, options);
        if (!equals) {
            System.out.println(options.get("diff"));
        }
        assert equals;
    }

    @Test
    void testInvalidCoordinateException_mostlyNull() {
        InvalidCoordinateException e1 = new InvalidCoordinateException(
                null,
                null,
                null,
                null
        );

        String json = TestUtil.toJson(e1);
        InvalidCoordinateException e2 = TestUtil.toObjects(json, null);

        assertThat(e2)
                .isInstanceOf(InvalidCoordinateException.class)
                .hasMessage("");

        assertThat(e2.getCubeName()).isNull();
        assertThat(e2.getCoordinateKeys()).isNull();
        assertThat(e2.getRequiredKeys()).isNull();
    }

    @Test
    void testInvalidCoordinateException_deepEquals() {
        Set<String> coordKeys = setOf("key1", "key2", "key3");
        Set<String> reqKeys = setOf("key1", "key2", "key4");

        InvalidCoordinateException e1 = new InvalidCoordinateException(
                "Missing required coordinate key: key4",
                "testCube",
                coordKeys,
                reqKeys
        );

        String json = TestUtil.toJson(e1);
        InvalidCoordinateException e2 = TestUtil.toObjects(json, null);

        assertThat(DeepEquals.deepEquals(e1, e2)).isTrue();
    }
    
    void methodThatThrowsException() {
        throw new IllegalArgumentException("foo");
    }

    void methodThatReThrowsException() {
        try {
            methodThatThrowsException();
        } catch (Exception e) {
            throw new JsonIoException(e);
        }
    }

    public static class ExceptionWithAThousandCuts extends RuntimeException {
        List<StupidEmojis> emojis;

        public ExceptionWithAThousandCuts(List<StupidEmojis> emojis) {
            super(emojis == null ? null : emojis.toString());
            this.emojis = emojis;
        }

        public ExceptionWithAThousandCuts(String s, List<StupidEmojis> emojis) {
            super(s);
            this.emojis = emojis;
        }

        public ExceptionWithAThousandCuts(String message, Throwable cause, List<StupidEmojis> emojis) {
            super(message, cause);
            this.emojis = emojis;
        }

        public ExceptionWithAThousandCuts(Throwable cause, List<StupidEmojis> emojis) {
            super(cause);
            this.emojis = emojis;
        }

        public List<StupidEmojis> getEmojis() {
            return this.emojis;
        }
    }

    public static class StupidEmojis {
        String emoji;

        public StupidEmojis(String emoji) {
            this.emoji = emoji;
        }

        @Override
        public String toString() {
            return this.emoji;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StupidEmojis)) {
                return false;
            }
            StupidEmojis that = (StupidEmojis) o;
            return Objects.equals(getEmoji(), that.getEmoji());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getEmoji());
        }

        public String getEmoji() {
            return this.emoji;
        }
    }


    public static class MultipleParameterConstructor extends RuntimeException {

        private List<StupidEmojis> emojis;
        private Long errorCount;
        private String randomThoughts;

        public MultipleParameterConstructor(String message, String randomThoughts, Long errorCount, List<StupidEmojis> emojis, Exception cause) {
            super(message, cause);

            this.randomThoughts = randomThoughts;
            this.errorCount = errorCount;
            this.emojis = emojis;
        }

        public MultipleParameterConstructor(List<StupidEmojis> emojis, Long errorCount, String randomThoughts) {
            this.emojis = emojis;
            this.errorCount = errorCount;
            this.randomThoughts = randomThoughts;
        }

        public List<StupidEmojis> getEmojis() {
            return this.emojis;
        }

        public Long getErrorCount() {
            return this.errorCount;
        }

        public String getRandomThoughts() {
            return this.randomThoughts;
        }
    }

    static class InvalidCoordinateException extends IllegalArgumentException implements Serializable
    {
        private final String cubeName;
        private final Set coordinateKeys;
        private final Set requiredKeys;

        InvalidCoordinateException(String msg, String cubeName, Set coordinateKeys, Set requiredKeys)
        {
            super(msg);
            this.cubeName = cubeName;
            this.coordinateKeys = coordinateKeys;
            this.requiredKeys = requiredKeys;
        }

        String getCubeName()
        {
            return cubeName;
        }

        Set getCoordinateKeys()
        {
            return coordinateKeys;
        }

        Set getRequiredKeys()
        {
            return requiredKeys;
        }
    }

}
