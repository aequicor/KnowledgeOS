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

RUN mkdir -p /app/index /vault /app/model/onnx /app/model/reranker/onnx

RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends wget ca-certificates \
    && wget -q -O /app/model/onnx/model.onnx \
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx" \
    && wget -q -O /app/model/onnx/vocab.txt \
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/raw/main/vocab.txt" \
    && wget -q -O /app/model/reranker/onnx/model.onnx \
        "https://huggingface.co/cross-encoder/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx" \
    && wget -q -O /app/model/reranker/onnx/vocab.txt \
        "https://huggingface.co/cross-encoder/ms-marco-MiniLM-L-6-v2/raw/main/vocab.txt" \
    && apt-get purge -y -qq wget \
    && apt-get autoremove -y -qq \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/mcp-api/build/install/mcp-api /app/

ENV BM25_INDEX_PATH=/app/index
ENV VAULT_PATH=/vault

EXPOSE 8080

ENTRYPOINT ["/app/bin/mcp-api"]
