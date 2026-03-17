package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ifnodoraemon.nanojob.config.JobTypeTopologyValidator;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.handler.JobHandler;
import com.ifnodoraemon.nanojob.handler.JobHandlerRegistry;
import com.ifnodoraemon.nanojob.jobtype.JobTypeDefinition;
import com.ifnodoraemon.nanojob.jobtype.JobTypeDefinitionRegistry;
import com.ifnodoraemon.nanojob.jobtype.JobTypeDescriptor;
import com.ifnodoraemon.nanojob.retry.RetryDecision;
import com.ifnodoraemon.nanojob.retry.RetryPolicy;
import com.ifnodoraemon.nanojob.retry.RetryPolicyRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobTypeTopologyValidationTests {

    @Test
    void shouldRejectDuplicateHandlers() {
        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(
                new TestHandler(JobType.NOOP),
                new TestHandler(JobType.NOOP)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate handler");
    }

    @Test
    void shouldRejectMissingRetryPolicyCoverage() {
        JobHandlerRegistry handlers = new JobHandlerRegistry(List.of(
                new TestHandler(JobType.NOOP),
                new TestHandler(JobType.HTTP)
        ));
        RetryPolicyRegistry policies = new RetryPolicyRegistry(List.of(new TestPolicy(JobType.NOOP)));
        JobTypeDefinitionRegistry definitions = new JobTypeDefinitionRegistry(List.of(
                new TestDefinition(JobType.NOOP),
                new TestDefinition(JobType.HTTP)
        ));

        assertThatThrownBy(() -> new JobTypeTopologyValidator(handlers, policies, definitions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("coverage mismatch")
                .hasMessageContaining("HTTP");
    }

    @Test
    void shouldRejectMissingDefinitionCoverage() {
        JobHandlerRegistry handlers = new JobHandlerRegistry(List.of(
                new TestHandler(JobType.NOOP),
                new TestHandler(JobType.HTTP)
        ));
        RetryPolicyRegistry policies = new RetryPolicyRegistry(List.of(
                new TestPolicy(JobType.NOOP),
                new TestPolicy(JobType.HTTP)
        ));
        JobTypeDefinitionRegistry definitions = new JobTypeDefinitionRegistry(List.of(new TestDefinition(JobType.NOOP)));

        assertThatThrownBy(() -> new JobTypeTopologyValidator(handlers, policies, definitions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definition")
                .hasMessageContaining("HTTP");
    }

    @Test
    void shouldRejectDuplicateDefinitions() {
        assertThatThrownBy(() -> new JobTypeDefinitionRegistry(List.of(
                new TestDefinition(JobType.NOOP),
                new TestDefinition(JobType.NOOP)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate job type definition");
    }

    private record TestHandler(JobType type) implements JobHandler {
        @Override
        public JobType getType() {
            return type;
        }

        @Override
        public void handle(Job job) {
        }
    }

    private record TestPolicy(JobType type) implements RetryPolicy {
        @Override
        public JobType supports() {
            return type;
        }

        @Override
        public RetryDecision evaluate(Job job, Exception exception) {
            return new RetryDecision(false, job.getRetryCount() + 1, LocalDateTime.now(), exception.getMessage());
        }
    }

    private record TestDefinition(JobType type) implements JobTypeDefinition {
        @Override
        public JobType getType() {
            return type;
        }

        @Override
        public JobTypeDescriptor describe() {
            return new JobTypeDescriptor(type, "test", List.of(), List.of());
        }

        @Override
        public void validatePayload(JsonNode payload) {
        }
    }
}
