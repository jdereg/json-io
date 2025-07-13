import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.ReadOptionsBuilder;

class BufferTest {
    public static void main(String[] args) {
        // Test ByteBuffer serialization
        ByteBuffer bb = ByteBuffer.allocate(10);
        bb.put(new byte[]{1, 2, 3, 4, 5});
        bb.flip();
        
        System.out.println("Original ByteBuffer position: " + bb.position());
        System.out.println("Original ByteBuffer limit: " + bb.limit());
        System.out.println("Original ByteBuffer remaining: " + bb.remaining());
        
        String json = JsonIo.toJson(bb, new WriteOptionsBuilder().build());
        System.out.println("ByteBuffer JSON: " + json);
        
        Object bb2Obj = JsonIo.toJava(json, new ReadOptionsBuilder().build());
        ByteBuffer bb2 = (ByteBuffer) bb2Obj;
        System.out.println("Deserialized ByteBuffer position: " + bb2.position());
        System.out.println("Deserialized ByteBuffer limit: " + bb2.limit());
        System.out.println("Deserialized ByteBuffer remaining: " + bb2.remaining());
        
        // Test CharBuffer serialization
        CharBuffer cb = CharBuffer.allocate(10);
        cb.put("hello");
        cb.flip();
        
        System.out.println("\nOriginal CharBuffer position: " + cb.position());
        System.out.println("Original CharBuffer limit: " + cb.limit());
        System.out.println("Original CharBuffer remaining: " + cb.remaining());
        
        String json2 = JsonIo.toJson(cb, new WriteOptionsBuilder().build());
        System.out.println("CharBuffer JSON: " + json2);
        
        Object cb2Obj = JsonIo.toJava(json2, new ReadOptionsBuilder().build());
        CharBuffer cb2 = (CharBuffer) cb2Obj;
        System.out.println("Deserialized CharBuffer position: " + cb2.position());
        System.out.println("Deserialized CharBuffer limit: " + cb2.limit());
        System.out.println("Deserialized CharBuffer remaining: " + cb2.remaining());
        
        // Print content
        System.out.println("CharBuffer content: " + cb2.toString());
    }
}