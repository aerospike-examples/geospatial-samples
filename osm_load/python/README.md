
Prerequisites
----------------------------------------------------------------

    sudo yum install -y protobuf-compiler protobuf-devel

    sudo pip install imposm.parser
    

Running
----------------------------------------------------------------

You need one of these or similar:

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.7
    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.6

Usage:

    ./osm_load --usage

Execute the program, argument is path to osm pbf data file:

    ./osm_load san-francisco-bay_california.osm.pbf
    
