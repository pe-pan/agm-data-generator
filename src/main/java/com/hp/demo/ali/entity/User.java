package com.hp.demo.ali.entity;

import com.hp.demo.ali.excel.AgmEntityIterator;

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
    private String firstName;
    private String lastName;
    private boolean portalUser;

    public User(String id, String login, String password, String firstName, String lastName, boolean portalUser) {
        this.id = id;
        this.login = login;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.portalUser = portalUser;
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

    public void setLogin(String login) {
        this.login = login;
        AgmEntityIterator.putReference("Users#" + id, login);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isPortalUser() {
        return portalUser;
    }

    static private Map<String, User> users = new HashMap<>();

    public static void addUser(User user) {
        users.put(user.getId(), user);
        AgmEntityIterator.putReference("Users#" + user.getId(), user.getLogin());
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
