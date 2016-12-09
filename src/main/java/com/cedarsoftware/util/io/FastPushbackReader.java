package com.cedarsoftware.util.io;

import java.io.Closeable;
import java.io.IOException;

public interface FastPushbackReader extends Closeable {

    int getCol();

    int getLine();

    void unread(int c) throws IOException;

    int read() throws IOException;

    String getLastSnippet();
}
