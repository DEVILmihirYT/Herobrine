# Herobrine AI

**Herobrine AI** is a Fabric mod for Minecraft Java 26.1+ by **Manish Rana**. It is being built as a server-authoritative, multiplayer-friendly Herobrine experience with persistent stages, adaptive behavior, contextual AI dialogue, optional voice integration, and a long-term ritual/endgame system.

## Project vision

The mod centers on a single persistent Herobrine entity that evolves through three stages:

- **Stage 1 — Hero:** helpful, mysterious, and protective.
- **Stage 2 — Glitched Hero:** increasingly hostile, stealthy, and capable of holding grudges.
- **Stage 3 — Herobrine:** a powerful hunter with special combat, escape, and world-event behavior.

Planned systems include multiplayer-aware player tracking, local chat context, asynchronous AI providers with fallback support, Gemini-based voice processing, ElevenLabs TTS, the Dark Frame ritual, and a safe-first Doomsday system.

## Technical goals

- Minecraft Java **26.1+**
- **Fabric** only
- Java **25**
- Developed and maintained through **GitHub + Superpowers/GitHub workflows**
- Server-authoritative gameplay
- Asynchronous AI/network work; never block the Minecraft main thread
- Bounded queues and efficient player-range tracking
- Persistent entity state and world-safe recovery
- AI responses are data, never executable commands

## Author

**Manish Rana**

## Status

Active development. The repository is built in small, testable phases; every subsystem is expected to pass GitHub Actions verification before it is treated as complete. The repository is intentionally being built in small, testable phases so every subsystem can be verified before the next one is added.


## Current runtime requirements

- Minecraft Java 26.1
- Fabric Loader 0.19.5 / Fabric API 26.1
- Java 25
- AI providers are optional at build time and use runtime environment variables:
  - `GROQ_API_KEY`
  - `GEMINI_CHAT_API_KEY`
  - `GEMINI_VOICE_API_KEY`
  - `ELEVENLABS_API_KEY`
- ElevenLabs voice mapping:
  - Stage 1 → Harry (`SOYHLrjzK2X1ezoPC6cr`)
  - Stage 2 → Charlie (`IKne3meq5aSn9XLyUdCD`)
  - Stage 3 → Adam (`pNInz6obpgDQGcFmaJgB`)
- Flashlight is removed. Mashaal remains.
- Dark Frame ritual consumes exactly 3 Dragon Heads, 20 Nether Stars and 10 Sculk blocks and produces 30 Dark Frames.
- A complete 4×5 Dark Frame structure creates the Dark Portal. Stage 3 entering it starts the 10-second red Doomsday BossBar and permanently disables Herobrine after the countdown.
- The Doomsday effect is intentionally bounded to a 16-block spherical area around each online player; it does not intentionally crash the server/client.
