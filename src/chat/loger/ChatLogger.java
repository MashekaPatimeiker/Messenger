package chat.loger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLogger {
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String message) {
        String logEntry = String.format("[%s] %s", LocalDateTime.now().format(dtf), message);
        System.out.println(logEntry);
        // Здесь можно добавить запись в файл, если нужно
    }

    public static void logUserAction(String username, String action) {
        log(String.format("USER ACTION: %s - %s", username, action));
    }
}