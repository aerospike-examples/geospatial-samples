
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

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/yelp-load:java .

     docker run --rm -v ~/Downloads:/data <myuser>/yelp-load:java -h localhost -p 3000 /data/yelp_academic_dataset_business.json 


