@ECHO OFF
REM Invocation of fshgen command-line interface on Windows.
REM In Windows cmd.exe, call:
REM
REM     fshgen --help
REM
REM In Windows PowerShell, call:
REM
REM     .\fshgen --help

SET SCRIPTDIR=%~dp0.

java -jar -Djava.awt.headless=true "%SCRIPTDIR%\fshgen.jar" %*
