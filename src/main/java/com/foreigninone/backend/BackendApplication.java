package com.foreigninone.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@EnableScheduling
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(BackendApplication.class, args);
	}

	private static void loadDotEnv() {
		List<Path> candidatePaths = List.of(
				Paths.get(".env"),
				Paths.get("Backend/.env"),
				Paths.get("../.env"),
				Paths.get("../../.env")
		);

		Path envPath = null;
		for (Path p : candidatePaths) {
			if (Files.exists(p) && !Files.isDirectory(p)) {
				envPath = p;
				break;
			}
		}

		if (envPath != null) {
			System.out.println("[BackendApplication] Loading environment variables from: " + envPath.toAbsolutePath());
			try {
				List<String> lines = Files.readAllLines(envPath);
				for (String line : lines) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
						continue;
					}
					int idx = line.indexOf('=');
					String key = line.substring(0, idx).trim();
					String value = line.substring(idx + 1).trim();
					if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
						value = value.substring(1, value.length() - 1);
					}
					if (System.getProperty(key) == null && System.getenv(key) == null) {
						System.setProperty(key, value);
					}
				}
			} catch (Exception e) {
				System.err.println("[BackendApplication] Failed to load .env: " + e.getMessage());
			}
		} else {
			System.out.println("[BackendApplication] No .env file found in candidate paths.");
		}
	}

}
