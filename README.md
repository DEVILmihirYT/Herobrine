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
