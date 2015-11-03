
Prerequisites
----------------------------------------------------------------

    sudo pip install tornado
    // make sure you have gcc, (YUM) openssl-devel or (APT) libssl-dev
    sudo pip install aerospike


Running the Proxy
----------------------------------------------------------------

This runs a proxy at localhost 8888. You probably want to point to a cluster at openstreetmap.
It must be configured to point at an aerospike.io server that has openstreetmap.
Please replace your server with 

Usage:

    ./geoproxy --usage

Normal:

    ./geoproxy
    
    ./geoproxy  -h C-83cd437ea7.aerospike.io -p 3200 -U dbadmin -P paranoia -n geo 


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
