# Voice Captions

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Requires](https://img.shields.io/badge/Requires-Plasmo%20Voice%202.1.17%2B-red?style=flat-square)](https://modrinth.com/mod/plasmo-voice)

Speech-to-text captions above players' heads for Plasmo Voice, recognized by a local Whisper server (NPU, GPU or CPU).

## Русский

### Что это

`Voice Captions` (mod id `voicecaptions`) — клиентский Fabric-мод для Minecraft 26.3 и аддон Plasmo Voice. Он берёт уже декодированный голос других игроков, режет его на фразы и отправляет локальному серверу распознавания Whisper. Распознанный текст появляется «облаком» над головой говорящего.

Распознавание идёт на вашем ПК. На ноутбуке с Ryzen AI 300 (например, HX 370) сервер FastFlowLM запускает Whisper на NPU, и процессор с видеокартой остаются игре.

### Возможности

- облако над головой привязано к игроку и движется вместе с ним, а с расстоянием плавно уменьшается (размер вблизи и вдали настраивается);
- ширина облака ограничена, текст переносится по словам, а слишком длинное слово разбивается с дефисом;
- строк не больше заданного числа: старые строки уходят за многоточие, новые выделены ярче старых;
- облака нескольких говорящих не перекрываются: дальнее поднимается над ближним, у края экрана облако прижимается внутрь;
- бегущие точки показывают, что игрок говорит или что фраза ещё распознаётся;
- фраза заканчивается паузой или сигналом конца речи от Plasmo Voice, а длинная речь режется в тихом месте, чтобы субтитры не ждали конца монолога;
- тишина и шум на распознавание не уходят, известные «галлюцинации» Whisper («Продолжение следует…», «Субтитры сделал…», «Thank you for watching») и зацикленные повторы отбрасываются;
- заглушённые в Plasmo Voice игроки не распознаются (опция);
- сквозь стены — по умолчанию да, потому что голос слышно через стены; можно включить проверку прямой видимости;
- если сервер не запущен, в чате появляется одна понятная подсказка (не чаще раза в минуту), а мод делает паузу, вместо того чтобы долбить сервер запросами;
- текст и звук никуда не сохраняются.

### Управление

| Действие | Клавиша / команда |
|---|---|
| Включить / выключить субтитры | не назначена; Options → Controls → Voice Captions, или `/voicecaptions toggle` |
| Открыть настройки | `/voicecaptions` или Mod Menu |
| Проверить сервер распознавания | `/voicecaptions check` или кнопка «Проверить сервер» |
| Показать пример облака | `/voicecaptions test [текст]` (над ближайшим игроком, иначе над вами — смотрите через F5) |

### Настройки

Две вкладки: **Распознавание** (адрес сервера, модель, язык, ожидание ответа, лимит очереди, пауза до конца фразы, максимальная длина фразы, «только слышимых») и **Вид** (ширина облака, число строк, время показа, дальность, размер вблизи и вдали, высота над ником, непрозрачность фона, сквозь стены, точки «говорит», ник в облаке). У каждого поля есть подсказка. Файл `config/voicecaptions.json` (атомарная запись, резервная копия `.bak`).

### Установка

1. **Fabric Loader** `0.19.3+`, **Fabric API** `0.161.0+26.3` и **Plasmo Voice** `2.1.17+` для 26.3.
2. Добавьте `voice-captions-1.0.0.jar` из `build/libs/` в папку `mods/`.
3. Запустите сервер распознавания.

**NPU (Ryzen AI 300, рекомендуется):**

1. Обновите драйвер NPU AMD до `32.0.203.311` или новее (Диспетчер устройств → Нейронные процессоры).
2. Установите FastFlowLM (`flm-setup.msi`, https://github.com/ROCm/FastFlowLM/releases).
3. В терминале:

```powershell
flm pull whisper-v3:turbo
flm serve --asr 1
```

Сервер слушает `127.0.0.1:52625`, это адрес по умолчанию в моде. Первая фраза после запуска распознаётся 10–15 секунд, пока грузится модель; дальше короткая фраза — примерно 1–2,5 секунды. FastFlowLM сам определяет язык и игнорирует поле «Язык».

**Без NPU:** Lemonade Server (`http://127.0.0.1:13305/v1/audio/transcriptions`, модель `Whisper-Large-v3-Turbo` или `Whisper-Small` на ROCm/Vulkan/CPU) или `whisper-server` из whisper.cpp (`http://127.0.0.1:8080/inference`). Для них поле «Язык» (`ru`) работает.

**Требования:** Minecraft `26.3` · Java `25` · только клиент · Plasmo Voice на сервере и у вас · локальный сервер распознавания с API `/v1/audio/transcriptions`.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/voice-captions-1.0.0.jar`.

---

## English

### What It Is

`Voice Captions` (mod id `voicecaptions`) is a client-side Fabric mod for Minecraft 26.3 and a Plasmo Voice addon. It takes the already decoded voice of other players, cuts it into phrases and sends them to a local Whisper recognition server. The recognized text appears as a bubble above the speaker's head.

Recognition runs on your PC. On a Ryzen AI 300 laptop (for example the HX 370) the FastFlowLM server runs Whisper on the NPU, leaving the CPU and GPU to the game.

### Features

- the bubble is pinned above the player, follows them and shrinks smoothly with distance (near and far sizes are configurable);
- bubble width is capped, text wraps by words, and an over-long word is split with a hyphen;
- the line count is capped: older lines go behind an ellipsis, the newest phrase is brighter than older ones;
- bubbles of several speakers never overlap: the farther one moves above the nearer one, and bubbles are pushed back inside the screen edges;
- animated dots show that a player is talking or that a phrase is still being recognized;
- a phrase ends on a pause or on Plasmo Voice's end-of-speech signal, and long speech is cut at a quiet moment so captions do not wait for the end of a monologue;
- silence and noise are not sent for recognition; known Whisper hallucinations ("Thank you for watching", "Продолжение следует…") and repetition loops are dropped;
- players muted in Plasmo Voice are skipped (option);
- through walls by default, because voice carries through walls; a line-of-sight check can be turned on;
- if the server is not running, chat shows one clear hint (at most once a minute) and the mod backs off instead of flooding the server;
- no text or audio is saved anywhere.

### Controls

| Action | Key / command |
|---|---|
| Toggle captions | unbound; Options → Controls → Voice Captions, or `/voicecaptions toggle` |
| Open settings | `/voicecaptions` or Mod Menu |
| Check the recognition server | `/voicecaptions check` or the "Check server" button |
| Show a sample bubble | `/voicecaptions test [text]` (above the nearest player, otherwise above you — use F5) |

### Configuration

Two tabs: **Recognition** (server address, model, language, request timeout, queue limit, pause that ends a phrase, max phrase length, "only audible") and **Appearance** (bubble width, line count, display time, max distance, near and far size, height above the name tag, background opacity, through walls, speaking dots, name in bubble). Every field has a tooltip. File `config/voicecaptions.json` (atomic write, `.bak` backup).

### Installation

1. **Fabric Loader** `0.19.3+`, **Fabric API** `0.161.0+26.3` and **Plasmo Voice** `2.1.17+` for 26.3.
2. Add `voice-captions-1.0.0.jar` from `build/libs/` to the `mods/` folder.
3. Start a recognition server.

**NPU (Ryzen AI 300, recommended):** update the AMD NPU driver to `32.0.203.311` or newer, install FastFlowLM (`flm-setup.msi`, https://github.com/ROCm/FastFlowLM/releases), then run `flm pull whisper-v3:turbo` and `flm serve --asr 1`. It listens on `127.0.0.1:52625`, the mod's default address. The first phrase after start takes 10–15 s while the model loads; after that a short phrase takes about 1–2.5 s. FastFlowLM detects the language itself and ignores the Language field.

**Without an NPU:** Lemonade Server (`http://127.0.0.1:13305/v1/audio/transcriptions`, model `Whisper-Large-v3-Turbo` or `Whisper-Small` on ROCm/Vulkan/CPU) or whisper.cpp `whisper-server` (`http://127.0.0.1:8080/inference`). Both honour the Language field (`ru`).

**Requirements:** Minecraft `26.3` · Java `25` · client only · Plasmo Voice on the server and on your client · a local recognition server with a `/v1/audio/transcriptions` API.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/voice-captions-1.0.0.jar`.

## Лицензия / License

MIT
