package com.passbook.service;

import java.math.BigDecimal;

public record Stats(int customers, int openAccounts, BigDecimal deposits, int pendingLoans, BigDecimal loanBook) {
}
