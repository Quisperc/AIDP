package cn.civer.authserver;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Scanner;
import java.util.UUID;

public class ClientSqlGenerator {

	/**
	 * Run this main method in your IDE to generate SQL for a new client.
	 */
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

		System.out.println("=== OAuth2 Client SQL Generator ===");
		System.out.println("This script will generate the SQL INSERT statement for your PostgreSQL database.");

		// 1. Client ID
		System.out.print("Enter Client ID (e.g., order-app): ");
		String clientId = scanner.nextLine().trim();
		if (clientId.isEmpty()) {
			System.out.println("Client ID cannot be empty.");
			return;
		}

		// 2. Client Secret
		System.out.print("Enter Client Secret (plain text, e.g., secret): ");
		String clientSecret = scanner.nextLine().trim();
		if (clientSecret.isEmpty()) {
			System.out.println("Client Secret cannot be empty.");
			return;
		}

		// 3. App Port (Shortcut)
		System.out.print("Enter App Port (e.g., 8082, or press Enter to type full URLs manually): ");
		String portStr = scanner.nextLine().trim();

		String redirectUri;
		String logoutUri;

		if (!portStr.isEmpty()) {
			redirectUri = "http://127.0.0.1:" + portStr + "/login/oauth2/code/oidc-client";
			logoutUri = "http://127.0.0.1:8080/login"; // Default to Auth Server login
			System.out.println("Generated Redirect URI: " + redirectUri);
			System.out.println("Generated Logout Redirect URI: " + logoutUri);
		} else {
			System.out.print("Enter Redirect URI (e.g., http://host:port/login/oauth2/code/oidc-client): ");
			redirectUri = scanner.nextLine().trim();

			System.out.print("Enter Post Logout Redirect URI (e.g., http://127.0.0.1:8080/login): ");
			logoutUri = scanner.nextLine().trim();
		}

		// Generate Data
		String id = UUID.randomUUID().toString();
		String hashedSecret = passwordEncoder.encode(clientSecret);

		// Settings JSON (Standard defaults)
		String clientSettings = "{\"@class\":\"java.util.Collections$UnmodifiableMap\",\"settings.client.require-authorization-consent\":true,\"settings.client.require-proof-key\":false}";
		String tokenSettings = "{\"@class\":\"java.util.Collections$UnmodifiableMap\",\"settings.token.access-token-time-to-live\":[\"java.time.Duration\",1800.000000000]}";

		System.out.println("\n\n=== COPY THE SQL BELOW TO YOUR DATABASE TOOL ===");
		System.out.println("--------------------------------------------------");

		String sql = String.format("""
				INSERT INTO oauth2_registered_client (
				    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
				    client_name, client_authentication_methods, authorization_grant_types,
				    redirect_uris, post_logout_redirect_uris, scopes, client_settings, token_settings
				) VALUES (
				    '%s', '%s', NOW(), '%s', NULL,
				    '%s', 'client_secret_basic', 'authorization_code,refresh_token',
				    '%s', '%s', 'openid,profile',
				    '%s', '%s'
				);
				""",
				id, clientId, hashedSecret, clientId,
				redirectUri, logoutUri,
				clientSettings, tokenSettings);

		System.out.println(sql);
		System.out.println("--------------------------------------------------");
	}
}
