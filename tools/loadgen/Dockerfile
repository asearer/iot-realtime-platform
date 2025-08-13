FROM golang:1.20-alpine AS builder

WORKDIR /app

# Copy go mod and sum files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o loadgen .

FROM alpine:latest

# Install ca-certificates for HTTPS requests
RUN apk --no-cache add ca-certificates curl

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/loadgen .

# Create non-root user
RUN addgroup -g 1001 -S loadgen && \
    adduser -u 1001 -S loadgen -G loadgen

RUN chown loadgen:loadgen loadgen
USER loadgen

# Set default environment variables
ENV TARGET_URL=http://rust-ingest:8090
ENV RATE=100
ENV DURATION=300s
ENV DEVICE_COUNT=50
ENV METRICS=temperature,humidity,pressure,cpu_usage
ENV OUTPUT_FORMAT=text
ENV VERBOSE=false

CMD ["./loadgen"]
