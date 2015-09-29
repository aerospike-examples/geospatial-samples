
    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.7

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.6

Get usage help:

    ./yelp_load

Load yelp data into a cluster server:

    ./yelp_load \
        -h C-17464dcc0c.aerospike-burro.net -p 3200 \
        -U dbadmin123 -P dbadmin123  -n ns1 \
        yelp_academic_dataset_business.json
