package com.ftpServer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(DataConnectionManager.class);

    private final ServerSocket dataServerSocket;
    private Socket dataSocket;

    public DataConnectionManager(ServerSocket dataServerSocket) {
        this.dataServerSocket = dataServerSocket;
    }

    public void waitForConnection(int timeoutMs) throws IOException {
        logger.info("Waiting for data connection on port: {}", dataServerSocket.getLocalPort());

        dataServerSocket.setSoTimeout(timeoutMs);
        try {
            dataSocket = dataServerSocket.accept();
            logger.info("Data connection accepted from: {}", dataSocket.getRemoteSocketAddress());
        } catch (SocketTimeoutException e) {
            logger.error("Data connection timeout after {}ms", timeoutMs);
            throw new IOException("Data connection timeout", e);
        }
    }

    public void sendDirectoryListing(String listing) throws IOException {
        if (dataSocket == null || dataSocket.isClosed()) {
            throw new IOException("Data socket not connected");
        }

        try (OutputStream out = dataSocket.getOutputStream()) {
            out.write(listing.getBytes());
            out.flush();
            logger.info("Directory listing sent: {} bytes", listing.length());
        } finally {
            close();
        }
    }

    public void sendFile(Path filePath) throws IOException {
        if (dataSocket == null || dataSocket.isClosed()) {
            throw new IOException("Data socket not connected");
        }

        try (OutputStream out = dataSocket.getOutputStream();
             InputStream in = Files.newInputStream(filePath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            out.flush();
            logger.info("File sent: {} bytes", totalBytes);
        } finally {
            close();
        }
    }

    public void receiveFile(Path filePath) throws IOException {
        if (dataSocket == null || dataSocket.isClosed()) {
            throw new IOException("Data socket not connected");
        }

        try (InputStream in = dataSocket.getInputStream();
             OutputStream out = Files.newOutputStream(filePath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            out.flush();
            logger.info("File received: {} bytes", totalBytes);
        } finally {
            close();
        }
    }

    public void close() {
        try {
            if (dataSocket != null && !dataSocket.isClosed()) {
                dataSocket.close();
                logger.debug("Data socket closed");
            }
            if (dataServerSocket != null && !dataServerSocket.isClosed()) {
                dataServerSocket.close();
                logger.debug("Data server socket closed");
            }
        } catch (IOException e) {
            logger.error("Error closing data connection", e);
        }
    }
}
