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
#include <sys/time.h>

#include <fstream>
#include <iostream>
#include <stdexcept>

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
char const * DEF_HOST       = "localhost";
int const    DEF_PORT       = 3000;
char const * DEF_NAMESPACE  = "test";
char const * DEF_SET        = "osm";
double const DEF_RADIUS     = 2000.0;

string  g_host		= DEF_HOST;
int     g_port      = DEF_PORT;
string  g_user;
string  g_pass;
string  g_namespace = DEF_NAMESPACE;
string  g_set       = DEF_SET;
double  g_radius    = -1.0;
double              g_lat;
double              g_lng;
char const *        g_filter_amenity = NULL;
    
char const *        g_valbin = "val";
char const *        g_locbin = "loc";

uint64_t            g_numrecs = 0;

uint64_t
now()
{
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return (tv.tv_sec * (uint64_t) 1000000) + tv.tv_usec;
}

bool
query_cb(const as_val * valp, void * udata)
{
	if (!valp)
		return true;	// query complete

	char const * valstr = NULL;

	if (g_filter_amenity)
	{
		as_string * sp = as_string_fromval(valp);
		if (!sp)
			fatal("query callback returned unexpected object");
		valstr = as_string_get(sp);
	}

	else
	{
		as_record * recp = as_record_fromval(valp);
		if (!recp)
			fatal("query callback returned non-as_record object");
		valstr = as_record_get_str(recp, g_valbin);
	}

	__sync_fetch_and_add(&g_numrecs, 1);
	
	cout << valstr << endl;

	return true;
}

void
query_circle(aerospike * asp, double lat, double lng, double radius)
{
	char region[1024];
	snprintf(region, sizeof(region),
			 "{ \"type\": \"AeroCircle\", \"coordinates\": [[%0.8f, %0.8f], %f] }",
			 lng, lat, radius);

	// fprintf(stderr, "%s\n", region);

	as_query query;
	as_query_init(&query, g_namespace.c_str(), g_set.c_str());

	as_query_where_inita(&query, 1);
	as_query_where(&query, g_locbin, as_geo_within(region));

	as_arraylist args;
	if (g_filter_amenity) {
		as_arraylist_init(&args, 1, 0);
		as_arraylist_append_str(&args, g_filter_amenity);
		as_query_apply(&query, "filter_by_amenity", "apply_filter",
					   (as_list *) &args);
	}
	
	as_error err;
	if (aerospike_query_foreach(asp, &err, NULL,
								&query, query_cb, NULL) != AEROSPIKE_OK)
		throwstream(runtime_error,
					"aerospike_query_foreach() returned "
					<< err.code << '-' << err.message);

	as_query_destroy(&query);
}

void
register_udf(aerospike * asp)
{
	string path = "filter_by_amenity.lua";
		
	ifstream istrm(path.c_str());
	string contents((istreambuf_iterator<char>(istrm)),
					istreambuf_iterator<char>());

	if (contents.empty())
		throwstream(runtime_error, "trouble opening " << path);
	
	as_bytes udf_content;
	as_bytes_init_wrap(&udf_content,
					   (uint8_t *) contents.data(),
					   contents.size(),
					   false);

	as_string base_string;
	const char* base = as_basename(&base_string, path.c_str());

	// Register the UDF file in the database cluster.
	as_error err;
	if (aerospike_udf_put(asp, &err, NULL, base, AS_UDF_TYPE_LUA,
						  &udf_content) == AEROSPIKE_OK) {
		// Wait for the system metadata to spread to all nodes.
		aerospike_udf_put_wait(asp, &err, NULL, base, 100);
	}
	else {
		throwstream(runtime_error,
					"udf_put failed: " << err.code << " - " << err.message);
	}

	as_string_destroy(&base_string);

	// This frees the local buffer.
	as_bytes_destroy(&udf_content);
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
	cerr << "usage: " << argv[0] << " [options] <latitude> <longitude>" << endl
		 << "  options:" << endl
		 << "    -u, --usage                 	display usage" << endl
		 << "    -h, --host=HOST             	database host           [" << DEF_HOST << "]" << endl
		 << "    -p, --port=PORT             	database port           [" << DEF_PORT << "]" << endl
		 << "    -U, --user=USER             	username                [<none>]" << endl
		 << "    -P, --password=PASSWORD     	password                [<none>]" << endl
		 << "    -n, --namespace=NAMESPACE   	query namespace         [" << DEF_NAMESPACE << "]" << endl
		 << "    -s, --set=SET               	query set               [" << DEF_SET << "]" << endl
		 << "    -r, --radius-meters=METERS  	radius in meters        [" << DEF_RADIUS << "]"  << endl
		 << "    -a, --amenity=AMENITY       	filter with amenity" << endl
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
			{"radius-meters",           required_argument,  0, 'r'},
			{"amenity",                 required_argument,  0, 'a'},
			{0, 0, 0, 0}
		};

	while (true)
	{
		int optndx = 0;
		int opt = getopt_long(argc, argv, "uh:p:U:P:n:s:r:a:",
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

		case 'r':
			g_radius = strtod(optarg, &endp);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid radius-meters value: " << optarg);
			break;

		case 'a':
			g_filter_amenity = strdup(optarg);
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

    if (optind + 1 >= argc) {
		cerr << "missing latitude and longitude arguments" << endl;
		usage(argc, argv);
		exit(1);
	}

	char const * argp = NULL;

	argp = argv[optind++];
	g_lat = strtod(argp, &endp);
	if (endp == argp)
		throwstream(runtime_error, "invalid latitude value: " << argp);
	
	argp = argv[optind++];
	g_lng = strtod(argp, &endp);
	if (endp == argp)
		throwstream(runtime_error, "invalid longitude value: " << argp);
	
	if (g_radius < 0.0)
		g_radius = DEF_RADIUS;
}

int
run(int & argc, char ** & argv)
{
	parse_arguments(argc, argv);

	aerospike as;
	Scoped<aerospike *> asp(&as, NULL, cleanup_aerospike);
	setup_aerospike(asp);

	if (g_filter_amenity)
		register_udf(asp);

	uint64_t t0 = now();

	query_circle(&as, g_lat, g_lng, g_radius);
	
	uint64_t t1 = now();

	cerr << "found " << g_numrecs << " in "
		 << double(t1 - t0) / 1000.0 << " mSec" << endl;
	
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
