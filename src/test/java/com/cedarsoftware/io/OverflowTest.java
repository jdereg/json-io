package com.cedarsoftware.io;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class OverflowTest
{
    private static String nestedDoc(int nesting, String open, String close, String content)
    {
        StringBuilder sb = new StringBuilder(nesting * (open.length() + close.length()));
        for (int i=0; i < nesting ;i++)
        {
            sb.append(open);
            if ((i & 31) == 0)
            {
                sb.append("\n");
            }
        }

        sb.append("\n").append(content).append("\n");
        for (int i=0; i < nesting; i++)
        {
            sb.append(close);
            if ((i & 31) == 0)
            {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Test
    public void testOverflow()
    {
        Throwable thrown = assertThrows(JsonIoException.class,() -> { TestUtil.toObjects(TOO_DEEP_DOC, null); });
        Assertions.assertTrue(thrown.getMessage().contains("Maximum parsing depth exceeded"));
    }

    private static final int TOO_DEEP_NESTING = 9999;
    private static final String TOO_DEEP_DOC = nestedDoc(TOO_DEEP_NESTING, "[ ", "] ", "0");
}
