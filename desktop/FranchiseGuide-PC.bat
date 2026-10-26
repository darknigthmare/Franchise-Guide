@echo off
setlocal
cd /d "%~dp0"
where javaw >nul 2>nul
if %errorlevel%==0 (
  start "FranchiseGuide" javaw -jar "dist\FranchiseGuide-PC.jar"
  exit /b 0
)
where java >nul 2>nul
if %errorlevel%==0 (
  java -jar "dist\FranchiseGuide-PC.jar"
  exit /b %errorlevel%
)
echo Java 17 ou plus recent est requis.
pause
