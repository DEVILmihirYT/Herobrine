package com.example.herobrine.ai;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface HerobrineAiProvider {
    CompletableFuture<HerobrineAiDecision> decide(HerobrineAiRequest request);
}
