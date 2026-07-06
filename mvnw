#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script for POSIX environments
# ----------------------------------------------------------------------------

# Find the project base dir
findBaseDir () {
    SCRIPT="$0"
    # resolve symlinks
    while [ -h "$SCRIPT" ] ; do
        LS=$(ls -ld "$SCRIPT")
        LINK=$(expr "$LS" : '.*-> \(.*\)$')
        if expr "$LINK" : '/.*' > /dev/null; then
            SCRIPT="$LINK"
        else
            SCRIPT=$(dirname "$SCRIPT")/"$LINK"
        fi
    done
    BASE_DIR=$(dirname "$SCRIPT")
    BASE_DIR=$(cd "$BASE_DIR" && pwd)
    export BASE_DIR
}

findBaseDir

MAVEN_WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

# Read distributionUrl from properties
DISTRIBUTION_URL=$(grep -E '^distributionUrl=' "$MAVEN_WRAPPER_PROPERTIES" | sed 's/distributionUrl=//')

# Determine JAVA_HOME / java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(which java)
fi

if [ -z "$JAVACMD" ] || [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: JAVA_HOME is not set and java not found in PATH." >&2
    exit 1
fi

# If wrapper jar is missing, download it
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep -E '^wrapperUrl=' "$MAVEN_WRAPPER_PROPERTIES" | sed 's/wrapperUrl=//')
    echo "Downloading Maven Wrapper from $WRAPPER_URL"
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "ERROR: Neither curl nor wget found. Cannot download Maven Wrapper." >&2
        exit 1
    fi
fi

exec "$JAVACMD" \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
