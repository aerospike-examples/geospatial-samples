
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike>=1.0.56

Running
----------------------------------------------------------------

Usage:

    ./osm_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    ./osm_around -r 300 37.421342 -122.098743

    # Just show cafes
    ./osm_around -r 300 -a cafe 37.421342 -122.098743

    # Using the Aerospike Cloud Service
    ./osm_around -U dbadmin -P mypasswd -h C-9f9ff9f99f.aerospike.io -p 3200 -r 300 -a cafe 37.421342 -122.098743

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/osm-around:python .

     docker run --rm <myuser>/osm-around:python -h localhost -p 3000 -r 300 -a cafe 37.421342 -122.098743


