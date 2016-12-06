package com.cedarsoftware.util.io;

import java.io.IOException;

/**
 * This class adds significant performance increase over using the JDK PushbackReader. This is due to this class not using
 * synchronization as it is not needed.
 */
public class FastPushbackBytesReader implements FastPushbackReader {
    private int idxSnippet = 0;

    protected int line = 1;

    protected int col = 0;

    private byte[] bytes;

    private int indexBytes = 0;

    FastPushbackBytesReader(byte[] bytes)
    {
        this.bytes = bytes;
    }

    public String getLastSnippet()
    {
        StringBuilder s = new StringBuilder();
        for (int i = idxSnippet; i < 256; i++) {
            if (appendChar(s, i)) {
                break;
            }
        }
        for (int i = 0; i < idxSnippet; i++) {
            if (appendChar(s, i)) {
                break;
            }
        }
        return s.toString();
    }

    private boolean appendChar(StringBuilder s, int i)
    {
        try {
            if (i >= bytes.length) {
                return true;
            }
            final int snip = bytes[i];
            if (snip == 0) {
                return true;
            }
            s.appendCodePoint(snip);
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    public int read() throws IOException
    {
        if (indexBytes >= bytes.length) {
            return -1;
        }
        int ch = bytes[indexBytes++];

        col++;
        if (ch == 0x0a) {
            line++;
            col = 0;
        }
        idxSnippet++;
        return ch;
    }

    @Override
    public int getCol()
    {
        return col;
    }

    @Override
    public int getLine()
    {
        return col;
    }

    public void unread(int c) throws IOException
    {
        indexBytes--;
        if (c == 0x0a) {
            line--;
        } else {
            col--;
        }

        idxSnippet--;
    }

    @Override
    public void close() throws IOException
    {
    }
}
