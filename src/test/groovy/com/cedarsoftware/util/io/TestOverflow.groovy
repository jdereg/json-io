package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertThrows
import static org.junit.Assert.assertTrue

class TestOverflow
{
    private final static int TOO_DEEP_NESTING = 9999;
    private final static String TOO_DEEP_DOC = nestedDoc(TOO_DEEP_NESTING, "[ ", "] ", "0");

    private static String nestedDoc(int nesting, String open, String close, String content) {
        StringBuilder sb = new StringBuilder(nesting * (open.length() + close.length()));
        for (int i = 0; i < nesting; ++i) {
            sb.append(open);
            if ((i & 31) == 0) {
                sb.append("\n");
            }
        }
        sb.append("\n").append(content).append("\n");
        for (int i = 0; i < nesting; ++i) {
            sb.append(close);
            if ((i & 31) == 0) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Test
    void testOverflow()
    {
        Throwable thrown = assertThrows(JsonIoException.class, { JsonReader.jsonToJava(TOO_DEEP_DOC) })
        assertTrue("", thrown.message.contains("Maximum parsing depth exceeded"))
    }
}
