package com.cedarsoftware.util.io;

import lombok.Getter;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * This class adds significant performance increase over using the JDK
 * PushbackReader.  This is due to this class not using synchronization
 * as it is not needed.
 * <p>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <p>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class FastPushbackBufferedReader implements FastPushbackReader
{
    private final Reader in;
    private final char[] cb;
    private int nChars, nextChar;
    private int unread = Integer.MAX_VALUE;
    @Getter
    protected int line = 1;
    @Getter
    protected int col = 0;

    public FastPushbackBufferedReader(InputStream inputStream)
    {
        in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        cb = new char[1024];
        nextChar = nChars = 0;
    }

    public String getLastSnippet()
    {
        StringBuilder s = new StringBuilder();
        for (int i=Math.max(nextChar - 1, 0); i < nChars; i++)
        {
            s.append(cb[i]);
        }
        return s.toString();
    }
    
    public int read() throws IOException
    {
        int ch;
        if (unread == Integer.MAX_VALUE)
        {
            ch = realRead();
        }
        else
        {
            ch = unread;
            unread = Integer.MAX_VALUE;
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

    public void unread(int c)
    {
        if ((unread = c) == 0x0a)
        {
            line--;
        }
        else
        {
            col--;
        }
    }

    /**
     * Read bytes in from original stream and add to buffer
     */
    private void fill() throws IOException
    {
        int n;
        
        do
        {
            n = in.read(cb, 0, cb.length);
        } while (n == 0);

        if (n > 0)
        {
            nChars = n;
            nextChar = 0;
        }
    }

    /**
     * Read a single UTF-8 character.  Return -1 if EOF, otherwise the int value of the character.
     */
    public int realRead() throws IOException
    {
        while (true)
        {
            if (nextChar >= nChars)
            {
                fill();
                if (nextChar >= nChars)
                {
                    return -1;
                }
            }
            return cb[nextChar++];
        }
    }

    public int read(char[] cbuf, int off, int len) throws IOException
    {
        throw new UnsupportedEncodingException("This method not implemented.");
    }

    public void close() throws IOException
    {
        in.close();
    }
}
