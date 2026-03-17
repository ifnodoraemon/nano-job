package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.entity.JobExecutionLog;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExecutionLogService {

    private final JobExecutionLogRepository jobExecutionLogRepository;

    public JobExecutionLogService(JobExecutionLogRepository jobExecutionLogRepository) {
        this.jobExecutionLogRepository = jobExecutionLogRepository;
    }

    @Transactional
    public JobExecutionLog start(Job job) {
        JobExecutionLog log = new JobExecutionLog();
        log.setJobId(job.getId());
        log.setAttemptNo(job.getRetryCount() + 1);
        log.setStatus(JobStatus.RUNNING);
        log.setStartedAt(LocalDateTime.now());
        return jobExecutionLogRepository.save(log);
    }

    @Transactional
    public void markSuccess(Long logId, String message) {
        jobExecutionLogRepository.finish(logId, JobStatus.SUCCESS, LocalDateTime.now(), message);
    }

    @Transactional
    public void markFailure(Long logId, String message) {
        jobExecutionLogRepository.finish(logId, JobStatus.FAILED, LocalDateTime.now(), message);
    }

    @Transactional
    public void markLatestRunningAsFailed(Long jobId, String message) {
        jobExecutionLogRepository.findFirstByJobIdAndStatusOrderByAttemptNoDesc(jobId, JobStatus.RUNNING)
                .ifPresent(log -> jobExecutionLogRepository.finish(
                        log.getId(),
                        JobStatus.FAILED,
                        LocalDateTime.now(),
                        message
                ));
    }
}
