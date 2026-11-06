package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipDetailAccessTest {
    private final MembershipRepository repository = mock(MembershipRepository.class);
    private final MembershipService service = new MembershipService(repository, mock(CoreEntityLookupService.class));

    @Test
    void api21RegularMemberCanReadCompletedMembershipButNotPendingRecord() {
        Membership target = mock(Membership.class);
        Membership actor = mock(Membership.class);
        User targetUser = mock(User.class);
        when(target.getUser()).thenReturn(targetUser);
        when(targetUser.getId()).thenReturn(9L);
        when(repository.findByIdAndClub_Id(10L, 1L)).thenReturn(Optional.of(target));
        when(repository.findByClub_IdAndUser_Id(1L, 7L)).thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(actor.getMemberType()).thenReturn(MemberType.REGULAR);
        when(actor.getRole()).thenReturn(MembershipRole.MEMBER);
        when(target.getStatus()).thenReturn(MembershipStatus.ACTIVE);

        assertThat(service.detail(1L, 10L, 7L)).isSameAs(target);

        when(target.getStatus()).thenReturn(MembershipStatus.PENDING);
        assertThatThrownBy(() -> service.detail(1L, 10L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
