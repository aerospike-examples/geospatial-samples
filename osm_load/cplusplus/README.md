
Prerequsites
----------------------------------------------------------------

You need the readosm library:

    https://www.gaia-gis.it/fossil/readosm/index

On RedHat/CentOS:

    sudo yum install -y readosm-devel

On MacOS:

    brew install readosm


Running
----------------------------------------------------------------

Usage:

    OBJS/osm_load --usage

Execute the program, argument is path to osm pbf data file:

    OBJS/osm_load san-francisco-bay_california.osm.pbf
