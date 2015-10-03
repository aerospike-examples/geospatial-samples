
Building
----------------------------------------------------------------

    mvn package
    

Running
----------------------------------------------------------------

    java -cp \
        target/osm-around-*-jar-with-dependencies.jar \
        com.aerospike.osm.Around \
        -r 300 -- 37.421342 -122.098743 
