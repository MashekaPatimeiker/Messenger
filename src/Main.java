import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            Path htmlPath = Paths.get("public/html/index.html");
            String htmlContent = Files.readString(htmlPath);
            new Server((req, resp) -> htmlContent).initserver();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load HTML file");
        }
    }
}