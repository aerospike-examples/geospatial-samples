
Building
----------------------------------------------------------------

    export CLIENTREPO=~/aerospike/aerospike-client-c

    make
    

Running
----------------------------------------------------------------

    # What's around the Mountain View office?
    OBJS/osm_around -l 37.421342 -122.098743 -r 300

    # Just show cafes
    OBJS/osm_around -l 37.421342 -122.098743 -r 300 -a cafe
