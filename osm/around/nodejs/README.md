
Building
----------------------------------------------------------------

    npm install .


Running
----------------------------------------------------------------

Usage:

    ./osm_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Mountain View office?
    ./osm_around -r 300 -- 37.421342 -122.098743

    # Just show cafes
    ./osm_around -r 300 -a cafe -- 37.421342 -122.098743

Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run Around 

Usage:

     docker build -t <myuser>/osm-around:nodejs .

     docker run --rm <myuser>/osm-around:nodejs -h localhost -p 3000 -r 300 -a cafe -- 37.421342 -122.098743