
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike>=1.0.54


Running
----------------------------------------------------------------

Get usage help:

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

     docker build -t <myuser>/yelp-load-python .

     docker run --rm -v ~/Downloads:/data <myuser>/yelp-load-python -h localhost -p 3000 /data/yelp_academic_dataset_business.json 

