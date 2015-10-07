
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike

Install imposm:

    sudo yum install -y protobuf-compiler protobuf-devel
    sudo pip install imposm.parser
    

Running
----------------------------------------------------------------

Usage:

    ./osm_load --usage

Execute the program, argument is path to osm pbf data file:

    ./osm_load san-francisco-bay_california.osm.pbf
    
