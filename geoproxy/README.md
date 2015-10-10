
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

Fetch the web page:

    curl http://localhost:8888/web/index.html

Make a query:

    curl \
        -H "Content-Type: application/json" \
        -X POST \
        -d '{"type": "Circle", "coordinates": [[-122.250629, 37.871022], 300]}' \
        http://localhost:8888/query
