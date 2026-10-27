@echo off
rem Abre o jogo (precisa de Java 11+ instalado). Execute depois de gerar o jar com: gradlew lwjgl3:dist
cd /d "%~dp0lwjgl3\build\libs"
java -jar EscaladaEspacial.jar
pause
