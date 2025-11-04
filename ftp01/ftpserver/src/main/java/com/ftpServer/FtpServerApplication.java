package com.ftpServer;
import com.ftpServer.service.FtpServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FtpServerApplication {
	public static void main(String[] args) {
		SpringApplication.run(FtpServerApplication.class, args);
	}
	@Bean
	public CommandLineRunner startFtpServer(FtpServer ftpServer) {
		return args -> {
			// Start FTP server in a separate thread
			new Thread(() -> ftpServer.start()).start();
		};
	}
}
