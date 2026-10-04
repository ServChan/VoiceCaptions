package org.lts.voicecaptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LanguageFilesTest {
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final Path LANG = Path.of("src/client/resources/assets/voicecaptions/lang");
    private static final Pattern KEY_LITERAL = Pattern.compile("\"(voicecaptions\\.[a-z0-9_.]+|key\\.voicecaptions\\.[a-z0-9_.]+)\"");

    @Test
    void allLocalesHaveTheSameKeys() throws IOException {
        Set<String> expected = read(LANG.resolve("en_us.json")).keySet();
        for (String locale : new String[]{"ru_ru.json", "es_es.json", "es_mx.json"}) {
            assertEquals(expected, read(LANG.resolve(locale)).keySet(), locale);
        }
    }

    @Test
    void everyKeyUsedInCodeExists() throws IOException {
        Set<String> keys = read(LANG.resolve("en_us.json")).keySet();
        try (Stream<Path> files = Files.walk(Path.of("src/client/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.equals("voicecaptions.json")) {
                        continue;
                    }
                    assertTrue(keys.contains(key), file.getFileName() + " uses missing key " + key);
                    if (key.startsWith("voicecaptions.option.")) {
                        assertTrue(keys.contains(key + ".tooltip"), "missing tooltip for " + key);
                    }
                }
            }
        }
    }

    private static Map<String, String> read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, MAP_TYPE);
        }
    }
}
