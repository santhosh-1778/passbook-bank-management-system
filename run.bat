@echo off
rem Builds and starts Passbook without Maven. Needs JDK 17+ and internet the first time (downloads 2 jars).
cd /d "%~dp0"
if not exist lib mkdir lib
if not exist out mkdir out
if not exist lib\sqlite-jdbc.jar powershell -Command "Invoke-WebRequest https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar -OutFile lib\sqlite-jdbc.jar"
if not exist lib\slf4j-api.jar powershell -Command "Invoke-WebRequest https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar -OutFile lib\slf4j-api.jar"
dir /s /b src\main\java\*.java > sources.txt
javac -d out @sources.txt
java -cp "out;lib\*" com.passbook.App
