
Building
----------------------------------------------------------------

    mvn package
    

Running
----------------------------------------------------------------

Usage:

    java -cp \
        target/osm-around-*-jar-with-dependencies.jar \
        com.aerospike.osm.Around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    java -cp \
        target/osm-around-*-jar-with-dependencies.jar \
        com.aerospike.osm.Around \
        -r 300 -- 37.421342 -122.098743 

    # Just show cafes
    java -cp \
        target/osm-around-*-jar-with-dependencies.jar \
        com.aerospike.osm.Around \
        -r 300 -a cafe -- 37.421342 -122.098743 
