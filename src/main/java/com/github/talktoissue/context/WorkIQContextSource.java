package com.github.talktoissue.context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches context from Microsoft Work IQ (Teams meetings, emails, documents, etc.)
 * by invoking the WorkIQ CLI as a subprocess.
 */
public class WorkIQContextSource implements ContextSource {

    private static final long WORKIQ_TIMEOUT_SECONDS = 180;

    private final String query;
    private final String tenantId;

    public WorkIQContextSource(String query, String tenantId) {
        this.query = query;
        this.tenantId = tenantId;
    }

    @Override
    public String getType() {
        return "workiq";
    }

    @Override
    public String describe() {
        return "Work IQ query: " + query;
    }

    @Override
    public String fetch() throws Exception {
        acceptEula();
        return runWorkIqQuery(query);
    }

    private String runWorkIqQuery(String query) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
            "npx", "-y", "@microsoft/workiq@latest", "ask", "-q", query
        ));
        if (tenantId != null && !tenantId.isBlank()) {
            command.add("--tenant-id");
            command.add(tenantId);
        }

        var pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        var process = pb.start();

        var output = new StringBuilder();
        var is = process.getInputStream();
        var buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            String chunk = new String(buf, 0, len);
            System.out.print(chunk);
            System.out.flush();
            output.append(chunk);
        }

        boolean finished = process.waitFor(WORKIQ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "Work IQ query timed out after " + WORKIQ_TIMEOUT_SECONDS + " seconds.";
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            System.err.println("[Work IQ] Exit code: " + exitCode);
            return "Work IQ query failed (exit code " + exitCode + "): " + output;
        }

        System.out.println("\n[Work IQ] Response length: " + output.length() + " chars");
        return output.toString();
    }

    private void acceptEula() throws IOException, InterruptedException {
        System.out.println("Accepting Work IQ EULA...");
        List<String> command = new ArrayList<>(List.of("npx", "-y", "@microsoft/workiq@latest", "accept-eula"));
        if (tenantId != null && !tenantId.isBlank()) {
            command.add("--tenant-id");
            command.add(tenantId);
        }

        var process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("Warning: Work IQ EULA acceptance returned exit code " + exitCode);
            System.err.println(output);
        } else {
            System.out.println("Work IQ EULA accepted.");
        }
    }
}
