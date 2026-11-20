FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package \
 && cp target/aidbridge-backend-0.0.1-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 aidbridge
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER aidbridge
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8081} -jar /app/app.jar"]
