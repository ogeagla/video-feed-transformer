FROM clojure:latest

RUN apt-get update -y && \
    apt-get install -y ffmpeg motion streamer

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY target/*-standalone.jar app-standalone.jar
CMD ["java", "-jar", "app-standalone.jar"]