package com.passbook.web;

import com.passbook.model.User;

import java.time.LocalDate;
import java.util.List;

/** Everything a view needs to know about the current request. user is null on public pages. */
public record ViewContext(User user, String csrf, String path, List<Flash> flashes, LocalDate today) {
}
