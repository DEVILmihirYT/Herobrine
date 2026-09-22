package com.example.herobrine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded local chat memory.
 *
 * Messages are retained per player in small windows. Every 20 messages produces
 * a deterministic batch signal for the AI layer; no unbounded transcript is
 * ever placed into an external model context.
 */
public final class HerobrineChatMemory {
    public static final int BATCH_SIZE = 20;
    public static final int VOICE_BATCH_SIZE = 40;
    private static final int MAX_MESSAGES_PER_PLAYER = BATCH_SIZE;
    private static final int MAX_GLOBAL_MESSAGES = BATCH_SIZE;

    public record Message(UUID playerId, String playerName, String text, long gameTime) {
    }

    public record BatchSignal(long messageCount, boolean chatBatchReady, boolean voiceCheckReady) {
    }

    private final Deque<Message> globalMessages = new ArrayDeque<>();
    private final Map<UUID, Deque<Message>> playerMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> messageCounts = new ConcurrentHashMap<>();

    public BatchSignal add(Message message) {
        if (message == null || message.text() == null || message.text().isBlank()) {
            return new BatchSignal(0L, false, false);
        }

        globalMessages.addLast(message);
        while (globalMessages.size() > MAX_GLOBAL_MESSAGES) {
            globalMessages.removeFirst();
        }

        Deque<Message> messages = playerMessages.computeIfAbsent(
                message.playerId(),
                ignored -> new ArrayDeque<>()
        );
        messages.addLast(message);
        while (messages.size() > MAX_MESSAGES_PER_PLAYER) {
            messages.removeFirst();
        }

        long count = messageCounts.merge(message.playerId(), 1L, Long::sum);
        boolean chatBatchReady = count % BATCH_SIZE == 0L;
        boolean voiceCheckReady = count % VOICE_BATCH_SIZE == 0L;
        return new BatchSignal(count, chatBatchReady, voiceCheckReady);
    }

    public List<Message> recent() {
        return List.copyOf(globalMessages);
    }

    public List<Message> recentFor(UUID playerId) {
        Deque<Message> messages = playerMessages.get(playerId);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    public long messageCount(UUID playerId) {
        return messageCounts.getOrDefault(playerId, 0L);
    }

    public void clear() {
        globalMessages.clear();
        playerMessages.clear();
        messageCounts.clear();
    }
}
