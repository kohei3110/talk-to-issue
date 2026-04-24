package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportTranscriptTool {

    public record CompiledTranscript(String meetingTitle, String meetingDate, String transcript) {}

    private volatile CompiledTranscript compiledTranscript;

    public CompiledTranscript getCompiledTranscript() {
        return compiledTranscript;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "report_transcript",
            "Report the compiled transcript and related context back to the system. "
                + "Call this tool ONCE after you have gathered all meeting transcript data, "
                + "Teams chat messages, emails, and SharePoint documents. "
                + "Combine everything into a single comprehensive transcript.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "meetingTitle", Map.of("type", "string", "description", "Title of the meeting"),
                    "meetingDate", Map.of("type", "string", "description", "Date of the meeting (ISO 8601 or natural language)"),
                    "transcript", Map.of("type", "string", "description",
                        "The full compiled transcript. Include: meeting transcript/recording content, "
                            + "relevant Teams chat messages, related email threads, "
                            + "and any referenced SharePoint documents. "
                            + "Format as a structured text with clear sections.")
                ),
                "required", List.of("meetingTitle", "transcript")
            ),
            invocation -> {
                var args = invocation.getArguments();
                String meetingTitle = (String) args.get("meetingTitle");
                String meetingDate = (String) args.getOrDefault("meetingDate", "unknown");
                String transcript = (String) args.get("transcript");

                compiledTranscript = new CompiledTranscript(meetingTitle, meetingDate, transcript);

                System.out.println("Received transcript for meeting: " + meetingTitle
                    + " (" + transcript.length() + " chars)");

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "meetingTitle", meetingTitle,
                    "meetingDate", meetingDate,
                    "transcriptLength", transcript.length()
                ));
            }
        );
    }
}
