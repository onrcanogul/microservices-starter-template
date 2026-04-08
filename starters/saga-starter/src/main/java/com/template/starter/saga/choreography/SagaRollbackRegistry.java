package com.template.starter.saga.choreography;

import com.template.messaging.saga.SagaRollback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Scans the application context at startup for beans annotated with {@link SagaRollback}
 * and builds a compensation map: source event → rollback consumer(s).
 * <p>
 * This makes the {@code @SagaRollback} annotation a runtime concept, not just documentation.
 * Services can query this registry to find which consumers handle compensation for a given event type.
 * <p>
 * Handles Spring CGLIB proxies correctly via {@link AopUtils#getTargetClass} and
 * {@link AnnotationUtils#findAnnotation}.
 */
@Slf4j
public class SagaRollbackRegistry {

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, List<RollbackEntry>> rollbackMap = new LinkedHashMap<>();

    public SagaRollbackRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public record RollbackEntry(
            Class<?> sourceEvent,
            Class<?> sourceProcessor,
            Object rollbackBean,
            String beanName
    ) {}

    @PostConstruct
    public void scan() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(SagaRollback.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            SagaRollback annotation = AnnotationUtils.findAnnotation(targetClass, SagaRollback.class);

            if (annotation == null) {
                continue;
            }

            RollbackEntry rollbackEntry = new RollbackEntry(
                    annotation.source(),
                    annotation.sourcesProcessor(),
                    bean,
                    beanName
            );

            rollbackMap.computeIfAbsent(annotation.source(), k -> new ArrayList<>())
                    .add(rollbackEntry);

            log.info("Registered saga rollback: {} -> {} (processor: {})",
                    annotation.source().getSimpleName(),
                    beanName,
                    annotation.sourcesProcessor().getSimpleName());
        }

        log.info("SagaRollbackRegistry: {} source events mapped to {} rollback handlers",
                rollbackMap.size(),
                rollbackMap.values().stream().mapToInt(List::size).sum());
    }

    /**
     * Get all registered rollback handlers for a given source event type.
     *
     * @param sourceEvent the event class that triggers compensation
     * @return list of rollback entries, or empty list if none registered
     */
    public List<RollbackEntry> getRollbackHandlers(Class<?> sourceEvent) {
        return rollbackMap.getOrDefault(sourceEvent, List.of());
    }

    /**
     * Check if a source event has any rollback handlers registered.
     */
    public boolean hasRollbackFor(Class<?> sourceEvent) {
        return rollbackMap.containsKey(sourceEvent);
    }

    /**
     * Get all registered rollback mappings (for diagnostics / actuator).
     */
    public Map<Class<?>, List<RollbackEntry>> getAllMappings() {
        return Collections.unmodifiableMap(rollbackMap);
    }
}
