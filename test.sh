#!/usr/bin/env bash
# Runs all tests (no database jar needed: they use the in-memory store).
set -e
cd "$(dirname "$0")"
rm -rf out-test && mkdir out-test
javac -d out-test $(find src -name '*.java')
java -cp out-test com.passbook.tests.AllTests
