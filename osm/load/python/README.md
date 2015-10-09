
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike

Install imposm:

    sudo yum install -y protobuf-compiler protobuf-devel
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

An example loading data into a cluster created on the Aerospike Cloud Service

    ./osm_load -U dbadmin -P mypasswd -h C-9f9ff9f99f.aerospike.io -p 3200 san-francisco-bay_california.osm.pbf
    
Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/osm-load .

     docker run -it --rm -v ~/Downloads:/data <myuser>/osm-load -U dbadmin -P mypasswd -h C-9f9ff9f99f.aerospike.io -p 3200 /data/san-francisco-bay_california.osm.pbf 

