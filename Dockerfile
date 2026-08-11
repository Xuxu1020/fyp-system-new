FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM tomcat:10.1-jdk17
# Remove default apps
RUN rm -rf /usr/local/tomcat/webapps/*

# Deploy our WAR as ROOT (serves at /)
COPY --from=build /app/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war

# Create uploads directory inside the image
# (will be overridden by Docker volume in production)
RUN mkdir -p /uploads/cars

EXPOSE 8080
CMD ["catalina.sh", "run"]