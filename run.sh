#!/usr/bin/env bash
# Builds and starts Passbook without Maven. Needs JDK 17+ and internet the first time (downloads 2 jars).
set -e
cd "$(dirname "$0")"
mkdir -p lib out
[ -f lib/sqlite-jdbc.jar ] || curl -fL -o lib/sqlite-jdbc.jar https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar
[ -f lib/slf4j-api.jar ]   || curl -fL -o lib/slf4j-api.jar   https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar
javac -d out $(find src/main/java -name '*.java')
java -cp "out:lib/*" com.passbook.App
