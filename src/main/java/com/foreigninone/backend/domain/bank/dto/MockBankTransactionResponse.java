package com.foreigninone.backend.domain.bank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockBankTransactionResponse {
    private String apiTranId;
    private String rspCode;
    private String rspMessage;
    private List<MockBankTransactionItem> resList;
}
