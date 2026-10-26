@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
 echo Node 22 ou plus recent est requis.
 pause
 exit /b 1
)
node -e "if(Number(process.versions.node.split('.')[0])<22){console.error('Node 22+ requis');process.exit(1)}"
if errorlevel 1 exit /b 1
node server\start.cjs
pause
