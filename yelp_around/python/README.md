
Prerequisites
----------------------------------------------------------------


Running
----------------------------------------------------------------

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.7

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.6

    # What's around the Andy Warhol Museum?
    ./yelp_around -r 300 40.4484 -80.0024

    # Just show bars
    ./yelp_around -r 300 -c Bars 40.4484 -80.0024
