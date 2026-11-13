FROM eclipse-temurin:21-jdk AS build
WORKDIR /source
COPY shared/src/main/java ./shared
COPY server/src/main/java ./server
RUN find shared server -name '*.java' > sources.txt \
    && mkdir classes \
    && javac --release 11 -encoding UTF-8 -d classes @sources.txt \
    && jar --create --file sky-room-server.jar --main-class edu.hitsz.sky.server.ServerMain -C classes .

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/sky-room-server.jar /app/sky-room-server.jar
USER 10001
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/sky-room-server.jar"]
