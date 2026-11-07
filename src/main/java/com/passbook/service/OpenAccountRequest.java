package com.passbook.service;

import java.math.BigDecimal;

/** Either existingCustomerId or newCustomer must be set. */
public record OpenAccountRequest(String type, BigDecimal opening, BigDecimal overdraftLimit,
                                 Long existingCustomerId, CustomerDetails newCustomer) {
}
