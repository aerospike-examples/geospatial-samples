
Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/

Make sure the C Client library is in your search path.

You need the readosm library:

    https://www.gaia-gis.it/fossil/readosm/index

On RedHat/CentOS:

    sudo yum install -y readosm-devel
    sudo yum install -u jansson-devel

On MacOS:

    brew install readosm
    brew install jansson
    export CPATH=/usr/local/include


Building
----------------------------------------------------------------

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/osm_load --usage

Execute the program, argument is path to osm pbf data file:

    OBJS/osm_load san-francisco-bay_california.osm.pbf

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/osm-load:cplusplus .

     docker run --rm -v ~/Downloads:/data <myuser>/osm-load:cplusplus -h localhost -p 3000 /data/san-francisco-bay_california.osm.pbf 


