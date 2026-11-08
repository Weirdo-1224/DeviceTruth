package io.devicetruth.application;

import io.devicetruth.application.ConversationMessage.Role;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationServiceTest {
    @Test void sessionsAreBounded() {
        var service = new ConversationService(2, 10);
        service.create();
        service.create();
        assertThatThrownBy(service::create)
                .isInstanceOf(ApplicationExceptions.SessionCapacityExceededException.class);
    }

    @Test void messagesAreBounded() {
        var service = new ConversationService(1, 2);
        var session = service.create();
        session.append(Role.USER, Route.CLARIFY, "one");
        session.append(Role.SYSTEM, Route.CLARIFY, "two");
        assertThatThrownBy(() -> session.append(Role.USER, Route.CLARIFY, "three"))
                .isInstanceOf(ApplicationExceptions.MessageCapacityExceededException.class);
    }

    @Test void unknownSessionIsNotFound() {
        var service = new ConversationService(1, 1);
        assertThatThrownBy(() -> service.require(java.util.UUID.randomUUID()))
                .isInstanceOf(ApplicationExceptions.SessionNotFoundException.class);
        assertThatThrownBy(() -> service.require(null))
                .isInstanceOf(ApplicationExceptions.SessionNotFoundException.class);
    }

    @Test void sessionsAreIsolated() {
        var service = new ConversationService(2, 10);
        var first = service.create();
        var second = service.create();
        first.currentAssetId("CAR-B");
        first.append(Role.USER, Route.DIAGNOSE, "CAR-B 通信超时");
        assertThat(second.currentAssetId()).isNull();
        assertThat(second.messages()).isEmpty();
    }

    @Test void concurrentAppendsAreSerialized() throws Exception {
        var service = new ConversationService(1, 1000);
        var session = service.create();
        int count = 50;
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++)
                futures.add(pool.submit(() -> session.append(Role.USER, Route.CLARIFY, "message")));
            for (var future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(session.messages()).hasSize(count);
        assertThat(session.messages().stream().map(ConversationMessage::messageId).distinct().count()).isEqualTo(count);
    }

    @Test void rejectedInteractionKeepsAssetAndMessagesUnchanged() {
        var service = new ConversationService(1, 3);
        var session = service.create();
        session.commitInteraction(Route.CLARIFY, "CAR-B", "CAR-B 帮我看看", "reply-1");
        assertThat(session.messages()).hasSize(2);
        assertThat(session.currentAssetId()).isEqualTo("CAR-B");

        assertThatThrownBy(() -> session.commitInteraction(Route.CLARIFY, "CAR-C", "CAR-C 帮我看看", "reply-2"))
                .isInstanceOf(ApplicationExceptions.MessageCapacityExceededException.class);
        assertThat(session.messages()).hasSize(2);
        assertThat(session.currentAssetId()).isEqualTo("CAR-B");
        assertThat(session.messages()).noneMatch(message -> message.content().startsWith("CAR-C"));
    }

    @Test void fullSessionRejectsInteractionWithoutChangingContext() {
        var service = new ConversationService(1, 2);
        var session = service.create();
        session.commitInteraction(Route.CLARIFY, "CAR-B", "CAR-B 帮我看看", "reply-1");
        assertThatThrownBy(() -> session.commitInteraction(Route.CLARIFY, "CAR-C", "CAR-C 帮我看看", "reply-2"))
                .isInstanceOf(ApplicationExceptions.MessageCapacityExceededException.class);
        assertThat(session.messages()).hasSize(2);
        assertThat(session.currentAssetId()).isEqualTo("CAR-B");
    }

    @Test void concurrentInteractionsCommitInPairs() throws Exception {
        var service = new ConversationService(1, 40);
        var session = service.create();
        int attempts = 20;
        var pool = Executors.newFixedThreadPool(8);
        int successes = 0;
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < attempts; i++) {
                final int n = i;
                futures.add(pool.submit(() -> {
                    try {
                        session.commitInteraction(Route.CLARIFY, "CAR-B", "u" + n, "s" + n);
                        return true;
                    } catch (ApplicationExceptions.MessageCapacityExceededException e) {
                        return false;
                    }
                }));
            }
            for (var future : futures) if (future.get()) successes++;
        } finally {
            pool.shutdownNow();
        }
        assertThat(successes).isEqualTo(attempts);
        var messages = session.messages();
        assertThat(messages).hasSize(attempts * 2);
        for (int i = 0; i < messages.size(); i += 2) {
            assertThat(messages.get(i).role()).isEqualTo(Role.USER);
            assertThat(messages.get(i + 1).role()).isEqualTo(Role.SYSTEM);
            assertThat(messages.get(i + 1).content()).isEqualTo("s" + messages.get(i).content().substring(1));
        }
    }

    @Test void concurrentCreatesRespectBound() throws Exception {
        var service = new ConversationService(5, 10);
        var pool = Executors.newFixedThreadPool(8);
        int successes = 0;
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 20; i++)
                futures.add(pool.submit(() -> {
                    try { service.create(); return true; }
                    catch (ApplicationExceptions.SessionCapacityExceededException e) { return false; }
                }));
            for (var future : futures) if (future.get()) successes++;
        } finally {
            pool.shutdownNow();
        }
        assertThat(successes).isEqualTo(5);
        assertThat(service.size()).isEqualTo(5);
    }
}