package com.ifnodoraemon.nanojob.repository;

import com.ifnodoraemon.nanojob.domain.entity.JobOutboxEvent;
import com.ifnodoraemon.nanojob.domain.enums.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobOutboxEventRepository extends JpaRepository<JobOutboxEvent, Long> {

    long countByStatus(OutboxStatus status);

    List<JobOutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            OutboxStatus status,
            LocalDateTime availableAt,
            Pageable pageable
    );

    List<JobOutboxEvent> findByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            OutboxStatus status,
            LocalDateTime updatedAt,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobOutboxEvent e
               set e.status = :publishedStatus,
                   e.publishedAt = :publishedAt,
                   e.lastError = null,
                   e.updatedAt = :updatedAt
             where e.id = :eventId
               and e.status = :expectedStatus
            """)
    int markPublished(
            @Param("eventId") Long eventId,
            @Param("expectedStatus") OutboxStatus expectedStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobOutboxEvent e
               set e.status = :processingStatus,
                   e.updatedAt = :updatedAt
             where e.id = :eventId
               and e.status = :expectedStatus
            """)
    int markProcessing(
            @Param("eventId") Long eventId,
            @Param("expectedStatus") OutboxStatus expectedStatus,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobOutboxEvent e
               set e.status = :processedStatus,
                   e.processedAt = :processedAt,
                   e.updatedAt = :updatedAt
             where e.id = :eventId
               and e.status = :expectedStatus
            """)
    int markProcessed(
            @Param("eventId") Long eventId,
            @Param("expectedStatus") OutboxStatus expectedStatus,
            @Param("processedStatus") OutboxStatus processedStatus,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobOutboxEvent e
               set e.status = :discardedStatus,
                   e.lastError = :lastError,
                   e.updatedAt = :updatedAt
             where e.id = :eventId
               and e.status in :expectedStatuses
            """)
    int markDiscarded(
            @Param("eventId") Long eventId,
            @Param("expectedStatuses") java.util.Collection<OutboxStatus> expectedStatuses,
            @Param("discardedStatus") OutboxStatus discardedStatus,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobOutboxEvent e
               set e.status = :pendingStatus,
                   e.publishAttemptCount = :publishAttemptCount,
                   e.availableAt = :availableAt,
                   e.publishedAt = null,
                   e.lastError = :lastError,
                   e.updatedAt = :updatedAt
             where e.id = :eventId
               and e.status = :expectedStatus
            """)
    int reschedulePending(
            @Param("eventId") Long eventId,
            @Param("expectedStatus") OutboxStatus expectedStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("publishAttemptCount") Integer publishAttemptCount,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
