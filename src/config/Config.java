package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private static final Map<String, String> config = new HashMap<>();

    public static void load(String path) throws IOException {
        Path configPath = Path.of(path);
        if (Files.exists(configPath)) {
            Files.lines(configPath).forEach(line -> {
                if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        config.put(parts[0].trim(), parts[1].trim());
                    }
                }
            });
        }
    }

    public static String get(String key) {
        return config.get(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(config.get(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(config.get(key));
    }
}