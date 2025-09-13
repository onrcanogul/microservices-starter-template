package com.template.messaging.saga;

import com.template.messaging.wrapper.EventWrapper;

public interface SagaStep<P, R> {
    void process(P event);
    void rollback(EventWrapper<R> payload);
}
