FROM alpine:3.8

RUN apk --update add gradle

WORKDIR /app

ADD build.gradle.kts /app/
ADD settings.gradle /app/
RUN mkdir -p src/main/kotlin
RUN touch src/main/kotlin/Trimmer.kt
RUN gradle build --console verbose --no-daemon --build-cache --project-cache-dir /app/.gradle

ADD . /app/

ENTRYPOINT ["gradle", "run", "--console", "verbose", "--no-daemon", "--build-cache", "--project-cache-dir", "/app/.gradle"]