package com.crane.bandwebbackend.domain.part.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.service.PartService;
import com.crane.bandwebbackend.global.error.ApiExceptionHandler;
import com.crane.bandwebbackend.global.config.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PartControllerBoundaryTest {
    private final PartService service = mock(PartService.class);
    private final CurrentActor currentActor = mock(CurrentActor.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PartController(service, currentActor))
            .addFilters(new RequestIdFilter()).setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void part02PassesAuthenticatedActorIdAndClubBoundaryToService() throws Exception {
        Club club = mock(Club.class);
        Part part = mock(Part.class);
        when(currentActor.requireUserId()).thenReturn(7L);
        when(service.create(1L, 7L, "색소폰", null)).thenReturn(part);
        when(part.getId()).thenReturn(2L);
        when(part.getClub()).thenReturn(club);
        when(club.getId()).thenReturn(1L);
        when(part.getName()).thenReturn("색소폰");
        when(part.isActive()).thenReturn(true);
        when(part.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 0, 0));

        mvc.perform(post("/api/v1/clubs/1/parts")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"색소폰\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("2"))
                .andExpect(jsonPath("$.data.isActive").value(true));

        verify(service).create(1L, 7L, "색소폰", null);
    }
}
