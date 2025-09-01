package chat;

import chat.loger.ChatLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UsersListInfo {
    private static ConcurrentHashMap<String, UserSession> activeUsers = new ConcurrentHashMap<>();
    private static int userCount = 0;

    public static class UserSession {
        private String username;
        private String sessionId;
        private long lastActivity;

        public UserSession(String username, String sessionId) {
            this.username = username;
            this.sessionId = sessionId;
            this.lastActivity = System.currentTimeMillis();
            ChatLogger.logUserAction(username, "New session created");
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
            ChatLogger.logUserAction(username, "Session activity updated");
        }
    }
    public synchronized static void addUser(String username, String sessionId) {
        if (!activeUsers.containsKey(username)) {
            activeUsers.put(username, new UserSession(username, sessionId));
            userCount++;
            ChatLogger.logUserAction(username, "Added to active users. Total users now: " + userCount);
        } else {
            ChatLogger.logUserAction(username, "Existing user session updated");
            activeUsers.get(username).updateActivity();
        }
    }

    public synchronized static void removeUser(String username) {
        if (activeUsers.containsKey(username)) {
            activeUsers.remove(username);
            userCount--;
            ChatLogger.logUserAction(username, "Removed from active users. Total users now: " + userCount);
        } else {
            ChatLogger.logUserAction(username, "Attempt to remove non-existent user");
        }
    }

    public static boolean userExists(String username) {
        return activeUsers.containsKey(username);
    }

    public static List<String> getActiveUsers() {
        return new ArrayList<>(activeUsers.keySet());
    }

    public static int getUserCount() {
        return userCount;
    }

    public static UserSession getUserSession(String username) {
        return activeUsers.get(username);
    }
}