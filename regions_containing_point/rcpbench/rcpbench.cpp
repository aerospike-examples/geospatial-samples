/* 
 * Copyright 2015 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more
 * contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

#include <getopt.h>
#include <math.h>
#include <stdint.h>
#include <sys/time.h>

#include <algorithm>
#include <cassert>
#include <fstream>
#include <iostream>
#include <map>
#include <stdexcept>
#include <utility>
#include <vector>

#include <openssl/sha.h>

#include <jansson.h>

#include <aerospike/aerospike.h>
#include <aerospike/aerospike_key.h>
#include <aerospike/aerospike_query.h>
#include <aerospike/aerospike_udf.h>
#include <aerospike/as_arraylist.h>
#include <aerospike/as_key.h>
#include <aerospike/as_query.h>
#include <aerospike/as_record.h>

#include "throwstream.h"
#include "scoped.h"

using namespace std;

#define fatal(fmt, ...)                                 \
    do {                                                \
        fprintf(stderr, "%s:%d: ", __FILE__, __LINE__); \
        fprintf(stderr, fmt, ##__VA_ARGS__);            \
        fprintf(stderr, "\n");                          \
        exit(1);                                        \
    } while(0)

namespace {

// Some things have defaults
char const * 	DEF_HOST		= "localhost";
int const    	DEF_PORT		= 3000;
char const * 	DEF_NAMESPACE	= "test";
char const * 	DEF_SET			= "osm";
size_t const	DEF_NUMINST		= 4;
size_t const	DEF_NUMTHREADS	= 80;
size_t const	DEF_TOTALPOINTS	= 11269358;
size_t const	DEF_NUMSAMPLES	= 10000;
int64_t const	DEF_SAMPLEOFF	= 0;

string  g_host			= DEF_HOST;
int     g_port			= DEF_PORT;
string  g_user;
string  g_pass;
string  g_namespace		= DEF_NAMESPACE;
string  g_set			= DEF_SET;
size_t	g_numinst		= DEF_NUMINST;
size_t	g_numthreads	= DEF_NUMTHREADS;
size_t	g_totalpoints   = DEF_TOTALPOINTS;
size_t	g_nsamples		= DEF_NUMSAMPLES;
int64_t	g_sampleoff		= DEF_SAMPLEOFF;

char const *		g_idbin = "id";
char const *        g_locbin = "loc";
char const *        g_rgnbin = "rgn";
char const *        g_hshbin = "hash";

typedef pair<double, double>	LatLng;
typedef pair<uint64_t, LatLng>	HashAndLatLng;
typedef vector<HashAndLatLng> LatLngSeq;
	
pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
LatLngSeq g_points;

typedef map<size_t, size_t> CountHist;
typedef map<size_t, uint64_t> ElapHist;

uint64_t g_numrecs;
uint64_t g_numbytes;

CountHist g_counthist;
ElapHist g_firsthist;
ElapHist g_elaphist;

size_t
bucketize(size_t size)
{
    //                      log10
    //     [0]:     0
    // (0 - 1]: 	1		0.00
    // (1 - 2]: 	2		0.30103
    // (2 - 5]: 	3		0.70757
    // (5 - 10]:	4		1.00
    // (10 - 20]:	5		1.30103
    // (20 - 50]:	6		1.70757
    // (50 - 100]:	7

    // Special case ...
    if (size == 0)
        return 0;
    
    double lsz = log10(double(size));
    double n1 = floor(lsz);
    double n2 = lsz - n1;
    size_t i1 = size_t(n1) * 3;

    if (n2 == 0.0)
        return i1 + 1;
    else if (n2 < 0.30103)
        return i1 + 2;
    else if (n2 < 0.70757)
        return i1 + 3;
    else
        return i1 + 4;
}

char const *
bucket_str(size_t ndx)
{
    static char buffer[1024];
    char const * valstr;
    if (ndx == 0)
        valstr = "[0]";
    else if (ndx == 1)
        valstr = "[1]";
    else
    {
        --ndx;

        int r1s = ndx / 3;
        int r1w = ndx % 3;
        int r1 = int(pow(10.0, double(r1s))) *
            (r1w == 0 ? 1 : r1w == 1 ? 2 : 5);

        --ndx;

        int r0s = ndx / 3;
        int r0w = ndx % 3;
        int r0 = int(pow(10.0, double(r0s))) *
            (r0w == 0 ? 1 : r0w == 1 ? 2 : 5);

        snprintf(buffer, sizeof(buffer), "(%d - %d]", r0, r1);
        valstr = buffer;
    }
    return valstr;
}

// We only save the first MAXIDS ids on return.
#define MAXIDS	1024

struct query_block_t
{
	size_t			m_numrecs;
	uint64_t		m_numbytes;
    uint64_t		m_t1;
	int64_t			m_osmids[MAXIDS];
};

uint64_t
now()
{
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return (tv.tv_sec * (uint64_t) 1000000) + tv.tv_usec;
}

bool
containing_region(as_val const * i_value, void * i_udata)
{
    query_block_t & qb = * (query_block_t *) i_udata;

    // Is this the first return value?
    if (qb.m_t1 == 0)
        qb.m_t1 = now();
    
    // Is the query complete?
    if (i_value == NULL)
        return true;

    size_t idndx = __sync_fetch_and_add(&qb.m_numrecs, 1);
	(void) idndx;

	as_record * recp = as_record_fromval(i_value);
	if (!recp)
		throwstream(runtime_error,
					"query callback returned non-as_record object");

	// FIXME - we're missing the map size, possible fixes:
	// 1) Just return one bin instead.
	// 2) Is there a way to query the entire record byte size?
	// 3) Both?
	//
	size_t len = 0;
	int64_t osmid = as_record_get_int64(recp, g_idbin, -1);
	if (osmid == -1)
		throwstream(runtime_error, "missing id bin");
	len += sizeof(int64_t);

#if 0
	// Save the first ids, for analysis
	if (idndx < MAXIDS) {
		qb.m_osmids[idndx] = osmid;
	}
#endif

    __sync_fetch_and_add(&qb.m_numbytes, len);

	return true;
}

void *
do_query_worker(void * argp)
{
    aerospike * asp = (aerospike *) argp;

    pthread_mutex_lock(&g_lock);
    while (!g_points.empty())
    {
        double lat = g_points.back().second.first;
        double lng = g_points.back().second.second;
        g_points.pop_back();
        pthread_mutex_unlock(&g_lock);

        uint64_t t0 = now();

        char qstr[1024];
        snprintf(qstr, sizeof(qstr),
           "{ \"type\": \"Point\", \"coordinates\": [%0.8f, %0.8f] }",
                 lng, lat);

        // fprintf(stderr, "%s\n", qstr);

        query_block_t qb = { 0, 0 };

        as_query query;
        as_query_init(&query, g_namespace.c_str(), g_set.c_str());

		as_query_select_inita(&query, 1);
		as_query_select(&query, g_idbin);
		
        as_query_where_inita(&query, 1);
        as_query_where(&query, g_rgnbin, as_geo_contains(qstr));

        as_error err;
        if (aerospike_query_foreach(asp, &err, NULL, &query,
                                    containing_region, &qb) != AEROSPIKE_OK)
            throwstream(runtime_error,
                        "aerospike_query_foreach() returned "
                        << err.code << " - " << err.message);

        as_query_destroy(&query);

        uint64_t t2 = now();

        pthread_mutex_lock(&g_lock);

		g_numrecs += qb.m_numrecs;
		g_numbytes += qb.m_numbytes;

        size_t ndx = bucketize(qb.m_numrecs);

        g_counthist[ndx] += 1;
        g_firsthist[ndx] += (qb.m_t1 - t0);
        g_elaphist[ndx] += (t2 - t0);

#if 0
		// Print out large result sets.
		if (qb.m_numrecs > 200) {
			ostringstream ostrm;
			ostrm << lat << ' ' << lng << " : [";
			for (size_t ii = 0; ii < qb.m_numrecs; ++ii) {
				if (ii != 0) {
					ostrm << ", ";
				}
				ostrm << qb.m_osmids[ii];
			}
			ostrm << "]";
			cerr << ostrm.str() << endl;
		}
#endif
    }
    pthread_mutex_unlock(&g_lock);
    return NULL;
}

void *
query_worker(void * argp)
{
	try {
		return do_query_worker(argp);
	}
	catch (exception const & ex) {
        cerr << "EXCEPTION: " << ex.what() << endl;
		abort();
	}
}

void
query_sample_points(aerospike * asp, size_t numinst, size_t numthreads)
{
    vector<pthread_t> thrds;

    // Create all the threads.
    thrds.resize(numthreads);
    for (size_t ii = 0; ii < numthreads; ++ii)
    {
        // Round-robin the threads onto the available clients.
        aerospike * tasp = &asp[ii % numinst];
        int err = pthread_create(&thrds[ii], NULL, &query_worker, tasp);
        if (err != 0)
            throwstream(runtime_error, "create thread failed: "
                        << strerror(err));
    }

    // Collect the threads on completion.
    for (size_t ii = 0; ii < numthreads; ++ii)
        pthread_join(thrds[ii], NULL);
}

bool
sample_point(as_val const * i_value, void * i_udata)
{
    // Is the query complete?
    if (i_value == NULL)
        return true;
    
    as_record * rec = as_record_fromval(i_value);
    if (!rec)
        return true;

	uint64_t hashval = as_record_get_int64(rec, g_hshbin, 0);
    
    char const * locstr = as_record_get_geojson_str(rec, g_locbin);

    json_error_t err;
    Scoped<json_t *> locobj(json_loads(locstr, 0, &err), NULL, json_decref);
    if (!locobj)
        throwstream(runtime_error, "failed to parse sample: " << locstr);
    if (!json_is_object(locobj))
        throwstream(runtime_error, "locobj not object");
    json_t * coordarr = json_object_get(locobj, "coordinates");
    if (!coordarr)
        throwstream(runtime_error, "failed to parse coordinates");
    if (!json_is_array(coordarr))
        throwstream(runtime_error, "coordinates not array");

    if (json_array_size(coordarr) != 2) {
		// This is not a point (likely a region), skip it.
		return true;
	}

    double lng = json_real_value(json_array_get(coordarr, 0));
    double lat = json_real_value(json_array_get(coordarr, 1));

    pthread_mutex_lock(&g_lock);
    g_points.push_back(make_pair(hashval, make_pair(lat, lng)));
    pthread_mutex_unlock(&g_lock);

    return true;
}

void
collect_sample_points(aerospike * asp, int64_t offset, size_t nsamples)
{
    // All records have a hash value which is uniformly distributed
    // between 0 and 2^63.  There are 10384716 total points.  We ask
    // for 10% extra ...
    //
    double factor = pow(2, 63) / (double) g_totalpoints;

    int64_t minval = int64_t(double(offset) * factor);
    int64_t maxval = minval + int64_t(double(nsamples) * 1.1 * factor);

    g_points.reserve(size_t(nsamples * 1.2));

    as_query query;
    as_query_init(&query, g_namespace.c_str(), g_set.c_str());
    as_query_where_inita(&query, 1);
    as_query_where(&query, g_hshbin, as_integer_range(minval, maxval));

    as_error err;
    as_status rv = aerospike_query_foreach(asp, &err, NULL,
                                           &query, sample_point, NULL);
    if (rv != AEROSPIKE_OK)
    {
        as_query_destroy(&query);
        throwstream(runtime_error, "collect_sample_points: "
                    << "aerospike_query_foreach: "
                    << dec << (int) rv << ' '
                    << err.code << ": " << err.message);
    }

    as_query_destroy(&query);

    // We'd like to have a deterministic set of points.  They will have
    // arrived in random order.  By sorting and then trimming we get
    // the same set each time ...

    cerr << "found " << g_points.size() << " candidate samples" << endl;

    if (g_points.size() < nsamples)
        throwstream(runtime_error,
					"not enough candidate samples: " << g_points.size());

    sort(g_points.begin(), g_points.end());
    g_points.resize(nsamples);
}

void
setup_aerospike(aerospike * asp)
{
	as_config cfg;
	as_config_init(&cfg);
	as_config_add_host(&cfg, g_host.c_str(), g_port);
	if (! g_user.empty())
		as_config_set_user(&cfg, g_user.c_str(), g_pass.c_str());
	aerospike_init(asp, &cfg);

	as_error err;
	if (aerospike_connect(asp, &err) != AEROSPIKE_OK)
		throwstream(runtime_error, "aerospike_connect failed: "
					<< err.code << " - " << err.message);
}

void
cleanup_aerospike(aerospike * asp)
{
	as_error err;
	if (aerospike_close(asp, &err) != AEROSPIKE_OK)
		throwstream(runtime_error, "aerospike_close failed: "
					<< err.code << " - " << err.message);
	
	aerospike_destroy(asp);
}

void
usage(int & argc, char ** & argv)
{
    cerr << "usage: " << argv[0] << " [options]" << endl
         << "  options:" << endl
         << "    -u, --usage                    display usage" << endl
         << "    -h, --host=HOST                database host           [" << DEF_HOST << "]" << endl
         << "    -p, --port=PORT                database port           [" << DEF_PORT << "]" << endl
         << "    -U, --user=USER                username                [<none>]" << endl
         << "    -P, --password=PASSWORD        password                [<none>]" << endl
         << "    -n, --namespace=NAMESPACE      query namespace         [" << DEF_NAMESPACE << "]" << endl
         << "    -s, --set=SET                  query set               [" << DEF_SET << "]" << endl
         << "    -i, --num-inst=NINST           number client instances [" << DEF_NUMINST << "]" << endl
         << "    -t, --num-threads=NTHREADS     number client threads   [" << DEF_NUMTHREADS << "]" << endl
         << "    -T, --total-points=NPOINTS     total points in db      [" << DEF_TOTALPOINTS << "]" << endl
         << "    -z, --num-samples=NSAMP        sample set size         [" << DEF_NUMSAMPLES << "]" << endl
         << "    -o, --sample-offset=OFFSET     sample set offset       [" << DEF_SAMPLEOFF << "]" << endl
        ;
}

void
parse_arguments(int & argc, char ** & argv)
{
	char * endp;

	static struct option long_options[] =
		{
			{"usage",                   no_argument,        0, 'u'},
			{"host",                    required_argument,  0, 'h'},
			{"port",                    required_argument,  0, 'p'},
			{"user",                    required_argument,  0, 'U'},
			{"password",                required_argument,  0, 'P'},
			{"namespace",               required_argument,  0, 'n'},
			{"set",                     required_argument,  0, 's'},
            {"num-inst",				required_argument,  0, 'i'},
            {"num-threads",				required_argument,  0, 't'},
            {"total-points",			required_argument,  0, 'T'},
            {"num-samples",				required_argument,  0, 'z'},
            {"sample-offset",			required_argument,	0, 'o'},
			{0, 0, 0, 0}
		};

	while (true)
	{
		int optndx = 0;
		int opt = getopt_long(argc, argv, "uh:p:U:P:n:s:i:t:T:z:o:",
							  long_options, &optndx);

		// Are we done processing arguments?
		if (opt == -1)
			break;

		switch (opt) {

		case 'u':
			usage(argc, argv);
			exit(0);
			break;

		case 'h':
			g_host = optarg;
			break;

		case 'p':
			g_port = strtol(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error, "invalid port value: " << optarg);
			break;

		case 'U':
			g_user = optarg;
			break;

		case 'P':
			g_pass = optarg;
			break;

		case 'n':
			g_namespace = optarg;
			break;

		case 's':
			g_set = optarg;
			break;

		case 'i':
			g_numinst = strtoul(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid num-inst value: " << optarg);
			break;

		case 't':
			g_numthreads = strtoul(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid num-threads value: " << optarg);
			break;

		case 'T':
			g_totalpoints = strtoul(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid total-points value: " << optarg);
			break;

		case 'z':
			g_nsamples = strtoul(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid num-samples value: " << optarg);
			break;

		case 'o':
			g_sampleoff = strtoul(optarg, &endp, 0);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid sample-offset value: " << optarg);
			break;

		case'?':
			// getopt_long already printed an error message
			usage(argc, argv);
			exit(1);
			break;

		default:
			throwstream(runtime_error, "unexpected option: " << char(opt));
			break;
		}
	}

	if (optind != argc) {
		cerr << "unrecognized command line arguments:";
		while (optind < argc) {
			cerr << " " << argv[optind++];
		}
		cerr << endl;
		usage(argc, argv);
		exit(1);
	}
}

int
run(int & argc, char ** & argv)
{
	parse_arguments(argc, argv);

	cerr << "host: " << g_host << endl
		 << "port: " << g_port << endl
		 << "user: " << g_user << endl
		 << "password: " << g_pass << endl
		 << "namespace: " << g_namespace << endl
		 << "set: " << g_set << endl
		 << "num-inst: " << g_numinst << endl
		 << "num-threads: " << g_numthreads << endl
		 << "total-points: " << g_totalpoints << endl
		 << "num-samples: " << g_nsamples << endl
		 << "sample-offset: " << g_sampleoff << endl
		;

    // Create multiple aerospike clients.
    vector<aerospike> asv(g_numinst);
    for (size_t ii = 0; ii < g_numinst; ++ii) {
        setup_aerospike(&asv[ii]);
	}

    collect_sample_points(&asv[0], g_sampleoff, g_nsamples);

    uint64_t t0 = now();

    query_sample_points(&asv[0], g_numinst, g_numthreads);

    uint64_t t1 = now();

    for (size_t ii = 0; ii < g_numinst; ++ii) {
        cleanup_aerospike(&asv[ii]);
	}

    cerr << "Query latency (mSec) by return size:" << endl;
    cerr << "               retsz    count    first      all" << endl;
    CountHist::const_iterator it0 = g_counthist.begin();
    ElapHist::const_iterator it1 = g_firsthist.begin();
    ElapHist::const_iterator it2 = g_elaphist.begin();
	size_t recsum = 0;
    while (it0 != g_counthist.end())
    {
        char buffer[2048];
        snprintf(buffer, sizeof(buffer),
                 "%20s %8ld %8.2f %8.2f",
                 bucket_str(it0->first),
                 it0->second,
                 double(it1->second) / (1000.0 * it0->second),
                 double(it2->second) / (1000.0 * it0->second));
        cerr << buffer << endl;

		recsum += it0->second;

        ++it0;
        ++it1;
        ++it2;
    }

	char buffer[2048];
	snprintf(buffer, sizeof(buffer), "               Total %8ld", recsum);
    cerr << "-----------------------------------------------" << endl;
	cerr << buffer << endl;
	
    cerr << "Average return "
		 << double(g_numrecs) / double(g_nsamples)
         << " regions/query" << endl;

    cerr << "Query throughput "
		 << double(g_nsamples) * 1e6 / double(t1 - t0)
         << " queries/sec" << endl;

    cerr << "Result throughput "
		 << double(g_numrecs) * 1e6 / double(t1 - t0)
         << " regions/sec" << endl;

    cerr << "Network throughput "
		 << double(g_numbytes) * 8 / double(t1 - t0)
         << " Mbits/sec" << endl;

	return 0;
}

} // end namespace

int
main(int argc, char ** argv)
{
	try
	{
		return run(argc, argv);
	}
	catch (exception const & ex)
	{
		cerr << "EXCEPTION: " << ex.what() << endl;
		return 1;
	}
}
