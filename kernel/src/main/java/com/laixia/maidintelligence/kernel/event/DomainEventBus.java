package com.laixia.maidintelligence.kernel.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Synchronous application bus used outside render and physics hot paths.
 */
public final class DomainEventBus implements DomainEventPublisher {
    private final Map<Class<?>, List<Consumer<?>>> subscribers =
            new LinkedHashMap<>();

    public <E extends DomainEvent> void subscribe(
            Class<E> type,
            Consumer<? super E> consumer
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(consumer, "consumer");
        subscribers.computeIfAbsent(type, ignored -> new ArrayList<>())
                .add(consumer);
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<?> subscriber
                : List.copyOf(subscribers.getOrDefault(
                        event.getClass(),
                        List.of()
                ))) {
            dispatch(subscriber, event);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends DomainEvent> void dispatch(
            Consumer<?> subscriber,
            E event
    ) {
        ((Consumer<E>) subscriber).accept(event);
    }
}
