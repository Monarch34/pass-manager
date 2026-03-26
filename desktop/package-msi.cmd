@echo off
setlocal
cd /d "%~dp0"

rem Isolated Gradle user home so %USERPROFILE%\.gradle\gradle.properties (e.g. org.gradle.java.home=Studio JBR)
rem does not override desktop\gradle.properties. Does not change system JAVA_HOME or user env.
if not exist "%~dp0.gradle-packaging-userhome" mkdir "%~dp0.gradle-packaging-userhome"
set "GRADLE_USER_HOME=%~dp0.gradle-packaging-userhome"

rem Requires desktop\gradle.properties with org.gradle.java.home pointing at a full JDK 17 (see gradle.properties.example).
call gradlew.bat --stop 2>nul
call gradlew.bat packageMsi %*
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%
