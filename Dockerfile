# syntax = docker/dockerfile:1

FROM eclipse-temurin:19-jdk-focal

WORKDIR /app

COPY cempaka-0.1-SNAPSHOT.jar ./

CMD ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseNUMA", "-XX:+UseCompressedOops", "-XX:+AlwaysPreTouch", "-XX:+UseShenandoahGC", "-Xlog:gc*:file=log/gc.log", "-Xms2G", "-Xmx2G", "--enable-preview", "-jar", "cempaka-0.1-SNAPSHOT.jar"]
