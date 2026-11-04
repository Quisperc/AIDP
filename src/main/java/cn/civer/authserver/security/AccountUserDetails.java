package cn.civer.authserver.security;

import cn.civer.authserver.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 表单登录的身份主体，携带账号资料与稳定主体标识。
 *
 * 约定（经 OidcLogoutAuthenticationProvider 源码核对）：
 * - getUsername() 返回可变的登录名，Spring Security 用它作为 Authentication.getName()，
 *   进而作为内部 principal_name、会话主体名、审计 actor 与登录限流的 key。
 *   保持这个名字可读，页面 / 审计 / 限流都不需要改语义。
 * - publicId 是 OIDC 协议层的 sub，由 TokenConfig 覆写进 ID Token。
 *   OIDC Core 要求 sub 永不重新分配，因此它必须是不可变值，不能是 username。
 *   客户端按 (iss, sub) 关联账号，改名后不会失配。
 * - RP-Initiated Logout 校验的是「当前 principal name == 授权时保存的 principal name」，
 *   并不比较 id_token.sub，所以两者不同不会破坏 /connect/logout。
 *
 * 改名时 AccountService 会按旧登录名强制全局登出，清掉以旧名挂着的
 * authorization / consent，避免内部 principal_name 可变带来的残留。
 */
public class AccountUserDetails implements UserDetails {

	private final String publicId;
	private final String loginName;
	private final String displayName;
	private final String email;
	private final boolean emailVerified;
	private final String password;
	private final boolean enabled;
	private final Collection<? extends GrantedAuthority> authorities;

	public AccountUserDetails(User user) {
		this.publicId = user.getPublicId();
		this.loginName = user.getUsername();
		String nickname = user.getNickname();
		this.displayName = (nickname == null || nickname.isBlank()) ? user.getUsername() : nickname;
		this.email = user.getEmail();
		this.emailVerified = user.isEmailVerified();
		this.password = user.getPassword();
		this.enabled = user.isEnabled();
		this.authorities = List.of(new SimpleGrantedAuthority(user.getRole()));
	}

	/** 登录名（可变），同时是内部 principal_name。 */
	@Override
	public String getUsername() {
		return loginName;
	}

	/** 不可变主体标识，仅用于 OIDC 协议层的 sub。 */
	public String getPublicId() {
		return publicId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}
}
