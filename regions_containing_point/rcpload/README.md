
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
    

Finding OSM PBF Data
----------------------------------------------------------------

I think any of the OSM PBF data subsets should work.  I've loaded ones
from http://download.geofabrik.de/ in the past.

    cd geo-samples/rcpload
    wget http://download.geofabrik.de/north-america/us/california-latest.osm.pbf
    OBJS/rcpload --help
    OBJS/rcpload california-latest.osm.pbf

Note the total number of points loaded (first line after the "..." stop):

    Loaded 124291 points in 119.002 seconds

The total number of points is a necessary parameter for the subsequent
benchmark runs (rcpbench).


Running
----------------------------------------------------------------

Usage:

    OBJS/rcpload --usage

Execute the program, argument is path to osm pbf data file:

    OBJS/rcpload ~/aerospike/data/geofabrik/california-latest.osm.pbf
