package com.ifnodoraemon.nanojob.retry;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;

public interface RetryPolicy {

    JobType supports();

    RetryDecision evaluate(Job job, Exception exception);
}
