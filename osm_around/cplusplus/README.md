
Building
----------------------------------------------------------------

    export CLIENTREPO=~/aerospike/aerospike-client-c

    make
    

Running
----------------------------------------------------------------

    # What's around the Mountain View office?
    OBJS/osm_around -r 300 -- 37.421342 -122.098743

    # Just show cafes
    OBJS/osm_around -r 300 -a cafe -- 37.421342 -122.098743
