
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

    OBJS/yelp_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    OBJS/yelp_around -r 300 -- 40.4484 -80.0024

    # Just show bars
    OBJS/yelp_around -r 300 -c Bars -- 40.4484 -80.0024
