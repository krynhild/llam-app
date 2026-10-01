# The jar is platform-independent, so compile natively even when building an image for another CPU architecture.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode package -DskipTests \
	&& cp target/*.jar app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
