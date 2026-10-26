# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS development
RUN apt-get update && apt-get install -y --no-install-recommends curl git rsync \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENV GRADLE_USER_HOME=/gradle-cache
COPY docker/start-dev-api.sh /usr/local/bin/start-dev-api.sh
RUN sed -i 's/\r$//' /usr/local/bin/start-dev-api.sh
EXPOSE 8080
CMD ["sh", "/usr/local/bin/start-dev-api.sh"]

FROM development AS builder
COPY . .
# Windows checkouts may contain CRLF in the Gradle shell wrapper.
RUN sed -i 's/\r$//' gradlew
RUN --mount=type=cache,target=/gradle-cache \
    sh gradlew --no-daemon :fineract-provider:bootJar -x test -x rat -x licenseMain -x licenseTest \
    && mkdir /output && cp fineract-provider/build/libs/fineract-provider*.jar /output/fineract.jar

FROM eclipse-temurin:17-jre-jammy AS production
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 fineract \
    && useradd --uid 10001 --gid fineract --home-dir /data fineract \
    && mkdir -p /app /data/.fineract && chown -R fineract:fineract /app /data
WORKDIR /app
COPY --from=builder --chown=fineract:fineract /output/fineract.jar ./fineract.jar
USER fineract
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/fineract.jar"]
