
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

    OBJS/osm_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    OBJS/osm_around -r 300 -- 37.421342 -122.098743

    # Just show cafes
    OBJS/osm_around -r 300 -a cafe -- 37.421342 -122.098743

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/osm-around:cplusplus .

     docker run --rm <myuser>/osm-around:cplusplus -h localhost -p 3000 -r 3000 -a cafe -- 37.421342 -122.098743

