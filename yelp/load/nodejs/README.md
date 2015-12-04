
Building
----------------------------------------------------------------

    npm install .


Running
----------------------------------------------------------------

Usage:

    ./yelp_load --usage

Load yelp business data into a local server:

    ./yelp_load yelp_academic_dataset_business.json

Load yelp business data into a cluster server:

    ./yelp_load \
        -h localhost -p 3000 \
        -n ns1 \
        yelp_academic_dataset_business.json

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/yelp-load-nodejs .

     docker run --rm -v ~/Downloads:/data <myuser>/yelp-load-nodejs -h localhost -p 3000 /data/yelp_academic_dataset_business.json 


