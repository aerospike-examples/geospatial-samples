
Building
----------------------------------------------------------------

    mvn package
    

Running
----------------------------------------------------------------

Usage:

    java -cp \
        target/yelp-load-*-jar-with-dependencies.jar \
        com.aerospike.yelp.Load \
        --usage

Execute the program, argument is path to the Yelp Challenge business data file:

    java -cp \
        target/yelp-load-*-jar-with-dependencies.jar \
        com.aerospike.yelp.Load \
        yelp_academic_dataset_business.json
