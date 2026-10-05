package org.lts.voicecaptions.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class DownloadProxy {
    private static final String INTERNET_SETTINGS =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";

    private DownloadProxy() {
    }

    public static ProxySelector selector() {
        Optional<InetSocketAddress> proxy = fromEnvironment(System.getenv());
        if (proxy.isEmpty() && EngineCatalog.isSupportedPlatform()) {
            proxy = fromWindowsSettings();
        }
        if (proxy.isEmpty()) {
            return java.net.http.HttpClient.Builder.NO_PROXY;
        }
        InetSocketAddress address = proxy.get();
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(new Proxy(Proxy.Type.HTTP, address));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress socket, IOException exception) {
            }
        };
    }

    static Optional<InetSocketAddress> fromEnvironment(Map<String, String> environment) {
        for (String name : new String[]{"HTTPS_PROXY", "https_proxy", "ALL_PROXY", "all_proxy", "HTTP_PROXY", "http_proxy"}) {
            Optional<InetSocketAddress> parsed = parse(environment.get(name));
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    static Optional<InetSocketAddress> parseWindows(String enabled, String server) {
        if (enabled == null || server == null || !enabled.strip().matches("0x0*1|1")) {
            return Optional.empty();
        }
        String value = server.strip();
        if (value.contains("=")) {
            String fallback = null;
            for (String part : value.split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String scheme = pair[0].strip().toLowerCase(Locale.ROOT);
                if (scheme.equals("https")) {
                    return parse(pair[1]);
                }
                if (scheme.equals("http") || fallback == null) {
                    fallback = pair[1];
                }
            }
            return parse(fallback);
        }
        return parse(value);
    }

    static Optional<InetSocketAddress> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.strip();
        if (value.toLowerCase(Locale.ROOT).startsWith("socks")) {
            return Optional.empty();
        }
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(value.substring(colon + 1));
            if (port < 1 || port > 65535) {
                return Optional.empty();
            }
            return Optional.of(InetSocketAddress.createUnresolved(value.substring(0, colon), port));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<InetSocketAddress> fromWindowsSettings() {
        return parseWindows(readRegistry("ProxyEnable"), readRegistry("ProxyServer"));
    }

    private static String readRegistry(String name) {
        try {
            Process process = new ProcessBuilder("reg", "query", INTERNET_SETTINGS, "/v", name)
                    .redirectErrorStream(true).start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            for (String line : output.split("\\R")) {
                String trimmed = line.strip();
                if (trimmed.startsWith(name)) {
                    String[] parts = trimmed.split("\\s+", 3);
                    return parts.length == 3 ? parts[2] : null;
                }
            }
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
