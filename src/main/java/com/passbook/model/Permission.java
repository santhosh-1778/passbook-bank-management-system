package com.passbook.model;

/** What a user may do. Roles are just named bundles of permissions. */
public enum Permission {
    SELF_SERVICE,        // customer: use own accounts, transfer, apply for / repay loans
    SERVE_ANY_ACCOUNT,   // staff: view and operate any account
    OPEN_ACCOUNT,
    MANAGE_CUSTOMERS,
    CLOSE_ACCOUNT,
    FREEZE_ACCOUNT,      // manager only
    APPROVE_LOAN,        // manager only
    MANAGE_STAFF,        // manager only
    CREDIT_INTEREST,     // manager only
    VIEW_AUDIT           // manager only
}
