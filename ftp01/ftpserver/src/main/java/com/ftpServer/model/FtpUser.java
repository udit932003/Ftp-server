package com.ftpServer.model;

public class FtpUser {
    private String username;
    private String password;
    private String homeDirectory;
    private boolean canWrite;

    public FtpUser(String username, String password, String homeDirectory, boolean canWrite) {
        this.username = username;
        this.password = password;
        this.homeDirectory = homeDirectory;
        this.canWrite = canWrite;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public void setHomeDirectory(String homeDirectory) {
        this.homeDirectory = homeDirectory;
    }

    public boolean getCanWrite() {
        return canWrite;
    }

    public void setCanWrite(boolean canWrite) {
        this.canWrite = canWrite;
    }
}
