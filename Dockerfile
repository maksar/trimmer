FROM alpine:3.8 as builder

RUN apk --update add gradle

WORKDIR /app

ADD build.gradle.kts /app/
ADD settings.gradle /app/
RUN mkdir -p src/main/kotlin
RUN touch src/main/kotlin/Trimmer.kt
RUN gradle build --console verbose --no-daemon --build-cache --project-cache-dir /app/.gradle

ADD . /app/

RUN gradle build --console verbose --no-daemon --build-cache --project-cache-dir /app/.gradle

RUN mkdir result && cd result && tar -xf ../build/distributions/trimmer*.tar --strip 1

FROM alpine:3.8

RUN apk --update add openjdk8-jre

COPY --from=builder /app/result /app
WORKDIR /app

ENTRYPOINT ["./bin/trimmer"]