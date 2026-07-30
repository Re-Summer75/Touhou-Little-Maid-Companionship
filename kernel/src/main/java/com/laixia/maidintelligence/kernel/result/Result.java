package com.laixia.maidintelligence.kernel.result;

import java.util.Objects;
import java.util.function.Function;

/**
 * Explicit success/failure value for application boundaries where exceptions
 * would hide an expected domain rejection.
 */
public sealed interface Result<T, E>
        permits Result.Success, Result.Failure {
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    <R> R fold(
            Function<? super T, ? extends R> onSuccess,
            Function<? super E, ? extends R> onFailure
    );

    record Success<T, E>(T value) implements Result<T, E> {
        public Success {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public <R> R fold(
                Function<? super T, ? extends R> onSuccess,
                Function<? super E, ? extends R> onFailure
        ) {
            return Objects.requireNonNull(onSuccess, "onSuccess").apply(value);
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        public Failure {
            Objects.requireNonNull(error, "error");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public <R> R fold(
                Function<? super T, ? extends R> onSuccess,
                Function<? super E, ? extends R> onFailure
        ) {
            return Objects.requireNonNull(onFailure, "onFailure").apply(error);
        }
    }
}
