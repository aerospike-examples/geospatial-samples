
Building
----------------------------------------------------------------

    export CLIENTREPO=~/aerospike/aerospike-client-c

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/yelp_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    OBJS/yelp_around -r 300 -- 40.4484 -80.0024

    # Just show bars
    OBJS/yelp_around -r 300 -c Bars -- 40.4484 -80.0024
