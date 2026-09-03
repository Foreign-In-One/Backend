# 1. 빌드 스테이지
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
# gradlew 파일에 실행 권한을 부여하고 빌드합니다.
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# 2. 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]