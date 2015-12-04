
Building
----------------------------------------------------------------

    npm install .


Running
----------------------------------------------------------------

Usage:

    ./yelp_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    ./yelp_around -r 300 -- 40.4484 -80.0024

    # Just show bars
    ./yelp_around -r 300 -c Bars -- 40.4484 -80.0024


Docker
----------------------------------------------------------------
A Docker file is included that packages up the code and dependencies to run the load, 

Usage:

     docker build -t <myuser>/yelp-around-nodejs .

     docker run --rm <myuser>/yelp-around-nodejs -h localhost -p 3000 -r 300 -c Bars -- 40.4484 -80.0024