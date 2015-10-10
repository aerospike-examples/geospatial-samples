
Prerequisites
----------------------------------------------------------------

    sudo pip install tornado


Running the Proxy
----------------------------------------------------------------

Usage:

    ./geoproxy --usage


Normal:

    ./geoproxy


Testing the Proxy
----------------------------------------------------------------

    curl \
        -H "Content-Type: application/json" \
        -X POST \
        -d '{"username":"xyz","password":"xyz"}' \
        http://localhost:8888/
