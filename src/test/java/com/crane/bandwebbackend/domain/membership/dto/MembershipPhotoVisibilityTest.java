package com.crane.bandwebbackend.domain.membership.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipPhotoVisibilityTest {
    @Test
    void u13ShowsPublicPhotoAndHidesPrivatePhotoFromOthers() {
        User user = User.createFromProvider("부원", "https://img.example/photo");
        ReflectionTestUtils.setField(user, "id", 7L);
        Membership membership = mock(Membership.class);
        Club club = mock(Club.class);
        when(membership.getId()).thenReturn(3L);
        when(membership.getClub()).thenReturn(club);
        when(club.getId()).thenReturn(1L);
        when(membership.getUser()).thenReturn(user);

        assertThat(MembershipResponse.from(membership, List.of(), 9L).profileImageUrl())
                .isEqualTo("https://img.example/photo");
        assertThat(MembershipPublicItem.from(membership, List.of(), 9L).profileImageUrl())
                .isEqualTo("https://img.example/photo");

        user.changeProfileImagePublic(false);
        assertThat(MembershipResponse.from(membership, List.of(), 9L).profileImageUrl())
                .isNull();
        assertThat(MembershipPublicItem.from(membership, List.of(), 9L).profileImageUrl())
                .isNull();
        assertThat(MembershipResponse.from(membership, List.of(), 7L).profileImageUrl())
                .isEqualTo("https://img.example/photo");
    }
}
