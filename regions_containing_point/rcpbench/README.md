
Prerequsites
----------------------------------------------------------------

Install the Aerospike C Client from the download page:

    http://www.aerospike.com/download/client/c/3.1.24/

On RedHat/CentOS:

    sudo yum install -u jansson-devel openssl lua-libs

On Debian/Ubuntu:

    sudo yum install -u libssl-dev libjansson-dev liblua50-dev

On MacOS:

    brew install jansson openssl lua


Building
----------------------------------------------------------------

    make
    

Running
----------------------------------------------------------------

Usage:

    OBJS/rcpbench --usage

Execute the benchmark:

    # Run the benchmark, you need to supply the total number of loaded points.
    OBJS/rcpbench -T 124291


Interpreting Results
----------------------------------------------------------------

Here is a sample run:

    $ OBJS/rcpbench -T 124291
    host: localhost
    port: 3000
    user: 
    password: 
    namespace: test
    set: osm
    num-inst: 4
    num-threads: 80
    total-points: 124291
    num-samples: 10000
    sample-offset: 0
    found 11063 candidate samples
    Query latency (mSec) by return size:
                   retsz    count    first      all
                     [0]     5433     1.43     1.43
                     [1]      911     1.21     1.46
                 (1 - 2]      520     1.16     1.38
                 (2 - 5]      999     1.21     1.46
                (5 - 10]      783     1.30     1.52
               (10 - 20]      564     1.43     1.67
               (20 - 50]      513     1.52     1.74
              (50 - 100]      173     2.04     2.27
             (100 - 200]      104     2.53     2.78
    -----------------------------------------------
                   Total    10000
    Average return 6.1139 regions/query
    Query throughput 49023.7 queries/sec
    Result throughput 299726 regions/sec
    Network throughput 19.1825 Mbits/sec

1. We preformed 49K queries per second.

2. The buckets show the distribution of number results for the set of
   queries.

3. The "first" column shows the average latency until the first result
   is returned on the client.  The "all" clientcolumn is the average
   latency until all results are returned.

