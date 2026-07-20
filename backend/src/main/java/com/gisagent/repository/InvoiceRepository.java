package com.gisagent.repository;

import com.gisagent.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByOrganizationIdOrderByPeriodMonthDesc(Long organizationId);
    Optional<Invoice> findByOrganizationIdAndPeriodMonth(Long organizationId, String periodMonth);
    List<Invoice> findByPeriodMonth(String periodMonth);

    /** 重新生成某账期账单时，先清除该组织旧数据（幂等覆盖） */
    @Modifying
    @Transactional
    @Query("DELETE FROM Invoice i WHERE i.organizationId = :orgId AND i.periodMonth = :month")
    void deleteByOrganizationAndMonth(@Param("orgId") Long organizationId, @Param("month") String periodMonth);

    /** 全平台重新生成时，清空整个账期（超管一次性重建） */
    @Modifying
    @Transactional
    @Query("DELETE FROM Invoice i WHERE i.periodMonth = :month")
    void deleteByMonth(@Param("month") String periodMonth);
}
