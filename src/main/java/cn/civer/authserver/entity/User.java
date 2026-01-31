package cn.civer.authserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String username;

	@Column(nullable = false)
	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
	private String password;

	@Column(nullable = false)
	private String role; // e.g., ROLE_USER, ROLE_ADMIN

	@Column(nullable = false)
	private boolean enabled = true;

	public User(String username, String password, String role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}
}
