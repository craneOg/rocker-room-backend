package com.crane.bandwebbackend.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class UserProfileImageTest {

    private static User of(long id, boolean imagePublic, boolean superAdmin) {
        User u = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "profileImageUrl", "https://img/" + id);
        ReflectionTestUtils.setField(u, "profileImagePublic", imagePublic);
        ReflectionTestUtils.setField(u, "isSuperAdmin", superAdmin);
        return u;
    }

    @Test
    @DisplayName("프로필 사진 비공개면 임원 포함 다른 사용자에게 NULL, 본인·Super Admin은 보임")
    void hiddenUnlessSelfOrSuperAdmin() {
        User owner = of(1, false, false);
        assertThat(owner.profileImageUrlFor(of(2, true, false))).isNull();
        assertThat(owner.profileImageUrlFor(owner)).isEqualTo("https://img/1");
        assertThat(owner.profileImageUrlFor(of(3, true, true))).isEqualTo("https://img/1");
        assertThat(of(4, true, false).profileImageUrlFor(of(2, true, false))).isEqualTo("https://img/4");
    }
}
