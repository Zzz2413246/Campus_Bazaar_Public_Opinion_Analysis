package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.repository.EventActionRepository;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventControllerTest {

    @Test
    void staleUpdateReturnsConflictWithoutOverwritingEvent() {
        EventRepository eventRepository = mock(EventRepository.class);
        EventActionRepository actionRepository = mock(EventActionRepository.class);
        EventController controller = new EventController(
            eventRepository,
            actionRepository,
            mock(PostRepository.class),
            null
        );
        EventEntity event = new EventEntity();
        event.setId("event-1");
        event.setUpdatedAt(LocalDateTime.parse("2026-07-25T12:00:00"));
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));

        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> controller.updateStatus("event-1", Map.of(
                "status", "处理中",
                "expectedUpdatedAt", "2026-07-25T11:59:59"
            ))
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(eventRepository, never()).save(event);
        verify(actionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
