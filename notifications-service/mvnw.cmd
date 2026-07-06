@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set BASE_DIR=%~dp0
set MAVEN_WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties

@REM Find java executable
if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    for %%i in (java.exe) do set JAVACMD=%%~$PATH:i
)

if not exist "%JAVACMD%" (
    echo ERROR: JAVA_HOME is not set and java.exe not found in PATH. >&2
    exit /b 1
)

@REM Download wrapper jar if missing
if not exist "%MAVEN_WRAPPER_JAR%" (
    for /f "tokens=2 delims==" %%a in ('findstr /i "wrapperUrl" "%MAVEN_WRAPPER_PROPERTIES%"') do set WRAPPER_URL=%%a
    echo Downloading Maven Wrapper from %WRAPPER_URL%
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%MAVEN_WRAPPER_JAR%'"
)

"%JAVACMD%" ^
  -classpath "%MAVEN_WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain ^
  %*

endlocal
