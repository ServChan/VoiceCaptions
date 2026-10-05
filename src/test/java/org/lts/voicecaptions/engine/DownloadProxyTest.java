package org.lts.voicecaptions.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DownloadProxyTest {
    @Test
    void parsesUrlsWithAndWithoutSchemeAndCredentials() {
        assertProxy("127.0.0.1", 7890, DownloadProxy.parse("http://127.0.0.1:7890"));
        assertProxy("proxy.lan", 3128, DownloadProxy.parse("proxy.lan:3128"));
        assertProxy("10.0.0.2", 8080, DownloadProxy.parse("http://user:secret@10.0.0.2:8080/"));
        assertTrue(DownloadProxy.parse("socks5://127.0.0.1:7891").isEmpty());
        assertTrue(DownloadProxy.parse("127.0.0.1").isEmpty());
        assertTrue(DownloadProxy.parse("host:99999").isEmpty());
        assertTrue(DownloadProxy.parse("  ").isEmpty());
    }

    @Test
    void prefersHttpsVariable() {
        Map<String, String> environment = Map.of(
                "HTTP_PROXY", "http://127.0.0.1:1111",
                "HTTPS_PROXY", "http://127.0.0.1:2222");
        assertProxy("127.0.0.1", 2222, DownloadProxy.fromEnvironment(environment));
        assertTrue(DownloadProxy.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void readsWindowsProxySettings() {
        assertProxy("127.0.0.1", 7890, DownloadProxy.parseWindows("0x1", "127.0.0.1:7890"));
        assertProxy("127.0.0.1", 7891, DownloadProxy.parseWindows("0x1", "http=127.0.0.1:7890;https=127.0.0.1:7891"));
        assertProxy("127.0.0.1", 7890, DownloadProxy.parseWindows("0x1", "http=127.0.0.1:7890;ftp=127.0.0.1:21"));
        assertTrue(DownloadProxy.parseWindows("0x0", "127.0.0.1:7890").isEmpty());
        assertTrue(DownloadProxy.parseWindows(null, null).isEmpty());
    }

    private static void assertProxy(String host, int port, Optional<InetSocketAddress> parsed) {
        assertTrue(parsed.isPresent());
        assertEquals(host, parsed.get().getHostString());
        assertEquals(port, parsed.get().getPort());
    }
}
