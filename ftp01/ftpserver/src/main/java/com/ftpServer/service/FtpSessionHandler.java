package com.ftpServer.service;

import com.ftpServer.config.FtpServerConfig;
import com.ftpServer.model.FtpUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class FtpSessionHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FtpSessionHandler.class);

    private final Socket controlSocket;
    private final UserService userService;
    private final FtpServerConfig config;

    private BufferedReader reader;
    private PrintWriter writer;

    private String username;
    private FtpUser authenticatedUser;
    private boolean isAuthenticated = false;
    private final Path rootDir;
    private Path currentDir;

    private DataConnectionManager dataConnectionManager;
    private String transferType = "A"; // A=ASCII, I=Binary

    // Constructor
    public FtpSessionHandler(Socket controlSocket, UserService userService, FtpServerConfig config) {
        this.controlSocket = controlSocket;
        this.userService = userService;
        this.config = config;
        this.rootDir = Paths.get(config.getRootDirectory()).toAbsolutePath();
        this.currentDir = rootDir;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            writer = new PrintWriter(controlSocket.getOutputStream(), true);

            logger.info("New FTP connection from: {}", controlSocket.getRemoteSocketAddress());
            sendReply(220, "FTP Server Ready");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                logger.info("Command received: {}", line);
                handleCommand(line);

                if (controlSocket.isClosed()) break;
            }
        } catch (IOException e) {
            logger.error("Error in FTP session", e);
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String line) throws IOException {
        String[] parts = line.split(" ", 2);
        String command = parts[0].toUpperCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "USER":
                handleUser(argument);
                break;
            case "PASS":
                handlePass(argument);
                break;
            case "SYST":
                sendReply(215, "UNIX Type: L8");
                break;
            case "FEAT":
                handleFeat();
                break;
            case "PWD":
                handlePwd();
                break;
            case "CWD":
                handleCwd(argument);
                break;
            case "CDUP":
                handleCdup();
                break;
            case "TYPE":
                handleType(argument);
                break;
            case "PASV":
                handlePasv();
                break;
            case "LIST":
                handleList(argument);
                break;
            case "NLST":
                handleNlst(argument);
                break;
            case "RETR":
                handleRetr(argument);
                break;
            case "STOR":
                handleStor(argument);
                break;
            case "DELE":
                handleDele(argument);
                break;
            case "MKD":
                handleMkd(argument);
                break;
            case "RMD":
                handleRmd(argument);
                break;
            case "NOOP":
                sendReply(200, "OK");
                break;
            case "QUIT":
                handleQuit();
                break;
            case "EPSV":
                handleEpsv();
                break;

            // ADD THESE TWO CASES HERE:
            case "EPRT":
            case "PORT":
                sendReply(502, "Command not implemented. Please use PASV mode");
                break;
            default:
                sendReply(502, "Command not implemented: " + command);
                break;
                
        }
    }
    private void handleEpsv() throws IOException {
        if (!checkAuthentication()) return;

        // Create data server socket on random port
        ServerSocket dataServerSocket = new ServerSocket(0);
        int port = dataServerSocket.getLocalPort();

        // Create data connection manager
        dataConnectionManager = new DataConnectionManager(dataServerSocket);

        // EPSV response format: 229 Entering Extended Passive Mode (|||port|)
        sendReply(229, "Entering Extended Passive Mode (|||" + port + "|)");
        logger.info("Extended passive mode enabled on port: {}", port);
    }



    private void handleUser(String username) {
        this.username = username;
        if (userService.userExists(username)) {
            sendReply(331, "User name okay, need password");
        } else {
            sendReply(530, "User not found");
        }
    }

    private void handlePass(String password) {
        if (username == null) {
            sendReply(503, "Login with USER first");
            return;
        }

        Optional<FtpUser> user = userService.authenticate(username, password);
        if (user.isPresent()) {
            authenticatedUser = user.get();
            isAuthenticated = true;

            // Set user's home directory
            Path userHome = rootDir.resolve(authenticatedUser.getHomeDirectory().substring(1));
            try {
                Files.createDirectories(userHome);
                currentDir = userHome;
            } catch (IOException e) {
                logger.error("Failed to create user home directory", e);
            }

            sendReply(230, "User logged in, proceed");
            logger.info("User {} logged in successfully", username);
        } else {
            sendReply(530, "Login incorrect");
            logger.warn("Failed login attempt for user: {}", username);
        }
    }

    private void handleFeat() {
        sendReply(211, "Features:");
        writer.println(" PASV");
        writer.println(" SIZE");
        writer.println(" MDTM");
        sendReply(211, "End");
    }

    private void handlePwd() {
        if (!checkAuthentication()) return;

        String relativePath = rootDir.relativize(currentDir).toString();
        String ftpPath = "/" + relativePath.replace(File.separator, "/");
        if (ftpPath.equals("//")) ftpPath = "/";

        sendReply(257, "\"" + ftpPath + "\" is current directory");
    }

    private void handleCwd(String path) throws IOException {
        if (!checkAuthentication()) return;

        Path newPath = resolvePath(path);

        if (Files.exists(newPath) && Files.isDirectory(newPath) && isWithinRoot(newPath)) {
            currentDir = newPath;
            sendReply(250, "Directory changed to " + path);
        } else {
            sendReply(550, "Failed to change directory");
        }
    }

    private void handleCdup() throws IOException {
        if (!checkAuthentication()) return;
        handleCwd("..");
    }

    private void handleType(String type) {
        if (!checkAuthentication()) return;

        String upperType = type.toUpperCase();
        if (upperType.equals("A") || upperType.equals("I")) {
            transferType = upperType;
            sendReply(200, "Type set to " + upperType);
        } else {
            sendReply(504, "Type not supported");
        }
    }

    private void handlePasv() throws IOException {
        if (!checkAuthentication()) return;

        // Create data server socket on random port
        ServerSocket dataServerSocket = new ServerSocket(0);
        int port = dataServerSocket.getLocalPort();

        // Get server address
        InetAddress addr = controlSocket.getLocalAddress();
        String ip = addr.getHostAddress().replace('.', ',');

        // Calculate port parts
        int p1 = port / 256;
        int p2 = port % 256;

        // Create data connection manager
        dataConnectionManager = new DataConnectionManager(dataServerSocket);

        sendReply(227, "Entering Passive Mode (" + ip + "," + p1 + "," + p2 + ")");
        logger.info("Passive mode enabled on port: {}", port);
    }

    private void handleList(String path) throws IOException {
        if (!checkAuthentication()) return;
        if (dataConnectionManager == null) {
            sendReply(425, "Use PASV first");
            return;
        }

        Path listPath = path.isEmpty() ? currentDir : resolvePath(path);

        if (!Files.exists(listPath) || !isWithinRoot(listPath)) {
            sendReply(550, "Directory not found");
            return;
        }

        sendReply(150, "Opening data connection for directory list");

        try {
            dataConnectionManager.waitForConnection(30000);
            StringBuilder listing = new StringBuilder();

            Files.list(listPath).forEach(file -> {
                try {
                    listing.append(formatListEntry(file)).append("\r\n");
                } catch (IOException e) {
                    logger.error("Error formatting list entry", e);
                }
            });

            dataConnectionManager.sendDirectoryListing(listing.toString());
            sendReply(226, "Transfer complete");
        } catch (IOException e) {
            logger.error("Error sending directory listing", e);
            sendReply(426, "Transfer failed");
        }
    }

    private void handleNlst(String path) throws IOException {
        if (!checkAuthentication()) return;
        if (dataConnectionManager == null) {
            sendReply(425, "Use PASV first");
            return;
        }

        Path listPath = path.isEmpty() ? currentDir : resolvePath(path);

        if (!Files.exists(listPath) || !isWithinRoot(listPath)) {
            sendReply(550, "Directory not found");
            return;
        }

        sendReply(150, "Opening data connection for name list");

        try {
            dataConnectionManager.waitForConnection(30000);
            StringBuilder listing = new StringBuilder();

            Files.list(listPath).forEach(file -> {
                listing.append(file.getFileName().toString()).append("\r\n");
            });

            dataConnectionManager.sendDirectoryListing(listing.toString());
            sendReply(226, "Transfer complete");
        } catch (IOException e) {
            logger.error("Error sending name listing", e);
            sendReply(426, "Transfer failed");
        }
    }

    private void handleRetr(String filename) throws IOException {
        if (!checkAuthentication()) return;
        if (dataConnectionManager == null) {
            sendReply(425, "Use PASV first");
            return;
        }

        Path file = resolvePath(filename);

        if (!Files.exists(file) || !isWithinRoot(file)) {
            sendReply(550, "File not found");
            return;
        }

        if (Files.isDirectory(file)) {
            sendReply(550, "Is a directory");
            return;
        }

        sendReply(150, "Opening data connection for " + filename);

        try {
            dataConnectionManager.waitForConnection(30000);
            dataConnectionManager.sendFile(file);
            sendReply(226, "Transfer complete");
        } catch (IOException e) {
            logger.error("Error retrieving file", e);
            sendReply(426, "Transfer failed");
        }
    }

    private void handleStor(String filename) throws IOException {
        if (!checkAuthentication()) return;

        if (!authenticatedUser.getCanWrite()) {
            sendReply(550, "Permission denied");
            return;
        }

        if (dataConnectionManager == null) {
            sendReply(425, "Use PASV first");
            return;
        }

        Path file = resolvePath(filename);

        if (!isWithinRoot(file)) {
            sendReply(550, "Access denied");
            return;
        }

        sendReply(150, "Opening data connection for " + filename);

        try {
            dataConnectionManager.waitForConnection(30000);
            dataConnectionManager.receiveFile(file);
            sendReply(226, "Transfer complete");
        } catch (IOException e) {
            logger.error("Error storing file", e);
            sendReply(426, "Transfer failed");
        }
    }

    private void handleDele(String filename) throws IOException {
        if (!checkAuthentication()) return;

        if (!authenticatedUser.getCanWrite()) {
            sendReply(550, "Permission denied");
            return;
        }

        Path file = resolvePath(filename);

        if (!Files.exists(file) || !isWithinRoot(file)) {
            sendReply(550, "File not found");
            return;
        }

        if (Files.isDirectory(file)) {
            sendReply(550, "Is a directory");
            return;
        }

        Files.delete(file);
        sendReply(250, "File deleted");
    }

    private void handleMkd(String dirname) throws IOException {
        if (!checkAuthentication()) return;

        if (!authenticatedUser.getCanWrite()) {
            sendReply(550, "Permission denied");
            return;
        }

        Path dir = resolvePath(dirname);

        if (!isWithinRoot(dir)) {
            sendReply(550, "Access denied");
            return;
        }

        Files.createDirectory(dir);
        sendReply(257, "\"" + dirname + "\" directory created");
    }

    private void handleRmd(String dirname) throws IOException {
        if (!checkAuthentication()) return;

        if (!authenticatedUser.getCanWrite()) {
            sendReply(550, "Permission denied");
            return;
        }

        Path dir = resolvePath(dirname);

        if (!Files.exists(dir) || !isWithinRoot(dir)) {
            sendReply(550, "Directory not found");
            return;
        }

        if (!Files.isDirectory(dir)) {
            sendReply(550, "Not a directory");
            return;
        }

        Files.delete(dir);
        sendReply(250, "Directory removed");
    }

    private void handleQuit() {
        sendReply(221, "Goodbye");
        cleanup();
    }

    private boolean checkAuthentication() {
        if (!isAuthenticated) {
            sendReply(530, "Not logged in");
            return false;
        }
        return true;
    }

    private Path resolvePath(String path) {
        if (path.startsWith("/")) {
            return rootDir.resolve(path.substring(1)).normalize();
        } else {
            return currentDir.resolve(path).normalize();
        }
    }

    private boolean isWithinRoot(Path path) {
        return path.normalize().startsWith(rootDir);
    }

    private String formatListEntry(Path file) throws IOException {
        File f = file.toFile();
        StringBuilder sb = new StringBuilder();

        // Permissions
        sb.append(f.isDirectory() ? "d" : "-");
        sb.append(f.canRead() ? "r" : "-");
        sb.append(f.canWrite() ? "w" : "-");
        sb.append(f.canExecute() ? "x" : "-");
        sb.append("r-xr-x ");

        // Links
        sb.append("1 ");

        // Owner and group
        sb.append("ftp ftp ");

        // Size
        sb.append(String.format("%8d ", f.length()));

        // Date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd HH:mm");
        String date = formatter.format(
                Files.getLastModifiedTime(file).toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        );
        sb.append(date).append(" ");

        // Filename
        sb.append(f.getName());

        return sb.toString();
    }

    private void sendReply(int code, String message) {
        String reply = code + " " + message;
        writer.println(reply);
        writer.flush();
        logger.debug("Reply sent: {}", reply);
    }

    private void cleanup() {
        try {
            if (dataConnectionManager != null) {
                dataConnectionManager.close();
            }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
            logger.info("FTP session closed for user: {}", username);
        } catch (IOException e) {
            logger.error("Error during cleanup", e);
        }
    }
}
