package com.example.herobrine.ai;

import com.example.herobrine.HerobrineChatMemory;
import com.example.herobrine.HerobrinePlayerTracker;
import com.example.herobrine.HerobrineStage;
import java.util.List;
import java.util.Locale;

/**
 * Builds compact model input from the local Minecraft state.
 *
 * This is an AI-context layer only. It does not execute game logic.
 */
public final class HerobrineAiPromptBuilder {
    private HerobrineAiPromptBuilder() {
    }

    public static String systemPrompt(HerobrineStage stage) {
        return HerobrineAiPersonality.systemPrompt(stage);
    }

    public static String context(HerobrineAiRequest request) {
        StringBuilder out = new StringBuilder(4096);

        out.append("CURRENT STAGE: ").append(request.stage()).append('\n');
        out.append("TRIGGER: ").append(request.trigger()).append('\n');
        out.append("PLAYER: ").append(request.playerName())
                .append(" (").append(request.playerId()).append(")\n");

        out.append("\nRELEVANT PLAYER SNAPSHOTS:\n");
        appendPlayers(out, request.trackedPlayers());

        out.append("\nPERSISTENT PLAYER MEMORY:\n");
        out.append("relationship=").append(request.relationship()).append("/100\n");
        appendMemories(out, request.playerMemories());

        out.append("\nRECENT LOCAL CHAT (bounded):\n");
        appendChat(out, request.recentChat());

        out.append("\nDECISION RULE:\n");
        out.append("Choose one believable intent from the capabilities supplied by Minecraft. ");
        out.append("Do not invent unavailable actions. Silence/OBSERVE is valid. ");
        out.append("Only retain meaningful information as memory.\n");

        return out.toString();
    }

    private static void appendPlayers(
            StringBuilder out,
            List<HerobrinePlayerTracker.Snapshot> players
    ) {
        if (players.isEmpty()) {
            out.append("(none)\n");
            return;
        }

        for (HerobrinePlayerTracker.Snapshot player : players) {
            out.append("- ")
                    .append(player.name())
                    .append(" uuid=").append(player.uuid())
                    .append(" dimension=").append(player.dimension())
                    .append(" pos=")
                    .append(format(player.x())).append(',')
                    .append(format(player.y())).append(',')
                    .append(format(player.z()))
                    .append(" distance=").append(format(player.distanceToHero()))
                    .append(" alive=").append(player.alive())
                    .append(" creative=").append(player.creative())
                    .append(" operator=").append(player.operator())
                    .append('\n');
        }
    }

    private static void appendMemories(StringBuilder out, List<String> memories) {
        if (memories.isEmpty()) {
            out.append("(none)\\n");
            return;
        }

        for (String memory : memories) {
            out.append("- ").append(memory).append('\\n');
        }
    }

    private static void appendChat(
            StringBuilder out,
            List<HerobrineChatMemory.Message> messages
    ) {
        if (messages.isEmpty()) {
            out.append("(none)\n");
            return;
        }

        for (HerobrineChatMemory.Message message : messages) {
            out.append("- ")
                    .append(message.playerName())
                    .append(": ")
                    .append(message.text())
                    .append(" [t=").append(message.gameTime()).append(']')
                    .append('\n');
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
