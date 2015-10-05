
Setup
----------------------------------------------------------------

    export PREFIX=~/aerospike/aerospike-client-c/target/Linux-x86_64
    export AEROSPIKE_LUA_PATH=/opt/aerospike/client/sys/udf/lua
    export DOWNLOAD=0

    npm install \
        /home/ksedgwic/aerospike/geospatial/aerospike-client-nodejs

    npm install .

Running
----------------------------------------------------------------

Usage:

    ./yelp_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    ./yelp_around -r 300 -- 40.4484 -80.0024

    # Just show bars
    ./yelp_around -r 300 -c Bars -- 40.4484 -80.0024
