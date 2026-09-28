#!/usr/bin/env bash

# Resolve APP_HOME
PRG="$0"
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done

APP_HOME="$(cd "$(dirname "$PRG")" >/dev/null && pwd)"

# Find Java
if [ -d "$HOME/.jdks/jdk-17" ]; then
    export JAVA_HOME="$HOME/.jdks/jdk-17"
    JAVACMD="$JAVA_HOME/bin/java"
elif [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/bin/java" ]; then
        JAVACMD="$JAVA_HOME/bin/java"
    elif [ -x "$JAVA_HOME/bin/java.exe" ]; then
        JAVACMD="$JAVA_HOME/bin/java.exe"
    else
        JAVACMD="java"
    fi
elif [ -f "/mnt/c/Program Files/Android/Android Studio/jbr/bin/java.exe" ]; then
    JAVACMD="/mnt/c/Program Files/Android/Android Studio/jbr/bin/java.exe"
else
    JAVACMD="java"
fi

CLASSPATH_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [[ "$JAVACMD" == *.exe ]]; then
    CLASSPATH_ARG="$(wslpath -w "$CLASSPATH_JAR")"
    PROJECT_DIR_ARG="$(wslpath -w "$APP_HOME")"
    exec "$JAVACMD" -classpath "$CLASSPATH_ARG" org.gradle.wrapper.GradleWrapperMain -p "$PROJECT_DIR_ARG" "$@"
else
    exec "$JAVACMD" -classpath "$CLASSPATH_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
