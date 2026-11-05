package cn.civer.authserver.repository;

import cn.civer.authserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	/**
	 * 邮箱唯一性校验：忽略大小写。写入已由 EmailPolicy 统一归一化为小写，
	 * 这里再兜一层，保证 V4 之前残留的大写邮箱也能被识别为「已占用」，
	 * 避免同一个邮箱因大小写不同被登记到两个账号上（库上另有精确唯一索引兜底）。
	 */
	Optional<User> findByEmailIgnoreCase(String email);

	/** 存量账号回填 public_id（V3 迁移只加列，UUID 由应用层生成以兼容 H2 与 PostgreSQL）。 */
	List<User> findByPublicIdIsNull();

	/**
	 * 统计「指定角色且已启用」的账号数。
	 *
	 * 用途单一但要紧：判断某个操作是否会消灭最后一个管理员。
	 * 管理台本身没有自助找回入口，最后一个管理员被停用/降级后只能改库恢复。
	 */
	long countByRoleAndEnabledTrue(String role);
}
