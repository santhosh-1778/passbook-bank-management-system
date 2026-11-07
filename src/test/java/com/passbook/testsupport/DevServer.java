package com.passbook.testsupport;

import com.passbook.App;
import com.passbook.web.BankServer;

import java.nio.file.Path;

/** Runs the whole site on the in-memory store (no SQLite jar needed). For quick UI checks only. */
public final class DevServer {
    public static void main(String[] args) throws Exception {
        BankServer s = App.build(new MemoryRepositories().create(), Path.of("static"));
        int port = s.start("127.0.0.1", Integer.parseInt(args.length > 0 ? args[0] : "8081"));
        System.out.println("DevServer on " + port);
    }
}
