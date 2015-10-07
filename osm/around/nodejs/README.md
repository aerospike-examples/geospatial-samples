
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
