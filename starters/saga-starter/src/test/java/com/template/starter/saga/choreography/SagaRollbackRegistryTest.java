package com.template.starter.saga.choreography;

import com.template.messaging.saga.SagaRollback;
import com.template.messaging.service.consumer.Consumer;
import com.template.messaging.wrapper.EventWrapper;
import com.template.messaging.event.base.Event;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SagaRollbackRegistryTest {

    record TestEvent() implements Event {}
    record OtherEvent() implements Event {}

    @SagaRollback(source = TestEvent.class, sourcesProcessor = Object.class)
    static class TestRollbackConsumer implements Consumer<OtherEvent> {
        @Override
        public void handle(EventWrapper<OtherEvent> wrapper) {}
    }

    @Test
    void scan_findsAnnotatedBeans_registersInMap() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        TestRollbackConsumer consumer = new TestRollbackConsumer();
        when(ctx.getBeansWithAnnotation(SagaRollback.class))
                .thenReturn(Map.of("testRollbackConsumer", consumer));

        SagaRollbackRegistry registry = new SagaRollbackRegistry(ctx);
        registry.scan();

        assertThat(registry.hasRollbackFor(TestEvent.class)).isTrue();
        assertThat(registry.getRollbackHandlers(TestEvent.class)).hasSize(1);
        assertThat(registry.getRollbackHandlers(TestEvent.class).get(0).beanName())
                .isEqualTo("testRollbackConsumer");
    }

    @Test
    void getRollbackHandlers_noHandlers_returnsEmptyList() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(SagaRollback.class)).thenReturn(Map.of());

        SagaRollbackRegistry registry = new SagaRollbackRegistry(ctx);
        registry.scan();

        assertThat(registry.hasRollbackFor(TestEvent.class)).isFalse();
        assertThat(registry.getRollbackHandlers(TestEvent.class)).isEmpty();
    }

    @Test
    void getAllMappings_returnsUnmodifiableMap() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        TestRollbackConsumer consumer = new TestRollbackConsumer();
        when(ctx.getBeansWithAnnotation(SagaRollback.class))
                .thenReturn(Map.of("testRollbackConsumer", consumer));

        SagaRollbackRegistry registry = new SagaRollbackRegistry(ctx);
        registry.scan();

        assertThatThrownBy(() -> registry.getAllMappings().put(OtherEvent.class, java.util.List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
