package com.foreigninone.backend.domain.bank.controller;

import com.foreigninone.backend.domain.bank.dto.MockBankTransactionResponse;
import com.foreigninone.backend.domain.bank.service.MockBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/mock/bank")
@RequiredArgsConstructor
public class MockBankController {

    private final MockBankService mockBankService;

    @GetMapping("/transactions")
    public ResponseEntity<MockBankTransactionResponse> getTransactions(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        MockBankTransactionResponse response = mockBankService.getMockTransactions(userId, from, to);
        return ResponseEntity.ok(response);
    }
}
