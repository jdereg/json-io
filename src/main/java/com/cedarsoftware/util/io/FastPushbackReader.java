package com.cedarsoftware.util.io;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * This class adds significant performance increase over using the JDK
 * PushbackReader.  This is due to this class not using synchronization
 * as it is not needed.
 */
public class FastPushbackReader extends FilterReader
{
    private static final int bufsize = 256;
    private final int[] buf = new int[bufsize];
    private int idx = 0;
    private int unread = Integer.MAX_VALUE;
    protected int line = 1;
    protected int col = 0;

    FastPushbackReader(Reader reader)
    {
        super(reader);
    }

    String getLastSnippet()
    {
        StringBuilder s = new StringBuilder();
        for (int i=idx; i < bufsize; i++)
        {
            if (appendChar(s, i))
            {
                break;
            }
        }
        for (int i=0; i < idx; i++)
        {
            if (appendChar(s, i))
            {
                break;
            }
        }
        return s.toString();
    }

    private boolean appendChar(StringBuilder s, int i)
    {
        try
        {
            final int snip = buf[i];
            if (snip == 0)
            {
                return true;
            }
            s.appendCodePoint(snip);
        }
        catch (Exception e)
        {
            return true;
        }
        return false;
    }

    public int read() throws IOException
    {
        int ch = unread == Integer.MAX_VALUE ? in.read() : unread;
        unread = Integer.MAX_VALUE;

        buf[idx++] = ch;
        if (idx >= bufsize)
        {
            idx = 0;
        }

        if (ch == 0x0a)
        {
            line++;
            col = 0;
        }
        else
        {
            col++;
        }
        return ch;
    }

    public void unread(int c) throws IOException
    {
        unread = c;
        if (idx < 1)
        {
            idx = bufsize - 1;
        }
        else
        {
            idx--;
        }

        if (c == 0x0a)
        {
            line--;
        }
        else
        {
            col--;
        }
    }
}
