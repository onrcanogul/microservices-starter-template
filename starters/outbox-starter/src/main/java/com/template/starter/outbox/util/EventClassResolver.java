package com.template.starter.outbox.util;

import com.template.messaging.event.base.Event;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventClassResolver {
    private final Map<String, Class<? extends Event>> map = new ConcurrentHashMap<>();

    public void register(String type, Class<? extends Event> clazz) {
        map.put(type, clazz);
    }

    private static final Set<String> ALLOWED_PACKAGES = Set.of("com.template");

    @SuppressWarnings("unchecked")
    public Class<? extends Event> resolve(String type) {
        Class<? extends Event> c = map.get(type);
        if (c != null) return c;

        if (ALLOWED_PACKAGES.stream().noneMatch(type::startsWith)) {
            throw new IllegalArgumentException("Event type not in allowed packages: " + type);
        }

        try {
            Class<?> raw = Class.forName(type);
            if (!Event.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException("Type is not an Event: " + raw.getName());
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            map.put(type, eventClass);
            return eventClass;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown event type: " + type, e);
        }
    }
}
