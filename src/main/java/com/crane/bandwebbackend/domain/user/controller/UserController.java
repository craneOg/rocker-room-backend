package com.crane.bandwebbackend.domain.user.controller;

import com.crane.bandwebbackend.domain.user.dto.HandleAvailabilityResponse;
import com.crane.bandwebbackend.domain.user.dto.ImageUploadResponse;
import com.crane.bandwebbackend.domain.user.dto.UpdateProfileRequest;
import com.crane.bandwebbackend.domain.user.dto.UserProfileResponse;
import com.crane.bandwebbackend.domain.user.service.ProfileImageService;
import com.crane.bandwebbackend.domain.user.service.UserProfileService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final ProfileImageService profileImageService;

    // API 7
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userProfileService.getMe(principal.userId());
    }

    // API 8
    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserProfileResponse updateMe(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.update(principal.userId(), request);
    }

    // API 129: 사진을 임시 위치에 올리고 임시 키를 돌려준다. 프로필에는 API 8로 키를 보내 연결한다
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse uploadProfileImage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose) {
        return profileImageService.upload(principal.userId(), file, purpose);
    }

    // API 128: 입력을 멈춘 뒤 확인하는 참고용 API. 저장 때(API 8) 서버가 다시 검사한다
    @GetMapping("/users/handle-availability")
    public HandleAvailabilityResponse handleAvailability(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @Pattern(regexp = "^[a-z0-9_]{5,30}$") String handle) {
        return userProfileService.checkHandle(principal.userId(), handle);
    }
}
