
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike>=1.0.56

Install imposm on RedHat:

    sudo yum install -y protobuf-compiler protobuf-devel
    sudo pip install imposm.parser   

Install imposm on Ubuntu:

    sudo apt-get install protobuf-compiler libprotobuf-dev
    sudo pip install imposm.parser
    
Install imposm on Mac:

    brew install protobuf
    sudo pip install imposm.parser

Running
----------------------------------------------------------------

Usage:

    ./osm_load --usage

Execute the program, argument is path to osm pbf data file:

    ./osm_load san-francisco-bay_california.osm.pbf
    
Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/osm-load:python .

     docker run --rm -v ~/Downloads:/data <myuser>/osm-load:python -h localhost -p 3000 /data/san-francisco-bay_california.osm.pbf 

