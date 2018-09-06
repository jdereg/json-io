package com.cedarsoftware.util.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * This class adds significant performance increase over using the JDK
 * PushbackReader.  This is due to this class not using synchronization
 * as it is not needed.
 */
public class FastPushbackBufferedReader extends BufferedReader implements FastPushbackReader
{
    private final int[] buf = new int[256];
    private int idx = 0;
    private int unread = Integer.MAX_VALUE;
    protected int line = 1;
    protected int col = 0;

    public FastPushbackBufferedReader(Reader reader)
    {
        super(reader);
    }

    public String getLastSnippet()
    {
        StringBuilder s = new StringBuilder();
        for (int i=idx; i < buf.length; i++)
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
        int ch;
        if (unread == 0x7fffffff)
        {
            ch = super.read();
        }
        else
        {
            ch = unread;
            unread = 0x7fffffff;
        }

        if ((buf[idx++] = ch) == 0x0a)
        {
            line++;
            col = 0;
        }
        else
        {
            col++;
        }

        if (idx >= buf.length)
        {
            idx = 0;
        }
        return ch;
    }

    public void unread(int c) throws IOException
    {
        if ((unread = c) == 0x0a)
        {
            line--;
        }
        else
        {
            col--;
        }

        if (idx < 1)
        {
            idx = buf.length - 1;
        }
        else
        {
            idx--;
        }
    }

    public int getCol()
    {
        return col;
    }

    public int getLine()
    {
        return line;
    }
}
