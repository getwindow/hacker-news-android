package io.dwak.holohackernews.app.models;

import com.orm.SugarRecord;

public class User extends SugarRecord<User>{
    private String userName;
    private String userCookie;
    private boolean isCurrentUser;

    public User() {
    }

    public User(String userName, String userCookie, boolean isCurrentUser) {
        this.userName = userName;
        this.userCookie = userCookie;
        this.isCurrentUser = isCurrentUser;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserCookie() {
        return userCookie;
    }

    public void setUserCookie(String userCookie) {
        this.userCookie = userCookie;
    }

    public boolean isCurrentUser() {
        return isCurrentUser;
    }

    public void setIsCurrentUser(boolean isCurrentUser) {
        this.isCurrentUser = isCurrentUser;
    }

    public static boolean isLoggedIn() {
        return count(User.class, null, null) > 0;
    }

}
