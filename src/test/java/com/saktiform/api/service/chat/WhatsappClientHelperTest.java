package com.saktiform.api.service.chat;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** gowa menolak multipart chunked ("multipart: NextPart: EOF"); request wajib ber-Content-Length. */
class WhatsappClientHelperTest {

    private HttpServer server;
    private final AtomicReference<Headers> headers = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private WhatsappClientHelper client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            var out = new ByteArrayOutputStream();
            exchange.getRequestBody().transferTo(out);
            headers.set(exchange.getRequestHeaders());
            body.set(out.toByteArray());

            var resp = "{\"code\":\"SUCCESS\",\"results\":{\"message_id\":\"m1\",\"status\":\"ok\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        client = new WhatsappClientHelper();
        ReflectionTestUtils.setField(client, "username", "u");
        ReflectionTestUtils.setField(client, "password", "p");
        ReflectionTestUtils.setField(client, "apiMultiDeviceUrl", "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendImageUsesContentLengthNotChunked() {
        var response = client.sendImage("dev", "628123", "Halo 🙏", "https://cdn/produk-media/media/a.webp");

        assertEquals("SUCCESS", response.getCode());
        assertNull(headers.get().getFirst("Transfer-Encoding"));
        assertEquals(String.valueOf(body.get().length), headers.get().getFirst("Content-Length"));

        var text = new String(body.get(), StandardCharsets.UTF_8);
        assertTrue(text.contains("name=\"image_url\""));
        assertTrue(text.contains("Halo 🙏"));
        assertTrue(text.trim().endsWith("--"));
    }
}
