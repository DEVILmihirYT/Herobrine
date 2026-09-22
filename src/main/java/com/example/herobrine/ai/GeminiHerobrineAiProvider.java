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
 * Gemini text provider. This is the separate chat-key provider.
 *
 * Credentials are read only from GEMINI_CHAT_API_KEY.
 */
public final class GeminiHerobrineAiProvider implements HerobrineAiProvider {
    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private final HttpClient httpClient;
    private final String model;

    public GeminiHerobrineAiProvider() {
        this(System.getenv().getOrDefault("GEMINI_CHAT_MODEL", DEFAULT_MODEL));
    }

    public GeminiHerobrineAiProvider(String model) {
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<HerobrineAiDecision> decide(HerobrineAiRequest request) {
        String apiKey = System.getenv("GEMINI_CHAT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("GEMINI_CHAT_API_KEY is not configured")
            );
        }

        JsonObject root = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text", request.systemPrompt()
                + "\nReturn ONLY the JSON decision object. No markdown.");
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);
        root.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("text", request.context());
        parts.add(text);
        content.add("parts", parts);
        contents.add(content);
        root.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.add("responseSchema", decisionSchema());
        root.add("generationConfig", generationConfig);

        URI endpoint = URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent"
        );

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(45))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CompletionException(new IllegalStateException(
                                "Gemini HTTP " + response.statusCode() + ": " + trim(response.body())
                        ));
                    }
                    return parseDecision(response.body());
                });
    }

    private static JsonObject decisionSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        properties.add("action", enumSchema());
        properties.add("speech", nullableString());
        properties.add("targetPlayer", nullableString());
        properties.add("itemId", nullableString());
        properties.add("itemCount", numberSchema());
        properties.add("memoryNote", nullableString());
        properties.add("relationshipDelta", numberSchema());
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("action");
        required.add("speech");
        required.add("targetPlayer");
        required.add("itemId");
        required.add("itemCount");
        required.add("memoryNote");
        required.add("relationshipDelta");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
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
        var candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response contains no candidates");
        }

        var parts = candidates.get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts");

        String content = null;
        for (var part : parts) {
            if (!part.isJsonObject()) {
                continue;
            }
            JsonObject partObject = part.getAsJsonObject();
            if (partObject.has("text") && !partObject.get("text").isJsonNull()) {
                content = partObject.get("text").getAsString();
                break;
            }
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Gemini response contains no text");
        }

        var json = JsonParser.parseString(content).getAsJsonObject();
        HerobrineAction action = HerobrineAction.valueOf(json.get("action").getAsString());

        String speech = nullableValue(json, "speech");
        UUID target = parseUuid(nullableValue(json, "targetPlayer"));
        String itemId = nullableValue(json, "itemId");
        int itemCount = json.get("itemCount").getAsInt();
        String memoryNote = nullableValue(json, "memoryNote");
        int relationshipDelta = json.get("relationshipDelta").getAsInt();

        return new HerobrineAiDecision(
                action, speech, target, itemId, itemCount, memoryNote, relationshipDelta
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
