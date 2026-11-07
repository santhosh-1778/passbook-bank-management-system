package com.passbook.service;

import com.passbook.model.Loan;

public record LoanView(Loan loan, String ownerName) {
}
