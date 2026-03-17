package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobPayloadSpec {

    JobType type();

    String description();
}
