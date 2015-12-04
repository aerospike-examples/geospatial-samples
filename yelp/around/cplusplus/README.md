Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/

On RedHat/CentOS:

    sudo yum install -u jansson-devel openssl lua-libs

On Debian/Ubuntu:

    sudo yum install -u libssl-dev libjansson-dev liblua50-dev

On MacOS:

    brew install jansson openssl lua


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

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/yelp-around-cplusplus .

     docker run --rm <myuser>/yelp-around-cplusplus -h localhost -p 3000 -r 300 -c Bars -- 40.4484 -80.0024
