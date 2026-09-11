FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac -cp "sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar" -d out src/Main.java
EXPOSE 8080
CMD ["java", "-cp", "out:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar", "Main"]
