package com.laixia.maidintelligence.kernel.service;

public interface ServiceRegistry {
    <T> T require(Class<T> contract);
}
