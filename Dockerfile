# ============================================================
# Build Stage
# ============================================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Maven Wrapper dan konfigurasi project
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Pastikan Maven Wrapper executable
RUN chmod +x mvnw

# Download dependency terlebih dahulu
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build Spring Boot
RUN ./mvnw clean package -DskipTests


# ============================================================
# Runtime Stage
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Jalankan sebagai non-root user
RUN useradd --system --create-home --shell /usr/sbin/nologin spring

# Copy hasil build
COPY --from=builder /app/target/*.jar app.jar

RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]