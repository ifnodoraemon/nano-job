package com.ifnodoraemon.nanojob.controller;

import com.ifnodoraemon.nanojob.domain.dto.CancelJobResponse;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.dto.JobLogResponse;
import com.ifnodoraemon.nanojob.domain.dto.JobResponse;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(request);
    }

    @GetMapping("/{jobKey}")
    public JobResponse getJob(@PathVariable String jobKey) {
        return jobService.getJob(jobKey);
    }

    @GetMapping
    public Page<JobResponse> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobService.listJobs(status, type, PageRequest.of(page, size));
    }

    @PostMapping("/{jobKey}/cancel")
    public CancelJobResponse cancelJob(@PathVariable String jobKey) {
        return jobService.cancelJob(jobKey);
    }

    @GetMapping("/{jobKey}/logs")
    public List<JobLogResponse> getJobLogs(@PathVariable String jobKey) {
        return jobService.getJobLogs(jobKey);
    }
}
