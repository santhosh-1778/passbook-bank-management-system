@echo off
cd /d "%~dp0"
if exist out-test rmdir /s /q out-test
mkdir out-test
dir /s /b src\*.java > sources.txt
javac -d out-test @sources.txt
java -cp out-test com.passbook.tests.AllTests
