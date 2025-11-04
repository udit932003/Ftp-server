
// Application.Properties mai agr kuch likha hota mtkb mene toh likhha h toh woh mani jayeggi
// port
//dir
//thread
//range
//agr mai waha ni likhata toh ye chlti
//or agr yaha ni likhta toh woh chlti
//or agr dono likhta toh bhi application he chlti


package com.ftpServer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ftp.server")// ye ni likhunga toh application.pro mai defualt 2121 port lega //
public class FtpServerConfig {



    private int port = 2121;
    private String rootDirectory = "ftp-root";
    private int maxThreads = 20;
    private int passivePortRangeStart = 50000;
    private int passivePortRangeEnd = 50100;

    // Getters and Setters
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getPassivePortRangeStart() {
        return passivePortRangeStart;
    }

    public void setPassivePortRangeStart(int passivePortRangeStart) {
        this.passivePortRangeStart = passivePortRangeStart;
    }

    public int getPassivePortRangeEnd() {
        return passivePortRangeEnd;
    }

    public void setPassivePortRangeEnd(int passivePortRangeEnd) {
        this.passivePortRangeEnd = passivePortRangeEnd;
    }
}
