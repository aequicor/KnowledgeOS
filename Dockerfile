FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY mcp-api/ mcp-api/
COPY mcp-imp/ mcp-imp/

RUN chmod +x gradlew
RUN ./gradlew :mcp-api:installDist --no-daemon -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app/index /vault

RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/mcp-api/build/install/mcp-api /app/
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

ENV BM25_INDEX_PATH=/app/index
ENV VAULT_PATH=/vault

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["/app/bin/mcp-api"]
