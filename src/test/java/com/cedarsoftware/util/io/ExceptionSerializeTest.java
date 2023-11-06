package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.ThrowableFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;

import static com.cedarsoftware.util.io.Writers.writeBasicString;
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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class ExceptionSerializeTest
{
    public static class MyException extends RuntimeException
    {
        MyException(String message, Throwable cause, long val)
        {
            super(message, cause);
            recordNumber = val;
        }

        public Long recordNumber;
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


    public class MyExceptionFactory extends ThrowableFactory
    {
        public Object newInstance(Class<?> c, JsonObject jsonObj)
        {
            String msg = (String)jsonObj.get("detailMessage");
            JsonObject jObjCause = (JsonObject)jsonObj.get("cause");
            Throwable cause = null;
            if (jObjCause != null)
            {
                JsonReader jr = new JsonReader();
                cause = (Throwable) jr.jsonObjectsToJava(jObjCause);
            }

            MyException myEx = (MyException) createException(msg, cause);
            Long rn = (Long) jsonObj.get("recordNumber");
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
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            MyException t = (MyException) obj;
            output.write("\"detailMessage\":");
            writeBasicString(output, t.getMessage());
            output.write(",\"cause\":");
            Throwable cause = t.getCause();
            String json = JsonWriter.objectToJson(cause);
            output.write(json);
            output.write(",");
            output.write("\"recordNumber\":");
            output.write(String.valueOf(t.recordNumber));
        }

        public boolean hasPrimitiveForm() { return false; }
    }


    @Test
    void testIllegalArgumentException_withNoCause()
    {
        Throwable e1 = new Throwable("That argument did not taste well.", null);
        String json = TestUtil.toJson(e1);
        Throwable e2 = TestUtil.toJava(json);

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
        Throwable t2 = TestUtil.toJava(json);

        assertThat(t1).hasCause(null);

        assertThat(t2)
                .hasCause(null)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("foo");

        assertThat(t2.getStackTrace()).isEqualTo(t1.getStackTrace());
    }

    @Test
    void testExceptionWithOnlyStringConstructor() {

        ExceptionWithStringConstructor t1 = new ExceptionWithStringConstructor("poo");

        String json = TestUtil.toJson(t1);
        Throwable t2 = TestUtil.toJava(json);

        assertThat(t1).hasCause(null);

        assertThat(t2)
                .hasCause(null)
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("poo");

        assertThat(t2.getStackTrace()).isEqualTo(t1.getStackTrace());
    }

    @Test
    void testExceptionWithThrowableConstructor() {

        ExceptionWithThrowableConstructor t1 = new ExceptionWithThrowableConstructor(new ExceptionWithStringConstructor("doo"));

        String json = TestUtil.toJson(t1);
        Throwable t2 = TestUtil.toJava(json);

        assertThat(t1).hasCauseInstanceOf(ExceptionWithStringConstructor.class);

        assertThat(t2)
                .isInstanceOf(ExceptionWithThrowableConstructor.class)
                .hasMessage("com.cedarsoftware.util.io.ExceptionSerializeTest$ExceptionWithStringConstructor: doo");

        assertThat(t2.getCause())
                .isInstanceOf(ExceptionWithStringConstructor.class)
                .hasMessage("doo");

        assertThat(t2.getStackTrace()).isEqualTo(t1.getStackTrace());
        assertThat(t2.getCause().getStackTrace()).isEqualTo(t1.getCause().getStackTrace());
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
        Throwable t2 = TestUtil.toJava(json);

        assertThat(t2)
                .isInstanceOf(JsonIoException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessage("java.lang.IllegalArgumentException: foo");

        assertThat(t2.getMessage()).isEqualTo(t1.getMessage());

        assertThat(t2.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("foo");

        assertThat(t2.getStackTrace()).isEqualTo(t1.getStackTrace());
        assertThat(t2.getCause().getStackTrace()).isEqualTo(t1.getCause().getStackTrace());

        assertThat(t2.getCause().getCause()).isNull();
        assertThat(t1.getCause().getCause()).isNull();

        assertThat(t2.getMessage()).isEqualTo(t1.getMessage());
    }

    @Test
    void testNestedException()
    {
        Throwable npe = new NullPointerException("you accessed a null with '.' fool.");
        Throwable ia = new IllegalArgumentException("That argument did not taste well.", npe);
        String json = TestUtil.toJson(ia);
        Throwable ia2 = TestUtil.toJava(json);
        assert ia2.getCause() instanceof NullPointerException;
        assert ia2.getCause() != npe;
        assert ia.getMessage().equals(ia2.getMessage());
    }

    @Test
    void tesSubclassedException()
    {
        Throwable npe = new NullPointerException("you accessed a null with '.' fool.");
        Throwable ia = new IllegalArgumentException("That argument did not taste well.", npe);
        Throwable q = new MyException("Subclassed exception with value field", ia, 16);
        JsonReader.assignInstantiator(MyException.class, new MyExceptionFactory());
        JsonWriter.addWriterPermanent(MyException.class, new MyExceptionWriter());
        String json = TestUtil.toJson(q);
        Throwable r = TestUtil.toJava(json);
        assert q.getCause() == ia;
        assert r instanceof MyException;
        MyException my = (MyException) r;
        assert my.recordNumber == 16L;
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
}
