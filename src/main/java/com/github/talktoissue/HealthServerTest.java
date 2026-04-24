package com.github.talktoissue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

public class HealthServerTest {
    private static HealthServer server;
    private static final int PORT = 18080;

    @BeforeAll
    public static void setUp() throws IOException {
        server = new HealthServer(PORT);
        server.start();
    }

    @AfterAll
    public static void tearDown() {
        server.stop(0);
    }

    @Test
    public void testHealthEndpoint() throws IOException {
        URL url = new URL("http://localhost:" + PORT + "/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode);
        String contentType = conn.getHeaderField("Content-Type");
        assertTrue(contentType.contains("application/json"));
        byte[] response = conn.getInputStream().readAllBytes();
        assertEquals("{\"status\": \"healthy\"}", new String(response));
    }
}
