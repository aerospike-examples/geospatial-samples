
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
