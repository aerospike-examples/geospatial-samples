FROM java:latest
RUN apt-get update
RUN apt-get -y install maven
ADD . /code
WORKDIR /code
RUN mvn package
ENTRYPOINT ["java","-cp", "/code/target/yelp-around-1.0.0-jar-with-dependencies.jar", "com.aerospike.yelp.Around"]