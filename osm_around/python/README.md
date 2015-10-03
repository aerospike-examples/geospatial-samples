
Prerequisites
----------------------------------------------------------------


Running
----------------------------------------------------------------

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.7

    export PYTHONPATH=${HOME}/aerospike/aerospike-client-python/build/lib.linux-x86_64-2.6

    # What's around the Mountain View office?
    ./osm_around -r 300 37.421342 -122.098743

    # Just show cafes
    ./osm_around -r 300 -a cafe 37.421342 -122.098743
