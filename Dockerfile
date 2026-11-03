# Build stage: compile all Java files
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN javac -cp "lib/*" -d bin backend/exception/*.java backend/model/*.java backend/storage/*.java backend/main.java

# Runtime stage: run the compiled application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/bin ./bin
COPY --from=builder /app/lib ./lib
EXPOSE 8080
CMD ["java", "-cp", "bin:lib/*", "backend.main"]
