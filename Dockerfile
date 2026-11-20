# Multi-stage Java build for Render Web Service
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac -encoding UTF-8 -d out \
    src/playzone/Main.java \
    src/playzone/model/*.java \
    src/playzone/people/*.java \
    src/playzone/exceptions/*.java \
    src/playzone/service/*.java \
    src/playzone/web/*.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
# Keep the static files in the image too, so the backend can still serve the
# complete local-style site if needed. Render frontend is deployed separately.
COPY web ./web
ENV PORT=10000
EXPOSE 10000
CMD ["sh", "-c", "java -Dfile.encoding=UTF-8 -Dplayzone.port=${PORT:-8080} -cp out playzone.web.PlayZoneWebServer"]
