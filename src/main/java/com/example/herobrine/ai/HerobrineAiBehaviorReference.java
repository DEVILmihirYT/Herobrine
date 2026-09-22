package com.example.herobrine.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the non-AI activity list as a behavioural reference for the external model.
 * Nothing in this class executes gameplay behaviour.
 */
public final class HerobrineAiBehaviorReference {
    private static final String RESOURCE = "herobrineai/ai_behavior_reference.txt";
    private static final String TEXT = load();

    private HerobrineAiBehaviorReference() {
    }

    public static String text() {
        return TEXT;
    }

    private static String load() {
        try (InputStream stream = HerobrineAiBehaviorReference.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return "Behaviour reference unavailable; use only currently supplied capabilities.";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "Behaviour reference could not be loaded; use only currently supplied capabilities.";
        }
    }
}
