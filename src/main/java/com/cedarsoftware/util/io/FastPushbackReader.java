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
    private static final int SNIPPET_LENGTH = 200;
    private final int[] buf;
    private final int[] snippet;
    private int idx;
    protected int line;
    protected int col;
    private int snippetLoc = 0;

    FastPushbackReader(Reader reader, int size)
    {
        super(reader);
        if (size <= 0)
        {
            throw new JsonIoException("size <= 0");
        }
        buf = new int[size];
        idx = size;
        snippet = new int[SNIPPET_LENGTH];
        line = 1;
        col = 0;
    }

    FastPushbackReader(Reader r)
    {
        this(r, 1);
    }

    String getLastSnippet()
    {
        StringBuilder s = new StringBuilder();
        for (int i=snippetLoc + 1; i < SNIPPET_LENGTH; i++)
        {
            if (appendChar(s, i))
            {
                break;
            }
        }
        for (int i=0; i <= snippetLoc; i++)
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
            if (snippet[i] == 0)
            {
                return true;
            }
            s.appendCodePoint(snippet[i]);
        }
        catch (Exception e)
        {
            return true;
        }
        return false;
    }

    public int read() throws IOException
    {
        final int ch = idx < buf.length ? buf[idx++] : in.read();
        if (ch >= 0)
        {
            if (ch == 0x0a)
            {
                line++;
                col = 0;
            }
            else
            {
                col++;
            }
            snippet[snippetLoc++] = ch;
            if (snippetLoc >= SNIPPET_LENGTH)
            {
                snippetLoc = 0;
            }
        }
        return ch;
    }

    public void unread(int c) throws IOException
    {
        if (idx == 0)
        {
            throw new JsonIoException("unread(int c) called more than buffer size (" + buf.length + ").  Increase FastPushbackReader's buffer size.  Currently " + buf.length);
        }
        if (c == 0x0a)
        {
            line--;
        }
        else
        {
            col--;
        }
        buf[--idx] = c;
        snippetLoc--;
        if (snippetLoc < 0)
        {
            snippetLoc = SNIPPET_LENGTH - 1;
        }
        snippet[snippetLoc] = c;
    }
}
