package com.example.herobrine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Bounded local chat memory for future AI requests.
 *
 * Every message is stored locally, but only explicit Hero/Herobrine mentions
 * are marked as external-AI triggers. No network request is made here.
 */
public final class HerobrineChatMemory {
    public static final int MAX_MESSAGES = 20;

    public record Message(UUID playerId, String playerName, String text, long gameTime) {
    }

    private final Deque<Message> messages = new ArrayDeque<>();

    public void add(Message message) {
        if (message == null || message.text() == null || message.text().isBlank()) {
            return;
        }

        messages.addLast(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    public List<Message> recent() {
        return List.copyOf(messages);
    }

    public List<Message> recentFor(UUID playerId) {
        return messages.stream()
                .filter(message -> message.playerId().equals(playerId))
                .toList();
    }

    public void clear() {
        messages.clear();
    }
}
