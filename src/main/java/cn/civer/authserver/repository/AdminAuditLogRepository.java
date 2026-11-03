package cn.civer.authserver.repository;

import cn.civer.authserver.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

	/** 审计页只需要倒序分页，不需要按条件查询（暂不提供筛选，避免过早设计） */
	Page<AdminAuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
