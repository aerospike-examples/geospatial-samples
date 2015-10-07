
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike


Running
----------------------------------------------------------------

Get usage help:

    ./yelp_load --usage

Load yelp business data into a local server:

    ./yelp_load yelp_academic_dataset_business.json

Load yelp business data into a cluster server:

    ./yelp_load \
        -h C-17464dcc0c.aerospike-burro.net -p 3200 \
        -U dbadmin123 -P dbadmin123  -n ns1 \
        yelp_academic_dataset_business.json
