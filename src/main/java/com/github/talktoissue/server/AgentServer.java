package com.github.talktoissue.server;

import io.javalin.Javalin;
import io.javalin.http.Context;

/**
 * HTTP server for receiving GitHub webhooks and exposing health endpoints.
 * Designed to run behind smee.io or ngrok for local development.
 */
public class AgentServer {

    private final Javalin app;
    private final EventRouter eventRouter;
    private final WebhookValidator webhookValidator;
    private final WorkQueue workQueue;

    public AgentServer(EventRouter eventRouter, WebhookValidator webhookValidator, WorkQueue workQueue) {
        this.eventRouter = eventRouter;
        this.webhookValidator = webhookValidator;
        this.workQueue = workQueue;

        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });

        registerRoutes();
    }

    private void registerRoutes() {
        app.get("/health", this::handleHealth);
        app.post("/webhooks/github", this::handleGitHubWebhook);
    }

    private void handleHealth(Context ctx) {
        int pending = workQueue.pendingTasks();
        ctx.json(java.util.Map.of(
            "status", "ok",
            "pending_tasks", pending
        ));
    }

    private void handleGitHubWebhook(Context ctx) {
        String signature = ctx.header("X-Hub-Signature-256");
        byte[] body = ctx.bodyAsBytes();

        if (webhookValidator != null && !webhookValidator.isValid(signature, body)) {
            System.err.println("[AgentServer] Invalid webhook signature, rejecting request");
            ctx.status(401).result("Invalid signature");
            return;
        }

        String eventType = ctx.header("X-GitHub-Event");
        if (eventType == null || eventType.isBlank()) {
            ctx.status(400).result("Missing X-GitHub-Event header");
            return;
        }

        String payload = ctx.body();
        System.out.println("[AgentServer] Received webhook: " + eventType);

        // Route asynchronously — return 202 immediately
        eventRouter.route(eventType, payload);
        ctx.status(202).result("Accepted");
    }

    public void start(int port) {
        app.start(port);
        System.out.println("[AgentServer] Listening on port " + port);
        System.out.println("[AgentServer] Webhook endpoint: POST http://localhost:" + port + "/webhooks/github");
        System.out.println("[AgentServer] Health check:     GET  http://localhost:" + port + "/health");
    }

    public void stop() {
        app.stop();
    }
}
