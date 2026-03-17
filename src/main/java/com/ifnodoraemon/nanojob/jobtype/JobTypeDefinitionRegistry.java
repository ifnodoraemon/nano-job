package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.RecordComponent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

@Component
public class JobTypeDefinitionRegistry {

    private static final String PAYLOAD_PACKAGE = "com.ifnodoraemon.nanojob.domain.payload";

    private final Map<JobType, JobTypeDefinition> definitions = new EnumMap<>(JobType.class);

    @Autowired
    public JobTypeDefinitionRegistry(JobPayloadMapper jobPayloadMapper) {
        this(discoverDefinitions(jobPayloadMapper));
    }

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

    private static List<JobTypeDefinition> discoverDefinitions(JobPayloadMapper jobPayloadMapper) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(JobPayloadSpec.class));

        return scanner.findCandidateComponents(PAYLOAD_PACKAGE).stream()
                .map(candidate -> loadPayloadClass(candidate.getBeanClassName()))
                .map(payloadClass -> toDefinition(payloadClass, jobPayloadMapper))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> JobTypeDefinition toDefinition(Class<?> payloadClass, JobPayloadMapper jobPayloadMapper) {
        if (!payloadClass.isRecord()) {
            throw new IllegalStateException("Job payload class must be a record: " + payloadClass.getName());
        }

        JobPayloadSpec spec = payloadClass.getAnnotation(JobPayloadSpec.class);
        if (spec == null) {
            throw new IllegalStateException("Missing @JobPayloadSpec on payload class: " + payloadClass.getName());
        }

        JobTypeDescriptor descriptor = new JobTypeDescriptor(
                spec.type(),
                spec.description(),
                requiredFields(payloadClass),
                optionalFields(payloadClass)
        );
        return new PayloadBackedJobTypeDefinition<>(
                spec.type(),
                (Class<T>) payloadClass,
                descriptor,
                jobPayloadMapper
        );
    }

    private static List<String> requiredFields(Class<?> payloadClass) {
        return recordComponents(payloadClass).stream()
                .filter(JobTypeDefinitionRegistry::isRequired)
                .map(RecordComponent::getName)
                .toList();
    }

    private static List<String> optionalFields(Class<?> payloadClass) {
        Set<String> required = new LinkedHashSet<>(requiredFields(payloadClass));
        return recordComponents(payloadClass).stream()
                .map(RecordComponent::getName)
                .filter(name -> !required.contains(name))
                .toList();
    }

    private static List<RecordComponent> recordComponents(Class<?> payloadClass) {
        return List.of(payloadClass.getRecordComponents());
    }

    private static boolean isRequired(RecordComponent component) {
        return hasRequiredConstraint(component)
                || hasRequiredConstraint(component.getAccessor())
                || hasRequiredConstraint(fieldOf(component));
    }

    private static boolean hasRequiredConstraint(java.lang.reflect.AnnotatedElement element) {
        return element != null && (element.isAnnotationPresent(NotNull.class)
                || element.isAnnotationPresent(NotBlank.class)
                || element.isAnnotationPresent(NotEmpty.class));
    }

    private static java.lang.reflect.Field fieldOf(RecordComponent component) {
        try {
            return component.getDeclaringRecord().getDeclaredField(component.getName());
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Missing backing field for record component: " + component.getName(), exception);
        }
    }

    private static Class<?> loadPayloadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to load payload class: " + className, exception);
        }
    }
}
