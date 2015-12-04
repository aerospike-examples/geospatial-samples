
Prerequisites
----------------------------------------------------------------

    sudo pip install tornado
    // make sure you have gcc, (YUM) openssl-devel or (APT) libssl-dev
    sudo pip install aerospike>=1.0.56


Running the Proxy
----------------------------------------------------------------

This runs a proxy at localhost 8888. You probably want to point to a cluster at openstreetmap.
It must be configured to point at an aerospike.io server that has openstreetmap.
Please replace your server with 

Usage:

    ./geoproxy --usage

Normal:

    ./geoproxy
    
    ./geoproxy  -h localhost -p 3000  -n geo 


Testing the Proxy
----------------------------------------------------------------

Make a query:

    curl \
        -H "Content-Type: application/json" \
        -X POST \
        -d '{"type": "AeroCircle", "coordinates": [[-122.250629, 37.871022], 300]}' \
        http://localhost:8888/query


Using the Web Interface
----------------------------------------------------------------

Navigate to:

    http://localhost:8888/web/around.html

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/geoproxy .

     docker run --rm -p 8888:8888 <myuser>/geoproxy  -h localhost -p 3000

In your browser point to

     http://<host addr>/web/around.html     

where <host addr> is the IP address of the Container,

     docker-machine ip <my docker host>



