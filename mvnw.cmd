@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "BASE=%~dp0"
set "PROPS=%BASE%.mvn\wrapper\maven-wrapper.properties"
for /f "tokens=1,* delims==" %%A in (%PROPS%) do (
  if "%%A"=="distributionUrl" set "URL=%%B"
  if "%%A"=="distributionSha256Sum" set "EXPECTED=%%B"
)
set "VER=3.9.16"
if "%MAVEN_USER_HOME%"=="" (set "MUH=%USERPROFILE%\.m2") else (set "MUH=%MAVEN_USER_HOME%")
set "DEST=%MUH%\wrapper\dists\perubilling-maven-%VER%"
set "MHOME=%DEST%\apache-maven-%VER%"
set "ZIP=%DEST%\apache-maven-%VER%-bin.zip"
if not exist "%MHOME%\bin\mvn.cmd" (
  if not exist "%DEST%" mkdir "%DEST%"
  if not exist "%ZIP%" powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP%'"
  for /f %%H in ('powershell -NoProfile -Command "(Get-FileHash '%ZIP%' -Algorithm SHA256).Hash.ToLower()"') do set "ACTUAL=%%H"
  if /I not "!ACTUAL!"=="%EXPECTED%" (echo Checksum Maven invalido 1>&2 & del /q "%ZIP%" & exit /b 1)
  powershell -NoProfile -Command "Expand-Archive -Force '%ZIP%' '%DEST%'"
)
call "%MHOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
