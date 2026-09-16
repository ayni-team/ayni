# Development image. It runs the application from the sources that docker
# compose mounts, so nobody needs Java or Maven installed on their machine.
#
# This is not the image you would deploy: a production one builds a jar and runs
# it on a plain JRE. That comes later, together with deployment.

FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Dependencies are downloaded first and cached as their own layer, so editing
# source code does not trigger a new download.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

EXPOSE 8080

CMD ["mvn", "-B", "spring-boot:run"]
