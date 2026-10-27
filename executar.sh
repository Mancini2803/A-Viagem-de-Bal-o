#!/bin/sh
# Abre o jogo (precisa de Java 11+). Gere o jar antes com: ./gradlew lwjgl3:dist
cd "$(dirname "$0")/lwjgl3/build/libs" || exit 1
case "$(uname)" in
  Darwin) exec java -XstartOnFirstThread -jar EscaladaEspacial.jar ;;
  *) exec java -jar EscaladaEspacial.jar ;;
esac
