package com.passbook.service;

import java.time.LocalDateTime;

public record BankEvent(String actor, String action, String detail, LocalDateTime at) {
}
