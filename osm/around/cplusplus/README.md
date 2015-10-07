
Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/


Building
----------------------------------------------------------------

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/osm_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    OBJS/osm_around -r 300 -- 37.421342 -122.098743

    # Just show cafes
    OBJS/osm_around -r 300 -a cafe -- 37.421342 -122.098743
