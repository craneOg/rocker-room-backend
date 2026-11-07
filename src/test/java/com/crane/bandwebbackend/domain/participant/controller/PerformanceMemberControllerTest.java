package com.crane.bandwebbackend.domain.participant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.participant.dto.ParticipantMutationResult;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.participant.service.MyParticipationReader;
import com.crane.bandwebbackend.domain.participant.service.ParticipantBulkDecisionService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantBulkService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantDecisionService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantImpactService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantListReader;
import com.crane.bandwebbackend.domain.participant.service.ParticipantRemovalService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.global.error.GlobalExceptionHandler;
import com.crane.bandwebbackend.global.config.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceMemberControllerTest {

	private final ParticipantRemovalService removal = mock(ParticipantRemovalService.class);
	private final CurrentActor actor = mock(CurrentActor.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PerformanceMemberController(
				mock(PerformanceMemberService.class),
				mock(ParticipantDecisionService.class),
				mock(ParticipantBulkService.class),
				mock(ParticipantImpactService.class),
				removal,
				mock(ParticipantBulkDecisionService.class),
				mock(ParticipantListReader.class),
				mock(MyParticipationReader.class),
				actor))
			.addFilters(new RequestIdFilter())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void withdrawAcceptsReasonWithoutPerformanceVersion() throws Exception {
		when(actor.requireUserId()).thenReturn(7L);
		when(removal.remove(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
				org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.eq(false)))
				.thenReturn(new ParticipantMutationResult(null, List.of(),
						new ParticipantMutationResult.Effects(List.of(), List.of(), List.of())));

		mvc.perform(post("/api/v1/clubs/1/performances/2/members/3/withdraw")
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"자진 철회\"}"))
				.andExpect(status().isOk());

		ArgumentCaptor<ParticipantRemovalRequest> body = ArgumentCaptor.forClass(ParticipantRemovalRequest.class);
		verify(removal).remove(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
				org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(7L), body.capture(),
				org.mockito.ArgumentMatchers.eq(false));
		assertThat(body.getValue().performanceVersion()).isNull();
		assertThat(body.getValue().reason()).isEqualTo("자진 철회");
	}

	@Test
	void rejectStillRequiresPerformanceVersion() throws Exception {
		mvc.perform(post("/api/v1/clubs/1/performances/2/members/3/reject")
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"승인 거절\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(removal);
	}
}
