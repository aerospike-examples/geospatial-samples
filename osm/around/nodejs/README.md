
Setup
----------------------------------------------------------------

    export PREFIX=~/aerospike/aerospike-client-c/target/Linux-x86_64
    export AEROSPIKE_LUA_PATH=/opt/aerospike/client/sys/udf/lua
    export DOWNLOAD=0

    npm install /home/ksedgwic/aerospike/aerospike-client-nodejs

    npm install .

Running
----------------------------------------------------------------

Usage:

    ./osm_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    ./osm_around -r 300 -- 37.421342 -122.098743

    # Just show cafes
    ./osm_around -r 300 -a cafe -- 37.421342 -122.098743
