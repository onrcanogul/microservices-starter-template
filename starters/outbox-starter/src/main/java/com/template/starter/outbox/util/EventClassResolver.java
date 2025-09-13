package com.template.starter.outbox.util;

import com.template.messaging.event.base.Event;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventClassResolver {
    private final Map<String, Class<? extends Event>> map = new ConcurrentHashMap<>();

    public Class<? extends Event> resolve(String type) {
        Class<? extends Event> c = map.get(type);
        if (c == null) throw new IllegalArgumentException("Unknown event type: " + type);
        return c;
    }
}
