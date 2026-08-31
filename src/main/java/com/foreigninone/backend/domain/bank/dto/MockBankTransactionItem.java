package com.foreigninone.backend.domain.bank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockBankTransactionItem {
    private String bankTranId;
    private String bankTranDate;
    private String tranTime;
    private String inoutType;
    private String tranType;
    private String printedContent;
    private String tranAmt;
    private String afterBalanceAmt;
    private String branchName;
    private String bankName;
    private String fintechUseNum;
}
