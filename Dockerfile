# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul -Doracle.jdbc.timezoneAsRegion=false"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system codedock \
    && useradd --system --gid codedock --home-dir /app codedock

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER codedock

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
