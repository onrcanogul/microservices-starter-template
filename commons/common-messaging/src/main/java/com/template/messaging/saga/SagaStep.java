package com.template.messaging.saga;

public interface SagaStep<P, R> {
    void process(P event);
    void rollback(R payload);
}
