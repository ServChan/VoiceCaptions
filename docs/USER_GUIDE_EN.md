# Voice Captions — User Guide

Mod version: 1.1.1 · Minecraft 26.3 · Fabric · client-only · Plasmo Voice addon

[Русская версия](USER_GUIDE_RU.md)

## Contents

1. [What the mod does](#what-the-mod-does)
2. [What you need](#what-you-need)
3. [Installation](#installation)
4. [Choosing a recognition engine](#choosing-a-recognition-engine)
5. [First check](#first-check)
6. [What the captions look like](#what-the-captions-look-like)
7. [Commands and keys](#commands-and-keys)
8. [Settings](#settings)
9. [Privacy](#privacy)
10. [Files and folders](#files-and-folders)
11. [FAQ and troubleshooting](#faq-and-troubleshooting)
12. [Building from source](#building-from-source)

## What the mod does

Voice Captions shows what other players say in [Plasmo Voice](https://modrinth.com/mod/plasmo-voice) voice chat: the recognized text appears as a bubble above the speaker's head, like subtitles.

The mod takes the already decoded voice from Plasmo Voice, cuts it into phrases and hands them to a local Whisper engine. Recognition runs on your own computer, on the graphics card or on the NPU of a Ryzen AI laptop. No cloud services are used.

It helps if you are hard of hearing, play muted, cannot follow fast speech, or want to see who exactly is talking in a crowd.

## What you need

- Minecraft 26.3 with Fabric Loader `0.19.3+` and Fabric API `0.161.0+26.3`.
- [Plasmo Voice](https://modrinth.com/mod/plasmo-voice) `2.1.17` or newer for 26.3, on your side and on the server.
- Java 25.
- Something to recognize speech with, one of:
  - a **graphics card** with Vulkan (NVIDIA, AMD or Intel) on Windows x64: the mod downloads and starts the engine itself;
  - the **NPU** of a Ryzen AI 300 laptop (an HX 370, for example) with FastFlowLM;
  - **your own** recognition server with a `/v1/audio/transcriptions` API.

## Installation

1. Install Fabric Loader, Fabric API and Plasmo Voice for 26.3.
2. Put `voice-captions-1.1.1.jar` into `mods` (see [Building](#building-from-source)).
3. Optional: [Mod Menu](https://modrinth.com/mod/modmenu).
4. Start the game and choose an engine (next section).

## Choosing a recognition engine

The default mode is **Auto**: the mod picks the engine itself.

1. If the FastFlowLM NPU server runs on the computer, speech goes to the NPU, and the CPU and GPU stay free for the game.
2. Otherwise the mod starts Whisper on the graphics card, if the engine is already downloaded.
3. If the NPU server is closed mid-game, the next phrase goes to the graphics card on its own.

### Graphics card (best for most people)

1. Type `/voicecaptions` in chat to open the settings.
2. Recognition tab → **Download for GPU** button.
3. The mod downloads whisper.cpp 1.8.4 with Vulkan (about 21 MB, from GitHub `lemonade-sdk`) and the model (from Hugging Face `ggerganov`), checks the size and SHA-256 and unpacks them into the game's `voicecaptions/` folder.
4. From then on the engine starts by itself when you join a world and is "warmed up" right away, so the first phrase does not wait 5–8 seconds for Vulkan to build its shaders.

If you use a proxy, the mod honours the Windows system proxy and the `HTTPS_PROXY` and `HTTP_PROXY` environment variables.

Models for the graphics card:

| Model | Size | Video memory | A 3–7 s phrase (RTX 5080) | Russian |
|---|---|---|---|---|
| Turbo (default) | 547 MB | about 0.9 GB | 60–120 ms | no mistakes on the test phrases |
| Small | 181 MB | not measured | not measured | between Turbo and Base |
| Base | 57 MB | not measured | 30–55 ms | noticeably wrong at times |

Change the model with the Model setting on the Recognition tab, then download it with the same button.

### NPU (Ryzen AI 300 laptops)

1. Update the AMD NPU driver to `32.0.203.311` or newer (Device Manager → Neural processors).
2. Install FastFlowLM: `flm-setup.msi` from the [releases page](https://github.com/ROCm/FastFlowLM/releases).
3. In a terminal run:

   ```powershell
   flm pull whisper-v3:turbo
   flm serve --asr 1
   ```

4. The server listens on `127.0.0.1:52625`. Auto mode finds it and prefers it over the graphics card.

The first phrase after the server starts takes 10–15 seconds while the model loads; after that a short phrase takes about 1–2.5 seconds. FastFlowLM detects the language itself and ignores the Language setting.

### Your own server

1. Switch Engine to **Own server**.
2. Enter the Server address, for example:
   - Lemonade Server: `http://127.0.0.1:13305/v1/audio/transcriptions`;
   - `whisper-server` from whisper.cpp: `http://127.0.0.1:8080/inference`.
3. Enter the Model if your server needs one (whisper.cpp ignores it).

In this mode speech goes only to the address you entered.

## First check

1. `/voicecaptions check` or the Check server button: the mod saves the settings, sends the engine one second of silence and reports which engine answered and how fast.
2. `/voicecaptions test Hello, this is a test`: a bubble appears above the nearest player, or above you if nobody is around (look with F5).
3. Ask a friend in voice chat to say something.

If there is nothing to recognize speech with, the mod posts one clear hint in chat (at most once a minute) and pauses instead of flooding the server with requests.

## What the captions look like

- The bubble is pinned above the player, follows them and shrinks smoothly with distance.
- Text wraps by words, and an over-long word is split with a hyphen.
- The line count is capped: older lines go behind an ellipsis, the newest phrase is brighter.
- Bubbles of several speakers never overlap: the farther one moves above the nearer one, and bubbles are pushed back inside the screen edges.
- Running dots show that a player is talking or that a phrase is still being recognized.
- A phrase ends on a pause or on Plasmo Voice's end-of-speech signal. Long speech is cut at a quiet moment, so captions do not wait for the end of a monologue.
- Silence and noise are not sent for recognition. Known Whisper hallucinations ("Thank you for watching", "Продолжение следует…") and repetition loops are dropped.

## Commands and keys

| Action | How |
|---|---|
| Open the settings | `/voicecaptions` or Mod Menu |
| Turn captions on or off | `/voicecaptions toggle` or the Toggle voice captions key (unbound by default, **Options → Controls → Voice Captions**) |
| Check recognition | `/voicecaptions check` |
| Engine status | `/voicecaptions engine` or `/voicecaptions engine status` |
| Download the GPU engine | `/voicecaptions engine install` |
| Cancel the download | `/voicecaptions engine cancel` |
| Show a sample bubble | `/voicecaptions test [text]` |

## Settings

Open the screen with `/voicecaptions` or Mod Menu. Every field has a tooltip.

### Recognition tab

| Setting | Default | Range | What it does |
|---|---|---|---|
| Captions | on | | the main switch |
| Engine | Auto | Auto, Own server | see [Choosing an engine](#choosing-a-recognition-engine) |
| GPU model | Turbo | Turbo, Small, Base | Whisper model for the graphics card |
| Download for GPU | | | downloads the engine and model, shows progress |
| Server address | `http://127.0.0.1:52625/v1/audio/transcriptions` | | Own server mode only |
| Model (own server) | `whisper-v3:turbo` | | model name for your own server |
| Language | `ru` | a language code or empty | empty means auto-detect |
| Request timeout, s | 20 | 5–120 | how long to wait for one phrase |
| Queue limit | 6 | 1–20 | with more phrases waiting, the oldest are skipped so captions stay current |
| Pause ends phrase, ms | 700 | 300–2000 | silence after which a phrase is sent |
| Max phrase, s | 7 | 3–20 | long speech is cut at a quiet moment after this many seconds |
| Only audible | on | | skip players you muted or turned down to zero in Plasmo Voice |

A "Now: …" line at the bottom of the tab shows what is recognizing speech at the moment.

### Appearance tab

| Setting | Default | Range | What it does |
|---|---|---|---|
| Bubble width, px | 180 | 60–400 | text width before wrapping |
| Max lines | 3 | 1–8 | older lines go behind an ellipsis |
| Show for, s | 4 | 1–30 | base time a phrase stays; longer phrases stay longer, up to 15 s |
| Max distance, blocks | 32 | 4–128 | captions of farther players are not shown |
| Size near | 1.0 | 0.5–2.0 | bubble size next to you (1 = normal GUI text) |
| Size far | 0.6 | 0.3–1.0 | share of the near size at the max distance |
| Height above name | 0.55 | 0–3 blocks | raise it if the bubble covers the Plasmo Voice icon |
| Background opacity | 170 | 0–255 | 0 is transparent, 255 is solid |
| Through walls | on | | voice carries through walls, so captions do too. Turn off to show only players you can see |
| Speaking dots | on | | running dots while a player talks |
| Name in bubble | off | | the speaker's name above the text; handy in crowds or with hidden name tags |

## Privacy

- In Auto mode speech never leaves your computer: both the NPU server and the GPU engine run locally.
- In Own server mode speech goes only to the address you entered.
- Neither audio nor text is saved to disk.
- The mod sends nothing to the Minecraft server.

## Files and folders

- `config/voicecaptions.json` holds the settings (atomic write, `.bak` backup).
- `voicecaptions/engine/` in the game folder holds the downloaded whisper.cpp, the models and the engine log `engine.log`. The Open engine folder button on the Recognition tab opens it. Delete it to free space; the engine downloads again the next time you install it.

## FAQ and troubleshooting

**No captions at all.** Check in order: is Plasmo Voice installed and can you hear the player; are captions on (`/voicecaptions toggle`); what does `/voicecaptions check` say; is the player within Max distance.

**`check` says there is nothing to recognize with.** Download the GPU engine or start FastFlowLM. In Own server mode check the address.

**The engine download does not start.** GitHub or Hugging Face cannot be reached directly. Turn on the system proxy or set `HTTPS_PROXY` and run `/voicecaptions engine install` again.

**The first phrase arrives with a long delay.** That is Vulkan building shaders (5–8 s) or the NPU loading the model (10–15 s). The mod makes the first request itself when you join a world, and later delays are short.

**Recognition makes mistakes.** Pick the Turbo model and check the Language (`ru` for Russian speech, `en` for English, empty for auto-detect).

**Captions lag behind.** Lower Max phrase and Pause ends phrase. On a weak graphics card pick the Small model.

**The bubble covers the Plasmo Voice icon above the head.** Raise Height above name.

**A whisper-server process stayed after a crash.** The mod closes it on the next start.

**Does it work on Linux and macOS?** The mod downloads the GPU engine for Windows x64 only. On other systems use Own server mode.

## Building from source

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/VoiceCaptions.git
cd VoiceCaptions
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. The jar is `build/libs/voice-captions-1.1.1.jar`.

A live engine test (downloads files into the given folder and needs `ru.wav`, `short.wav` and `en.wav` at 16 kHz mono):

```powershell
.\gradlew.bat test --tests "*LiveEngineTest" "-Dvoicecaptions.live=<folder>" "-Dvoicecaptions.live.models=base,turbo"
```
