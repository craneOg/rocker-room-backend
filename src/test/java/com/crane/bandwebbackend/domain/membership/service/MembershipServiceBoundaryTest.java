package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipServiceBoundaryTest {
    private final MembershipRepository repository = mock(MembershipRepository.class);
    private final CoreEntityLookupService entityLookup = mock(CoreEntityLookupService.class);
    private final MembershipService service = new MembershipService(repository, entityLookup);

    @Test
    void mem01RejectsMissingClubBeforeLookingUpTheUser() {
        when(entityLookup.require(1L)).thenThrow(new ApiException(ApiErrorCode.NOT_FOUND,
                "동아리를 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.apply(1L, 7L, JoinSource.SEARCH, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ApiErrorCode.NOT_FOUND));
        verifyNoInteractions(repository);
    }

    @Test
    void mem01UsesTeamOneClubStateAndCurrentUserForJoinCodeApplication() {
        Club club = mock(Club.class);
        User user = mock(User.class);
        when(entityLookup.require(1L)).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(club.getJoinCode()).thenReturn("ABCD234567");
        when(entityLookup.requireActive(7L)).thenReturn(user);
        when(repository.findByClub_IdAndUser_Id(1L, 7L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.apply(1L, 7L, JoinSource.JOIN_CODE, "ABCD234567", "기타 김");

        assertThat(result.getClub()).isSameAs(club);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDING);
        verify(entityLookup).require(1L);
        verify(entityLookup).requireActive(7L);
    }

    @Test
    void mem01RejectsWrongCodeBeforeLookingUpTheUser() {
        Club club = mock(Club.class);
        when(entityLookup.require(1L)).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(club.getJoinCode()).thenReturn("ABCD234567");

        assertThatThrownBy(() -> service.apply(1L, 7L, JoinSource.JOIN_CODE, "WRONG23456", null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ApiErrorCode.NOT_FOUND));
        verifyNoInteractions(repository);
    }
}
