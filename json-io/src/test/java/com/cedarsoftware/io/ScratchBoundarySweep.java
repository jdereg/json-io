package com.cedarsoftware.io;
import com.cedarsoftware.util.FastReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class ScratchBoundarySweep {
    @Test
    void sweep() {
        int firstFail = -1, lastFail = -1, failCount = 0;
        java.util.List<Integer> fails = new java.util.ArrayList<>();
        for (int pad = 250; pad <= 8300; pad++) {
            StringBuilder run = new StringBuilder();
            for (int i = 0; i < pad; i++) run.append('x');
            String expected = run + "\"end";
            String json = "\"" + run + "\\\"end\"";
            boolean ok;
            try {
                CharStreamTokenizer t = new CharStreamTokenizer(new FastReader(new StringReader(json)),
                        false, true, false, false, false, false);
                ok = (t.nextToken() == JsonToken.VALUE_STRING) && expected.equals(t.getText());
            } catch (Throwable e) { ok = false; }
            if (!ok) { failCount++; if (firstFail<0) firstFail=pad; lastFail=pad; if (fails.size()<40) fails.add(pad); }
        }
        System.out.println("SWEEP failCount=" + failCount + " firstFail=" + firstFail + " lastFail=" + lastFail);
        System.out.println("SWEEP firstFails=" + fails);
    }
}
