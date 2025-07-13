import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.cedarsoftware.io.*;

public class test_aliases {
    public static void main(String[] args) {
        // Test Point alias
        Point point = new Point(10, 20);
        System.out.println("Point JSON: " + JsonIo.toJson(point, new WriteOptionsBuilder().build()));
        
        // Test File alias  
        File file = new File("/home/user/test.txt");
        System.out.println("File JSON: " + JsonIo.toJson(file, new WriteOptionsBuilder().build()));
        
        // Test Path alias
        Path path = Paths.get("/home/user/test.txt");
        System.out.println("Path JSON: " + JsonIo.toJson(path, new WriteOptionsBuilder().build()));
        
        // Test Color alias
        Color color = new Color(255, 128, 64);
        System.out.println("Color JSON: " + JsonIo.toJson(color, new WriteOptionsBuilder().build()));
    }
}