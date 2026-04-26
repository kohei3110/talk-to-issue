package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportVerificationTool {

    public record TestFailure(String testName, String message, String file) {}

    public record VerificationResult(
        boolean buildSuccess,
        boolean testsSuccess,
        String buildOutput,
        String testOutput,
        List<TestFailure> failures,
        List<String> suggestions
    ) {}

    private volatile VerificationResult verificationResult;

    public VerificationResult getVerificationResult() {
        return verificationResult;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.create(
            "report_verification",
            "Report the build and test verification results. "
                + "Call this tool ONCE after running build and test commands and analyzing the output.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "buildSuccess", Map.of("type", "boolean",
                        "description", "Whether the build (compile) succeeded"),
                    "testsSuccess", Map.of("type", "boolean",
                        "description", "Whether all tests passed (true if no tests exist)"),
                    "buildOutput", Map.of("type", "string",
                        "description", "Relevant build output (errors, warnings). Keep concise — include only error messages."),
                    "testOutput", Map.of("type", "string",
                        "description", "Relevant test output (failures, errors). Keep concise."),
                    "failures", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "testName", Map.of("type", "string", "description", "Name of the failing test"),
                            "message", Map.of("type", "string", "description", "Error message or assertion failure"),
                            "file", Map.of("type", "string", "description", "File path of the failing test")
                        ),
                        "required", List.of("testName", "message")
                    ), "description", "List of individual test failures"),
                    "suggestions", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "Suggestions for fixing the failures")
                ),
                "required", List.of("buildSuccess", "testsSuccess", "buildOutput", "testOutput", "failures", "suggestions")
            ),
            invocation -> {
                var args = invocation.getArguments();
                boolean buildSuccess = (Boolean) args.get("buildSuccess");
                boolean testsSuccess = (Boolean) args.get("testsSuccess");
                String buildOutput = (String) args.get("buildOutput");
                String testOutput = (String) args.get("testOutput");

                List<Map<String, Object>> rawFailures = (List<Map<String, Object>>) args.get("failures");
                List<TestFailure> failures = rawFailures.stream()
                    .map(f -> new TestFailure(
                        (String) f.get("testName"),
                        (String) f.get("message"),
                        (String) f.getOrDefault("file", "")
                    ))
                    .toList();

                List<String> suggestions = (List<String>) args.get("suggestions");

                verificationResult = new VerificationResult(
                    buildSuccess, testsSuccess, buildOutput, testOutput, failures, suggestions
                );

                String buildIcon = buildSuccess ? "✓" : "✗";
                String testIcon = testsSuccess ? "✓" : "✗";
                System.out.println("[Verification] Build: " + buildIcon + " | Tests: " + testIcon);
                if (!failures.isEmpty()) {
                    System.out.println("  Failures:");
                    for (var f : failures) {
                        System.out.println("    - " + f.testName() + ": " + f.message());
                    }
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "buildSuccess", buildSuccess,
                    "testsSuccess", testsSuccess,
                    "failureCount", failures.size()
                ));
            }
        );
    }
}
