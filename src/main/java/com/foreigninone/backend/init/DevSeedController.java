package com.foreigninone.backend.init;

import com.foreigninone.backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevSeedController {

    private final DataInitializer dataInitializer;

    @PostMapping("/reset-seed")
    public ResponseEntity<ApiResponse<Void>> resetSeed() {
        dataInitializer.resetSeedData();
        return ResponseEntity.ok(ApiResponse.okMessage("시드 데이터가 성공적으로 재초기화되었습니다."));
    }
}
