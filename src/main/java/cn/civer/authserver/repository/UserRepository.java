package cn.civer.authserver.repository;

import cn.civer.authserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	/**
	 * 统计「指定角色且已启用」的账号数。
	 *
	 * 用途单一但要紧：判断某个操作是否会消灭最后一个管理员。
	 * 管理台本身没有自助找回入口，最后一个管理员被停用/降级后只能改库恢复。
	 */
	long countByRoleAndEnabledTrue(String role);
}
