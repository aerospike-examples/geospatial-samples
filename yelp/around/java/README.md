
Building
----------------------------------------------------------------

    mvn package
    

Running
----------------------------------------------------------------

Usage:

    java -cp \
        target/yelp-around-*-jar-with-dependencies.jar \
        com.aerospike.yelp.Around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    java -cp \
        target/yelp-around-*-jar-with-dependencies.jar \
        com.aerospike.yelp.Around \
        -r 300 -- 40.4484 -80.0024

    # Just show bars
    java -cp \
        target/yelp-around-*-jar-with-dependencies.jar \
        com.aerospike.yelp.Around \
        -r 300 -c Bars -- 40.4484 -80.0024

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/yelp-around:java .

     docker run --rm <myuser>/yelp-around:java -h localhost -p 3000 -r 300 -c Bars -- 40.4484 -80.0024