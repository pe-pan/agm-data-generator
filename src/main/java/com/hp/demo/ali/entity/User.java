package com.hp.demo.ali.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 11/15/12.
 */
public class User {

    private String id;
    private String login;
    private String password;

    public User(String id, String login, String password) {
        this.id = id;
        this.login = login;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    static private Map<String, User> users = new HashMap<String, User>();

    public static void addUser(User user) {
        users.put(user.getId(), user);
    }

    public static User getUser(String userId) {
        return users.get(userId);
    }

    public static User[] getUsers() {
        User[] returnValue = new User[users.size()];
        Collection<User> col = users.values();
        int i = 0;
        for (User user : col) {
            returnValue[i++] = user;
        }
        return returnValue;
    }
}
