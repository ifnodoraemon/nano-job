package com.ifnodoraemon.nanojob.repository;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByJobKey(String jobKey);

    Optional<Job> findFirstByDedupKeyAndStatusInOrderByCreatedAtDesc(String dedupKey, Set<JobStatus> statuses);

    Optional<Job> findFirstByDedupKeyAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String dedupKey,
            LocalDateTime createdAt
    );

    long countByStatus(JobStatus status);

    java.util.List<Job> findTop100ByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(
            JobStatus status,
            LocalDateTime executeAt
    );

    java.util.List<Job> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            JobStatus status,
            LocalDateTime nextRetryAt
    );

    java.util.List<Job> findTop100ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAsc(
            JobStatus status,
            LocalDateTime leaseExpiresAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :runningStatus,
                   j.lastError = null,
                   j.nextRetryAt = null,
                   j.lockOwner = :lockOwner,
                   j.leaseExpiresAt = :leaseExpiresAt,
                   j.updatedAt = :updatedAt
             where j.id = :jobId
               and j.status in :claimableStatuses
            """)
    int claimForExecution(
            @Param("jobId") Long jobId,
            @Param("claimableStatuses") Collection<JobStatus> claimableStatuses,
            @Param("runningStatus") JobStatus runningStatus,
            @Param("lockOwner") String lockOwner,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :successStatus,
                   j.lockOwner = null,
                   j.leaseExpiresAt = null,
                   j.updatedAt = :updatedAt
             where j.id = :jobId
               and j.status = :expectedStatus
            """)
    int markSuccess(
            @Param("jobId") Long jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("successStatus") JobStatus successStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :retryWaitStatus,
                   j.retryCount = :retryCount,
                   j.nextRetryAt = :nextRetryAt,
                   j.lockOwner = null,
                   j.leaseExpiresAt = null,
                   j.lastError = :lastError,
                   j.updatedAt = :updatedAt
             where j.id = :jobId
               and j.status = :expectedStatus
            """)
    int markRetryWaiting(
            @Param("jobId") Long jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("retryWaitStatus") JobStatus retryWaitStatus,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :failedStatus,
                   j.retryCount = :retryCount,
                   j.lockOwner = null,
                   j.leaseExpiresAt = null,
                   j.lastError = :lastError,
                   j.updatedAt = :updatedAt
             where j.id = :jobId
               and j.status = :expectedStatus
            """)
    int markFailed(
            @Param("jobId") Long jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("failedStatus") JobStatus failedStatus,
            @Param("retryCount") Integer retryCount,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
