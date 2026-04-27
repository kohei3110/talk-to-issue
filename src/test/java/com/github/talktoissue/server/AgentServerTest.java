package com.github.talktoissue.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServerTest {

    @Mock EventRouter eventRouter;
    @Mock WebhookValidator webhookValidator;
    @Mock WorkQueue workQueue;

    private AgentServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void healthEndpointReturns200() throws Exception {
        when(workQueue.pendingTasks()).thenReturn(0);
        server = new AgentServer(eventRouter, webhookValidator, workQueue);
        server.start(0); // random port

        // We need to get the actual port - Javalin doesn't expose it easily through the public API
        // Instead, test at a known port
        server.stop();

        server = new AgentServer(eventRouter, webhookValidator, workQueue);
        int port = 18080 + (int)(Math.random() * 1000);
        server.start(port);

        var url = URI.create("http://localhost:" + port + "/health").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    void webhookRejectsInvalidSignature() throws Exception {
        when(webhookValidator.isValid(any(), any())).thenReturn(false);
        server = new AgentServer(eventRouter, webhookValidator, workQueue);
        int port = 18080 + (int)(Math.random() * 1000);
        server.start(port);

        var url = URI.create("http://localhost:" + port + "/webhooks/github").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("X-Hub-Signature-256", "sha256=invalid");
        conn.setRequestProperty("X-GitHub-Event", "issues");
        conn.getOutputStream().write("{}".getBytes());
        assertEquals(401, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    void webhookRejectsMissingEventHeader() throws Exception {
        when(webhookValidator.isValid(any(), any())).thenReturn(true);
        server = new AgentServer(eventRouter, webhookValidator, workQueue);
        int port = 18080 + (int)(Math.random() * 1000);
        server.start(port);

        var url = URI.create("http://localhost:" + port + "/webhooks/github").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("X-Hub-Signature-256", "sha256=valid");
        conn.getOutputStream().write("{}".getBytes());
        assertEquals(400, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    void webhookAcceptsValidRequest() throws Exception {
        when(webhookValidator.isValid(any(), any())).thenReturn(true);
        server = new AgentServer(eventRouter, webhookValidator, workQueue);
        int port = 18080 + (int)(Math.random() * 1000);
        server.start(port);

        var url = URI.create("http://localhost:" + port + "/webhooks/github").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("X-Hub-Signature-256", "sha256=valid");
        conn.setRequestProperty("X-GitHub-Event", "issues");
        conn.getOutputStream().write("{\"action\":\"opened\"}".getBytes());
        assertEquals(202, conn.getResponseCode());
        conn.disconnect();

        verify(eventRouter).route(eq("issues"), contains("opened"));
    }

    @Test
    void webhookAllowedWithNullValidator() throws Exception {
        server = new AgentServer(eventRouter, null, workQueue);
        int port = 18080 + (int)(Math.random() * 1000);
        server.start(port);

        var url = URI.create("http://localhost:" + port + "/webhooks/github").toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("X-GitHub-Event", "push");
        conn.getOutputStream().write("{}".getBytes());
        assertEquals(202, conn.getResponseCode());
        conn.disconnect();
    }
}
