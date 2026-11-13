@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version >NUL 2>&1
if errorlevel 1 goto missingJava
"%JAVA_EXE%" "-Xmx64m" "-Xms64m" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
:missingJava
echo Set JAVA_HOME to an installed JDK 21, or put Java on PATH.
exit /b 1
