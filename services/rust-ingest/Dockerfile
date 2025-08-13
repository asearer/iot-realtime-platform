FROM rust:1.72 as builder

WORKDIR /app
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY build.rs ./

# Install protobuf compiler
RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*

# Build the application
RUN cargo build --release

FROM debian:bookworm-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    libssl3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/target/release/rust-ingestion /app/rust-ingestion

# Create non-root user
RUN groupadd -r iot && useradd -r -g iot iot
RUN chown -R iot:iot /app
USER iot

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:${LISTEN_PORT:-8090}/health || exit 1

EXPOSE 8090

CMD ["./rust-ingestion"]
