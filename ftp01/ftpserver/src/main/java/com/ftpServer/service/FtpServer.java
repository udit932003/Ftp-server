package com.ftpServer.service;


import com.ftpServer.service.FtpSessionHandler;
import com.ftpServer.config.FtpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class FtpServer {

    private static final Logger logger = LoggerFactory.getLogger(FtpServer.class);

    private final FtpServerConfig config;
    private final UserService userService;
    private final ExecutorService threadPool;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public FtpServer(FtpServerConfig config, UserService userService) {
        this.config = config;
        this.userService = userService;
        this.threadPool = Executors.newFixedThreadPool(config.getMaxThreads());
    }

    public void start() {
        try {
            // Create root directory if it doesn't exist
            Files.createDirectories(Paths.get(config.getRootDirectory()));

            // Start server socket
            serverSocket = new ServerSocket(config.getPort());
            running = true;

            logger.info("===========================================");
            logger.info("FTP Server started successfully");
            logger.info("Port: {}", config.getPort());
            logger.info("Root Directory: {}", config.getRootDirectory());
            logger.info("Max Threads: {}", config.getMaxThreads());
            logger.info("===========================================");

            // Accept connections in a loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(new FtpSessionHandler(clientSocket, userService, config));
                } catch (IOException e) {
                    if (running) {
                        logger.error("Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start FTP server", e);
        }
    }

    @PreDestroy
    public void stop() {
        logger.info("Shutting down FTP server...");
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            threadPool.shutdown();
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }

            logger.info("FTP server stopped");
        } catch (IOException | InterruptedException e) {
            logger.error("Error stopping FTP server", e);
            threadPool.shutdownNow();
        }
    }
}

