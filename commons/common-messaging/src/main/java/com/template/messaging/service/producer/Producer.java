package com.template.messaging.service.producer;

import com.template.messaging.event.base.Event;

public interface Producer<E extends Event> {
    void process(E event);
}
