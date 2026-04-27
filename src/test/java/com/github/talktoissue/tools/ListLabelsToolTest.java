package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListLabelsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @SuppressWarnings("unchecked")
    @Test
    void listsLabels() throws Exception {
        var label = mock(GHLabel.class);
        when(label.getName()).thenReturn("bug");
        when(label.getDescription()).thenReturn("Something broken");
        when(label.getColor()).thenReturn("d73a4a");

        var iterable = mock(PagedIterable.class);
        when(iterable.toList()).thenReturn(List.of(label));
        when(repository.listLabels()).thenReturn(iterable);

        var result = invokeAsList(Map.of());
        assertEquals(1, result.size());
        assertEquals("bug", result.get(0).get("name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void labelWithNullDescription() throws Exception {
        var label = mock(GHLabel.class);
        when(label.getName()).thenReturn("wontfix");
        when(label.getDescription()).thenReturn(null);
        when(label.getColor()).thenReturn("ffffff");

        var iterable = mock(PagedIterable.class);
        when(iterable.toList()).thenReturn(List.of(label));
        when(repository.listLabels()).thenReturn(iterable);

        var result = invokeAsList(Map.of());
        assertEquals("", result.get(0).get("description"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeAsList(Map<String, Object> args) throws Exception {
        var tool = new ListLabelsTool(repository);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (List<Map<String, Object>>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
