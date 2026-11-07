package com.passbook.service;

/** loginId and tempPassword are null when the account was added to an existing customer. */
public record OpenedAccount(String accountNo, long customerId, String loginId, String tempPassword) {
}
