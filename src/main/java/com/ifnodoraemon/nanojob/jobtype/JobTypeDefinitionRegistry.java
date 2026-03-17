package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobTypeDefinitionRegistry {

    private final Map<JobType, JobTypeDefinition> definitions = new EnumMap<>(JobType.class);

    public JobTypeDefinitionRegistry(List<JobTypeDefinition> definitions) {
        definitions.forEach(definition -> {
            JobTypeDefinition previous = this.definitions.put(definition.getType(), definition);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate job type definition registered for type: " + definition.getType()
                );
            }
        });
    }

    public JobTypeDefinition get(JobType type) {
        JobTypeDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("No job type definition registered for type: " + type);
        }
        return definition;
    }

    public Set<JobType> supportedTypes() {
        EnumSet<JobType> supported = EnumSet.noneOf(JobType.class);
        supported.addAll(definitions.keySet());
        return supported;
    }

    public List<JobTypeDescriptor> describeAll() {
        return definitions.values().stream()
                .map(JobTypeDefinition::describe)
                .sorted((left, right) -> left.type().name().compareTo(right.type().name()))
                .toList();
    }
}
