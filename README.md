# Voice Captions

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.1.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Requires](https://img.shields.io/badge/Requires-Plasmo%20Voice%202.1.17%2B-red?style=flat-square)](https://modrinth.com/mod/plasmo-voice)

Speech-to-text captions above players' heads for Plasmo Voice, recognized by a local Whisper server (NPU, GPU or CPU).

Recognition picks the engine on its own: the Ryzen AI NPU when its server runs, otherwise Whisper on this PC's graphics card, downloaded and started by the mod.

## Русский

### Что это

`Voice Captions` (mod id `voicecaptions`) — клиентский Fabric-мод для Minecraft 26.3 и аддон Plasmo Voice. Он берёт уже декодированный голос других игроков, режет его на фразы и отправляет локальному серверу распознавания Whisper. Распознанный текст появляется «облаком» над головой говорящего.

Распознавание идёт на вашем ПК, движок выбирается сам (режим «Авто»):

1. Если запущен NPU-сервер FastFlowLM (ноутбук с Ryzen AI 300, например HX 370), речь идёт на NPU, и процессор с видеокартой остаются игре.
2. Иначе мод сам запускает Whisper на видеокарте: whisper.cpp с Vulkan, работает на NVIDIA, AMD и Intel. Движок и модель скачиваются один раз кнопкой в настройках.

Можно указать и свой сервер распознавания (режим «Свой сервер»).

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
- движок для видеокарты запускается при входе в мир и сразу «прогревается», поэтому первая фраза не ждёт загрузки модели; при выходе из игры процесс закрывается, а оставшийся после вылета процесс мод закрывает при следующем запуске;
- если NPU-сервер закрыли посреди игры, следующая фраза сама уходит на видеокарту;
- если распознавать нечем, в чате появляется одна понятная подсказка (не чаще раза в минуту), а мод делает паузу, вместо того чтобы долбить сервер запросами;
- текст и звук никуда не сохраняются.

### Управление

| Действие | Клавиша / команда |
|---|---|
| Включить / выключить субтитры | не назначена; Options → Controls → Voice Captions, или `/voicecaptions toggle` |
| Открыть настройки | `/voicecaptions` или Mod Menu |
| Проверить распознавание | `/voicecaptions check` или кнопка «Проверить сервер» (пишет, какой движок ответил и за сколько) |
| Состояние движка / скачать для видеокарты | `/voicecaptions engine`, `/voicecaptions engine install`, `/voicecaptions engine cancel` |
| Показать пример облака | `/voicecaptions test [текст]` (над ближайшим игроком, иначе над вами — смотрите через F5) |

### Настройки

Две вкладки: **Распознавание** (движок «Авто» или «Свой сервер», модель для видеокарты, кнопка «Скачать для видеокарты» с ходом загрузки, папка движка; для своего сервера — адрес и модель; язык, ожидание ответа, лимит очереди, пауза до конца фразы, максимальная длина фразы, «только слышимых»; внизу строка «Сейчас: …» показывает, чем распознаётся речь) и **Вид** (ширина облака, число строк, время показа, дальность, размер вблизи и вдали, высота над ником, непрозрачность фона, сквозь стены, точки «говорит», ник в облаке). У каждого поля есть подсказка. Файл `config/voicecaptions.json` (атомарная запись, резервная копия `.bak`).

### Установка

1. **Fabric Loader** `0.19.3+`, **Fabric API** `0.161.0+26.3` и **Plasmo Voice** `2.1.17+` для 26.3.
2. Добавьте `voice-captions-1.1.0.jar` из `build/libs/` в папку `mods/`.
3. Выберите, чем распознавать.

**Видеокарта (любой ПК с Windows x64):** откройте `/voicecaptions` → «Распознавание» → «Скачать для видеокарты». Мод скачает whisper.cpp 1.8.4 с Vulkan (21 МБ, github.com/lemonade-sdk) и модель (huggingface.co/ggerganov) в папку `voicecaptions/` игры, проверит SHA-256 и дальше будет запускать движок сам. Системный прокси Windows и переменные `HTTPS_PROXY`/`HTTP_PROXY` учитываются. Замер на RTX 5080:

| Модель | Размер | Видеопамять | Фраза 3–7 с | Качество русского |
|---|---|---|---|---|
| Turbo (по умолчанию) | 547 МБ | ≈ 0,9 ГБ | 60–120 мс | без ошибок на тестовых фразах |
| Base | 57 МБ | не замерялась | 30–55 мс | ошибается: «стою портала внизер», «крепер» |
| Small | 181 МБ | — | не замерялась | между ними |

Первая фраза после запуска движка занимает 5–8 секунд (Vulkan собирает шейдеры), поэтому мод делает её сам при входе в мир.

**NPU (Ryzen AI 300):**

1. Обновите драйвер NPU AMD до `32.0.203.311` или новее (Диспетчер устройств → Нейронные процессоры).
2. Установите FastFlowLM (`flm-setup.msi`, https://github.com/ROCm/FastFlowLM/releases).
3. В терминале:

```powershell
flm pull whisper-v3:turbo
flm serve --asr 1
```

Сервер слушает `127.0.0.1:52625`; режим «Авто» находит его сам и предпочитает видеокарте. Первая фраза после запуска распознаётся 10–15 секунд, пока грузится модель; дальше короткая фраза — примерно 1–2,5 секунды. FastFlowLM сам определяет язык и игнорирует поле «Язык».

**Свой сервер:** Lemonade Server (`http://127.0.0.1:13305/v1/audio/transcriptions`) или `whisper-server` из whisper.cpp (`http://127.0.0.1:8080/inference`) — переключите движок на «Свой сервер» и укажите адрес.

**Требования:** Minecraft `26.3` · Java `25` · только клиент · Plasmo Voice на сервере и у вас · Windows x64 с видеокартой (Vulkan) или NPU-сервер, либо свой сервер с API `/v1/audio/transcriptions`.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/voice-captions-1.1.0.jar`.

Живая проверка движка на этом ПК (скачивает файлы в указанную папку, нужны `ru.wav`, `short.wav`, `en.wav` 16 кГц моно):

```powershell
.\gradlew.bat test --tests "*LiveEngineTest" "-Dvoicecaptions.live=<папка>" "-Dvoicecaptions.live.models=base,turbo"
```

---

## English

### What It Is

`Voice Captions` (mod id `voicecaptions`) is a client-side Fabric mod for Minecraft 26.3 and a Plasmo Voice addon. It takes the already decoded voice of other players, cuts it into phrases and sends them to a local Whisper recognition server. The recognized text appears as a bubble above the speaker's head.

Recognition runs on your PC and the engine is chosen automatically ("Auto" mode): the FastFlowLM NPU server when it runs (Ryzen AI 300 laptops such as the HX 370), otherwise Whisper on this PC's graphics card — whisper.cpp with Vulkan (NVIDIA, AMD, Intel), downloaded once with a button in the settings and started by the mod itself. An "Own server" mode sends speech to any address you enter.

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
- the GPU engine starts and warms up when you join a world, so the first phrase does not wait for the model; it is closed on exit, and a process left behind by a crash is closed on the next start;
- if the NPU server is closed mid-game, the next phrase goes to the graphics card;
- if nothing can recognize speech, chat shows one clear hint (at most once a minute) and the mod backs off instead of flooding the server;
- no text or audio is saved anywhere.

### Controls

| Action | Key / command |
|---|---|
| Toggle captions | unbound; Options → Controls → Voice Captions, or `/voicecaptions toggle` |
| Open settings | `/voicecaptions` or Mod Menu |
| Check recognition | `/voicecaptions check` or the "Check server" button (shows which engine answered and how fast) |
| Engine status / download for GPU | `/voicecaptions engine`, `/voicecaptions engine install`, `/voicecaptions engine cancel` |
| Show a sample bubble | `/voicecaptions test [text]` (above the nearest player, otherwise above you — use F5) |

### Configuration

Two tabs: **Recognition** (engine "Auto" or "Own server", GPU model, "Download for GPU" with progress, engine folder; address and model for your own server; language, request timeout, queue limit, pause that ends a phrase, max phrase length, "only audible"; a "Now: …" line shows what recognizes speech) and **Appearance** (bubble width, line count, display time, max distance, near and far size, height above the name tag, background opacity, through walls, speaking dots, name in bubble). Every field has a tooltip. File `config/voicecaptions.json` (atomic write, `.bak` backup).

### Installation

1. **Fabric Loader** `0.19.3+`, **Fabric API** `0.161.0+26.3` and **Plasmo Voice** `2.1.17+` for 26.3.
2. Add `voice-captions-1.1.0.jar` from `build/libs/` to the `mods/` folder.
3. Choose what recognizes speech.

**Graphics card (any Windows x64 PC):** open `/voicecaptions` → Recognition → "Download for GPU". The mod downloads whisper.cpp 1.8.4 with Vulkan (21 MB, github.com/lemonade-sdk) and the model (huggingface.co/ggerganov) into the game's `voicecaptions/` folder, checks SHA-256 and starts the engine itself. The Windows system proxy and `HTTPS_PROXY`/`HTTP_PROXY` are honoured. Measured on an RTX 5080: Turbo (default, 547 MB, ≈0.9 GB VRAM) answers a 3–7 s phrase in 60–120 ms with no mistakes on the Russian test phrases; Base (57 MB) takes 30–55 ms but makes mistakes. The first phrase after the engine starts takes 5–8 s while Vulkan builds shaders, so the mod sends it itself when you join a world.

**NPU (Ryzen AI 300):** update the AMD NPU driver to `32.0.203.311` or newer, install FastFlowLM (`flm-setup.msi`, https://github.com/ROCm/FastFlowLM/releases), then run `flm pull whisper-v3:turbo` and `flm serve --asr 1`. It listens on `127.0.0.1:52625`; "Auto" mode finds it and prefers it over the graphics card. The first phrase after start takes 10–15 s while the model loads; after that a short phrase takes about 1–2.5 s. FastFlowLM detects the language itself and ignores the Language field.

**Own server:** Lemonade Server (`http://127.0.0.1:13305/v1/audio/transcriptions`) or whisper.cpp `whisper-server` (`http://127.0.0.1:8080/inference`) — switch the engine to "Own server" and enter the address.

**Requirements:** Minecraft `26.3` · Java `25` · client only · Plasmo Voice on the server and on your client · Windows x64 with a Vulkan graphics card or the NPU server, or your own server with a `/v1/audio/transcriptions` API.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/voice-captions-1.1.0.jar`.

## Лицензия / License

MIT
