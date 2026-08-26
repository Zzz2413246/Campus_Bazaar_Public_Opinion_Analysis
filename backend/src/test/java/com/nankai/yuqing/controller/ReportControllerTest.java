package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    @Test
    void dailyReportListKeepsEmptyCalendarDaysAtZeroWithoutBorrowingOldEvents() {
        PostRepository posts = mock(PostRepository.class);
        EventRepository events = mock(EventRepository.class);
        Post post = new Post();
        post.setId("p-1");
        post.setPublishTime(LocalDateTime.parse("2026-07-15T12:00:00"));
        EventEntity event = new EventEntity();
        event.setId("e-1");
        event.setCreatedAt(LocalDateTime.parse("2026-07-15T12:00:00"));
        event.setRisk("中");
        when(posts.findAll()).thenReturn(List.of(post));
        when(events.findAll()).thenReturn(List.of(event));

        List<Map<String, Object>> reports = new ReportController(posts, events).list("daily");

        assertEquals(7, reports.size());
        assertEquals(LocalDate.now().toString(), reports.get(0).get("date"));
        assertEquals(0L, reports.get(0).get("newPosts"));
        assertEquals(0, reports.get(0).get("events"));
        assertEquals(0L, reports.get(0).get("highRisk"));
    }
}
