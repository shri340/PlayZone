@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
echo Compiling PlayZone Java files...
javac -encoding UTF-8 -d out src\playzone\Main.java src\playzone\model\*.java src\playzone\people\*.java src\playzone\exceptions\*.java src\playzone\service\*.java src\playzone\web\*.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Check the Java errors above.
  pause
  exit /b 1
)
echo.
echo Starting PlayZone. Open http://localhost:8080 in your browser.
echo For other devices on the same Wi-Fi, use this laptop's IPv4 address and :8080.
java -Dfile.encoding=UTF-8 -cp out playzone.web.PlayZoneWebServer
pause
