# Herobrine AI

Herobrine AI is a Fabric mod for Minecraft Java 26.1+ by Manish Rana. It is being built as a server-authoritative, multiplayer-friendly Herobrine experience with persistent stages, adaptive behavior, contextual AI dialogue, optional voice integration, and a long-term endgame system.

## Project vision

The mod centers on a single persistent Herobrine entity that evolves through three stages:

- Stage 1 — Hero: helpful, mysterious, and protective.
- Stage 2 — Glitched Hero: increasingly hostile, stealthy, and capable of holding grudges.
- Stage 3 — Herobrine: a powerful hunter with special combat, escape, and world-event behavior.

## AI interaction model

- Players address the intelligence as Verity with “Hey Verity” or “Oye Verity”.
- Chat is retained locally in bounded per-player windows.
- Every 20 messages a background AI scan may update persistent memory, but that scan is restricted to observation and never speaks.
- Every 40 messages the voice check may choose to speak once, without a text-chat response.
- An explicit Verity mention can produce a normal contextual response.
- Provider order is Gemini Chat → Groq GPT-OSS 120B → Groq GPT-OSS 20B. A failed or limited provider is bypassed for that request without losing the immutable request snapshot.
- The Minecraft context remains bounded, so old chat does not grow the model prompt forever. Provider quotas themselves cannot be removed by the mod; fallback is used whenever a provider returns an error or limit response.

## Technical goals

- Minecraft Java 26.1+
- Fabric only
- Java 25
- Developed and maintained through GitHub + Superpowers/GitHub workflows
- Server-authoritative gameplay
- Asynchronous AI/network work; never block the Minecraft main thread
- Bounded queues and efficient player-range tracking
- Persistent entity state and world-safe recovery
- AI responses are data, never executable commands

## Endgame

- The Dark Frame recipe is intentionally not included yet. Dark Frames are reserved for the future castle schematic.
- After a player kills the Ender Dragon, the mod generates and persists a random castle location roughly 6,000–12,000 blocks from world spawn.
- The coordinates flash on the killer's screen and are written to chat.
- The actual castle structure is deferred until the user-provided schematic is attached.
- The completed castle will contain exactly enough Dark Frames for the intended portal.
- A complete 4×5 Dark Frame structure creates the Dark Portal.
- When Stage 3 Herobrine enters the portal, a 10-second red Doomsday BossBar begins.
- If the player destroys the portal before the countdown ends, the portal vanishes and the player wins.
- Otherwise, Doomsday permanently ends Herobrine and applies the bounded 16-block destruction effect.
- The mod never intentionally crashes the Minecraft client or server.

## Current runtime requirements

- Minecraft Java 26.1
- Fabric Loader 0.19.5 / Fabric API 26.1
- Java 25
- AI providers are optional at build time and use runtime environment variables:
  - GROQ_API_KEY
  - GEMINI_CHAT_API_KEY
  - GEMINI_VOICE_API_KEY
  - ELEVENLABS_API_KEY
- ElevenLabs voice mapping:
  - Stage 1 → Harry (SOYHLrjzK2X1ezoPC6cr)
  - Stage 2 → Charlie (IKne3meq5aSn9XLyUdCD)
  - Stage 3 → Adam (pNInz6obpgDQGcFmaJgB)
- Flashlight is removed. Mashaal remains.
