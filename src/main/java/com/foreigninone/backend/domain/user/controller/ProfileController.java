package com.foreigninone.backend.domain.user.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.user.dto.ProfileResponse;
import com.foreigninone.backend.domain.user.dto.ProfileUpdateRequest;
import com.foreigninone.backend.domain.user.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @RequestHeader(value = "X-Demo-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId
    ) {
        Long userId = paramUserId != null ? paramUserId : headerUserId;
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @RequestHeader(value = "X-Demo-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        Long userId = paramUserId != null ? paramUserId : headerUserId;
        ProfileResponse response = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "프로필이 성공적으로 수정되었습니다."));
    }
}
