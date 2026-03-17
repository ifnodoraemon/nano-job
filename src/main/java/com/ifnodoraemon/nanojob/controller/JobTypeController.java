package com.ifnodoraemon.nanojob.controller;

import com.ifnodoraemon.nanojob.domain.dto.JobTypeDefinitionResponse;
import com.ifnodoraemon.nanojob.service.JobTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-types")
public class JobTypeController {

    private final JobTypeService jobTypeService;

    public JobTypeController(JobTypeService jobTypeService) {
        this.jobTypeService = jobTypeService;
    }

    @GetMapping
    public List<JobTypeDefinitionResponse> listJobTypes() {
        return jobTypeService.listJobTypes();
    }
}
