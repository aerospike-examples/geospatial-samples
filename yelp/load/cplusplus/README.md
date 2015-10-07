
Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/

You need the jansson JSON library and OpenSSL:

On RedHat/CentOS:

    sudo yum install -y jansson-devel jansson
    sudo yum install -y openssl-devel openssl

On MacOS:

    brew install jansson
    brew install openssl


Building
----------------------------------------------------------------

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/yelp_load --usage

Execute the program, argument is path to the Yelp Challenge business data file:

    OBJS/yelp_load yelp_academic_dataset_business.json
