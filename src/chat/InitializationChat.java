package chat;

import java.util.List;

import chat.loger.ChatLogger;
public class InitializationChat {
    static {
        ChatLogger.log("Chat system initialized");
    }

    public static void initializeUserSession(String username, String sessionId) {
        if (username == null || username.trim().isEmpty()) {
            String errorMsg = "Attempt to initialize session with empty username";
            ChatLogger.log(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        ChatLogger.logUserAction(username, "Attempting to enter chat");
        UsersListInfo.addUser(username, sessionId);
        ChatLogger.logUserAction(username, "Successfully entered chat");
    }

    public static void terminateUserSession(String username) {
        ChatLogger.logUserAction(username, "Attempting to leave chat");
        UsersListInfo.removeUser(username);
        ChatLogger.logUserAction(username, "Successfully left chat");
    }

    public static List<String> getActiveUsers() {
        ChatLogger.log("Retrieving active users list");
        return UsersListInfo.getActiveUsers();
    }

    public static boolean isUserActive(String username) {
        boolean active = UsersListInfo.userExists(username);
        ChatLogger.logUserAction(username, active ? "Checked presence (online)" : "Checked presence (offline)");
        return active;
    }
}