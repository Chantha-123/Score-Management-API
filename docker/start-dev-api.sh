#!/bin/sh
set -eu

# Compile on Docker's filesystem: Java/Lombok performs many file metadata reads,
# which are slow on Windows bind mounts. Only this native workspace is synced.
printf 'Synchronizing API sources into the Docker workspace...\n'
rsync -a --delete \
    --exclude='.gradle/' --exclude='build/' --exclude='.env*' \
    --exclude='*.log' --exclude='.claude/' --exclude='.idea/' --exclude='.vscode/' \
    --exclude='fineract-provider/gradle.zip' \
    /source/ /workspace/
cd /workspace

# Normalize the wrapper without altering the source checkout. Copy fresh wrapper
# files on every restart, including when the project's Gradle version changes.
tr -d '\r' < gradlew > /tmp/gradlew
mkdir -p /tmp/gradle/wrapper
cp gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties /tmp/gradle/wrapper/
printf 'Starting the Gradle development build...\n'
exec sh /tmp/gradlew --no-daemon --console=plain --info \
    :fineract-provider:bootRun -x test -x rat -x licenseMain -x licenseTest
