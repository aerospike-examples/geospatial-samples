
Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/

You need the readosm library:

    https://www.gaia-gis.it/fossil/readosm/index

On RedHat/CentOS:

    sudo yum install -y readosm-devel
    sudo yum install -u jansson-devel

On MacOS:

    brew install readosm
    brew install jansson


Building
----------------------------------------------------------------

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/osm_load --usage

Execute the program, argument is path to osm pbf data file:

    OBJS/osm_load san-francisco-bay_california.osm.pbf
