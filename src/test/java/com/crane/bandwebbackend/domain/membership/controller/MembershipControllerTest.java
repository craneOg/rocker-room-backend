package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.dto.MembershipPublicItem;
import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.membership.service.MembershipStatusService;
import com.crane.bandwebbackend.domain.membership.service.MembershipDecisionService;
import com.crane.bandwebbackend.domain.membership.service.MemberDirectoryService;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalPreviewService;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalService;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ApiExceptionHandler;
import com.crane.bandwebbackend.global.config.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MembershipControllerTest {
    private final MembershipService service = mock(MembershipService.class);
    private final MembershipStatusService statusService = mock(MembershipStatusService.class);
    private final MembershipDecisionService decisionService = mock(MembershipDecisionService.class);
    private final MembershipPartReader partReader = mock(MembershipPartReader.class);
    private final MemberDirectoryService directory = mock(MemberDirectoryService.class);
    private final MembershipWithdrawalPreviewService withdrawalPreview = mock(MembershipWithdrawalPreviewService.class);
    private final MembershipWithdrawalService withdrawalService = mock(MembershipWithdrawalService.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MembershipController(service, statusService, decisionService, partReader,
                            directory, withdrawalPreview, withdrawalService, actor))
            .addFilters(new RequestIdFilter())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @BeforeEach
    void actorId() {
        when(actor.requireUserId()).thenReturn(7L);
    }

    @Test
    void api21RegularMemberSeesOnlyPublicFieldsOfAnotherMember() throws Exception {
        Membership target = mock(Membership.class);
        Membership viewer = mock(Membership.class);
        User user = mock(User.class);
        when(target.getId()).thenReturn(10L);
        when(target.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(9L);
        when(user.getName()).thenReturn("다른 부원");
        MembershipPublicItem publicItem = MembershipPublicItem.from(target, List.of(), 7L);
        when(directory.detail(1L, 10L, 7L)).thenReturn(publicItem);
        when(viewer.getMemberType()).thenReturn(MemberType.REGULAR);
        when(viewer.getRole()).thenReturn(MembershipRole.MEMBER);
        when(partReader.partsOf(10L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/clubs/1/memberships/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("다른 부원"))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.handle").doesNotExist());
    }

    @Test
    void mem03ReadsActorFromAuthenticationPortAndSharesRequestId() throws Exception {
        Membership membership = mock(Membership.class);
        Club club = mock(Club.class);
        User user = mock(User.class);
        when(membership.getId()).thenReturn(10L);
        when(membership.getClub()).thenReturn(club);
        when(membership.getUser()).thenReturn(user);
        when(membership.getMemberType()).thenReturn(MemberType.REGULAR);
        when(membership.getRole()).thenReturn(MembershipRole.MEMBER);
        when(membership.getStatus()).thenReturn(MembershipStatus.PENDING);
        when(membership.getJoinSource()).thenReturn(JoinSource.SEARCH);
        when(membership.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 1, 0, 0));
        when(membership.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 10, 1, 0, 0));
        when(club.getId()).thenReturn(1L);
        when(user.getId()).thenReturn(7L);
        when(user.getName()).thenReturn("테스트 부원");
        MembershipResponse ownItem = MembershipResponse.from(membership, List.of(), 7L);
        when(directory.detail(1L, 10L, 7L)).thenReturn(ownItem);
        when(partReader.partsOf(10L)).thenReturn(List.of());

        var result = mvc.perform(get("/api/v1/clubs/1/memberships/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("10"))
                .andExpect(jsonPath("$.data.displayName").value("테스트 부원"))
                .andReturn();

        String headerId = result.getResponse().getHeader("X-Request-Id");
        assertThat(headerId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + headerId + "\"");
        verify(directory).detail(1L, 10L, 7L);
    }

    @Test
    void api160RequiresCsrfHeaderBeforeStatusChange() throws Exception {
        mvc.perform(post("/api/v1/clubs/1/memberships/10/take-leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
