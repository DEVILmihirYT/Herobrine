package com.example.herobrine.ai;

import com.example.herobrine.HerobrineAction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * OpenAI-compatible Groq provider.
 *
 * Credentials are read only from the GROQ_API_KEY environment variable.
 */
public final class GroqHerobrineAiProvider implements HerobrineAiProvider {
    private static final URI ENDPOINT =
            URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final String PRIMARY_MODEL = "openai/gpt-oss-120b";

    private final HttpClient httpClient;
    private final String model;

    public GroqHerobrineAiProvider() {
        this(PRIMARY_MODEL);
    }

    public GroqHerobrineAiProvider(String model) {
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<HerobrineAiDecision> decide(HerobrineAiRequest request) {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GROQ_API_KEY is not configured")
            );
        }

        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("temperature", 0.7);
        root.addProperty("max_completion_tokens", 700);
        root.addProperty("reasoning_effort", "medium");

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", request.systemPrompt()
                + "\nReturn ONLY the JSON decision object. No markdown.");
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", request.context());
        messages.add(user);
        root.add("messages", messages);

        root.add("response_format", decisionSchema());

        HttpRequest httpRequest = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CompletionException(new IllegalStateException(
                                "Groq HTTP " + response.statusCode() + ": " + trim(response.body())
                        ));
                    }
                    return parseDecision(response.body());
                });
    }

    private static JsonObject decisionSchema() {
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");

        JsonObject schema = new JsonObject();
        schema.addProperty("name", "herobrine_decision");
        schema.addProperty("strict", true);

        JsonObject body = new JsonObject();
        body.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        properties.add("action", enumSchema());
        properties.add("speech", nullableString());
        properties.add("targetPlayer", nullableString());
        properties.add("itemId", nullableString());
        properties.add("itemCount", numberSchema());
        properties.add("memoryNote", nullableString());
        properties.add("relationshipDelta", numberSchema());
        body.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("action");
        required.add("speech");
        required.add("targetPlayer");
        required.add("itemId");
        required.add("itemCount");
        required.add("memoryNote");
        required.add("relationshipDelta");
        body.add("required", required);
        body.addProperty("additionalProperties", false);

        schema.add("schema", body);
        format.add("json_schema", schema);

        JsonObject wrapper = new JsonObject();
        wrapper.add("type", format.get("type"));
        wrapper.add("json_schema", format.get("json_schema"));
        return wrapper;
    }

    private static JsonObject enumSchema() {
        JsonObject object = new JsonObject();
        object.addProperty("type", "string");
        JsonArray values = new JsonArray();
        for (HerobrineAction action : HerobrineAction.values()) {
            values.add(action.name());
        }
        object.add("enum", values);
        return object;
    }

    private static JsonObject nullableString() {
        JsonObject object = new JsonObject();
        JsonArray types = new JsonArray();
        types.add("string");
        types.add("null");
        object.add("type", types);
        return object;
    }

    private static JsonObject numberSchema() {
        JsonObject object = new JsonObject();
        object.addProperty("type", "integer");
        return object;
    }

    private static HerobrineAiDecision parseDecision(String responseBody) {
        var root = JsonParser.parseString(responseBody).getAsJsonObject();
        var choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq response contains no choices");
        }

        String content = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        var json = JsonParser.parseString(content).getAsJsonObject();

        HerobrineAction action = HerobrineAction.valueOf(json.get("action").getAsString());
        String speech = nullableValue(json, "speech");
        UUID target = parseUuid(nullableValue(json, "targetPlayer"));
        String itemId = nullableValue(json, "itemId");
        int itemCount = json.get("itemCount").getAsInt();
        String memoryNote = nullableValue(json, "memoryNote");
        int relationshipDelta = json.get("relationshipDelta").getAsInt();

        return new HerobrineAiDecision(
                action,
                speech,
                target,
                itemId,
                itemCount,
                memoryNote,
                relationshipDelta
        );
    }

    private static String nullableValue(JsonObject json, String name) {
        if (!json.has(name) || json.get(name).isJsonNull()) {
            return null;
        }
        return json.get(name).getAsString();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
