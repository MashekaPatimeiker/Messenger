package chat;
import java.util.ArrayList;
import java.util.List;

public class UsersMainInfo {
    public static List<String> users =new ArrayList<>();
    public static int userCount = 0;

    public static void addUser(String username) {
        if (!users.contains(username)) {
            users.add(username);
            userCount++;
        } else {
            //System.out.println("User '" + username + "' already exists.");
        }
    }
    public static int getUserCount() {
        return userCount;
    }
    public static boolean userExists(String username) {
        return users.contains(username);
    }
    public static void removeUser(String username) {
        if (users.contains(username)) {
            users.remove(username);
            userCount--;
        } else {
          //  System.out.println("User '" + username + "' not found.");
        }
    }
}