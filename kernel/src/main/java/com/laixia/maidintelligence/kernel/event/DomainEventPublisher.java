package com.laixia.maidintelligence.kernel.event;

public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
