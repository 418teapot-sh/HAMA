# 빌드와 실행을 나눕니다. 실행 이미지에는 JDK·Gradle·소스 없이 JRE 와 jar 만 남습니다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 다운로드를 소스와 분리된 레이어로 둡니다. 소스만 바뀌면 이 레이어는 캐시를 씁니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 CI(ci-backend)가 PR 에서 이미 돌립니다.
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 돌리지 않습니다. 앱이 뚫려도 컨테이너 안에서 root 권한을 갖지 않게 합니다.
RUN groupadd --system hama && useradd --system --gid hama hama
COPY --from=build /app/build/libs/hama.jar hama.jar
USER hama

# 로그 시각을 한국 시간으로 맞춥니다.
ENV TZ=Asia/Seoul
# 힙을 컨테이너 메모리 한도 기준으로 잡습니다. 한도는 deploy/docker-compose.yml 의 mem_limit 입니다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENV SPRING_PROFILES_ACTIVE=dev

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "hama.jar"]
