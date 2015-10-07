
Prerequisites
----------------------------------------------------------------

Install Aerospike using pip:

    sudo pip install aerospike


Running
----------------------------------------------------------------

Usage:

    ./yelp_around --usage

Execute the program providing latitude and longitude as arguments:

    # What's around the Andy Warhol Museum?
    ./yelp_around -r 300 40.4484 -80.0024

    # Just show bars
    ./yelp_around -r 300 -c Bars 40.4484 -80.0024
