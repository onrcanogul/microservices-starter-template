package com.template.messaging.service.consumer;

import com.template.messaging.event.base.Event;
import com.template.messaging.wrapper.EventWrapper;

public interface Consumer<R extends Event> {
    void handle(EventWrapper<R> wrapper);
}
