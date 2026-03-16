package com.ifnodoraemon.nanojob.repository;

import com.ifnodoraemon.nanojob.domain.entity.JobExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {

    List<JobExecutionLog> findByJobIdOrderByAttemptNoAsc(Long jobId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobExecutionLog log
               set log.status = :status,
                   log.finishedAt = :finishedAt,
                   log.message = :message
             where log.id = :logId
            """)
    int finish(
            @Param("logId") Long logId,
            @Param("status") com.ifnodoraemon.nanojob.domain.enums.JobStatus status,
            @Param("finishedAt") java.time.LocalDateTime finishedAt,
            @Param("message") String message
    );
}
