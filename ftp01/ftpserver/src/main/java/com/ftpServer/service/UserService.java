package com.ftpServer.service;

import com.ftpServer.model.FtpUser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final Map<String, FtpUser> users = new HashMap<>();

    public UserService() {
        // Initialize default users
        users.put("admin", new FtpUser("admin", "admin123", "/", true));
        users.put("user1", new FtpUser("user1", "pass1", "/user1", false));
        users.put("guest", new FtpUser("guest", "guest", "/guest", true));
    }

    public Optional<FtpUser> authenticate(String username, String password) {
        FtpUser user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public boolean userExists(String username) {

        return users.containsKey(username);
    }

    public void addUser(FtpUser user) {

        users.put(user.getUsername(), user);
    }
}
