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
#include <stdint.h>
#include <sys/time.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <map>
#include <stdexcept>
#include <vector>

#include <jansson.h>

#include "throwstream.h"
#include "scoped.h"

#include <aerospike/aerospike.h>
#include <aerospike/aerospike_key.h>
#include <aerospike/aerospike_query.h>
#include <aerospike/as_hashmap.h>
#include <aerospike/as_key.h>
#include <aerospike/as_query.h>
#include <aerospike/as_record.h>

#include "json.h"

using namespace std;

namespace {

// Some things have defaults
char const * DEF_HOST       = "localhost";
int const    DEF_PORT       = 3000;
char const * DEF_NAMESPACE  = "test";
char const * DEF_SET        = "yelp";

string  g_host		= DEF_HOST;
int     g_port      = DEF_PORT;
string  g_user;
string  g_pass;
string  g_namespace = DEF_NAMESPACE;
string  g_set       = DEF_SET;
string	g_infile;
    
char const *        g_valbin = "val";
char const *        g_locbin = "loc";
char const *        g_mapbin = "map";
string	g_locndx;
	
size_t g_npoints = 0;

double
fetch_coord(json_t * obj, char const * name, string const & line) {
	json_t * coordobj = json_object_get(obj, name);
	if (! coordobj) {
		throwstream(runtime_error, "missing " << name << ": " << line);
	}
	if (json_is_real(coordobj)) {
		return json_real_value(coordobj);
	}
	else if (json_is_integer(coordobj)) {
		return double(json_integer_value(coordobj));
	}
	else {
		throwstream(runtime_error, "invalid " << name << ": " << line);
	}
}

void
handle_line(aerospike * asp, string const & line)
{
	json_error_t err;
	Scoped<json_t *> entry(json_loadb(line.data(), line.size(), 0, &err),
						   NULL, json_decref);
	if (! entry) {
		throwstream(runtime_error, "failed to parse record: "
					<< err.line << ": " << err.text);
    }

	double latitude = fetch_coord(entry, "latitude", line);
	double longitude = fetch_coord(entry, "longitude", line);
	
	// Construct the GeoJSON loc bin value.
    Scoped<json_t *> locobj(json_object(), NULL, json_decref);
    json_object_set_new(locobj, "type", json_string("Point"));
    Scoped<json_t *> coordobj(json_array(), NULL, json_decref);
    json_array_append_new(coordobj, json_real(longitude));
    json_array_append_new(coordobj, json_real(latitude));
    json_object_set(locobj, "coordinates", coordobj);
    Scoped<char *> locstr(json_dumps(locobj, JSON_COMPACT), NULL,
                          (void (*)(char*)) free);

	// Convert the input record into a Aerospike nested map.
	as_map * mapp = (as_map *) as_json_to_val(entry);

	json_t * busidobj = json_object_get(entry, "business_id");
	if (! busidobj)
		throwstream(runtime_error, "missing business_id: " << line);
	if (! json_is_string(busidobj))
		throwstream(runtime_error, "business_id not string: " << line);
	string busid = json_string_value(busidobj);
	
    as_key key;
    as_key_init_str(&key, g_namespace.c_str(), g_set.c_str(), busid.c_str());

    uint16_t nbins = 3;
    
	as_record rec;
	as_record_inita(&rec, nbins);
	as_record_set_geojson_str(&rec, g_locbin, locstr);
	as_record_set_str(&rec, g_valbin, line.c_str());
	as_record_set_map(&rec, g_mapbin, mapp);
    
	as_policy_write wpol;
	as_policy_write_init(&wpol);
	wpol.timeout = 10 * 1000;

	as_error aserr;
	as_status rv = aerospike_key_put(asp, &aserr, &wpol, &key, &rec);
	as_record_destroy(&rec);
	as_key_destroy(&key);
	
	if (rv != AEROSPIKE_OK)
        throwstream(runtime_error, "aerospike_key_put failed: "
                    << aserr.code << " - " << aserr.message);

	if (__sync_add_and_fetch(&g_npoints, 1) % 1000 == 0)
		cerr << '.';
}
	
void
process_lines(aerospike * asp, ifstream & ifstrm)
{
	string line;
	while (getline(ifstrm, line)) {
		handle_line(asp, line);
	}
}
	
uint64_t
now()
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (tv.tv_sec * (uint64_t) 1000000) + tv.tv_usec;
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
create_indexes(aerospike * asp)
{
    {
        as_error err;
        as_index_task task;
        if (aerospike_index_create(asp, &err, &task, NULL,
                                   g_namespace.c_str(), g_set.c_str(),
								   g_locbin, g_locndx.c_str(),
                                   AS_INDEX_GEO2DSPHERE) != AEROSPIKE_OK)
            throwstream(runtime_error, "aerospike_index_create() returned "
                        << err.code << " - " << err.message);

        // Wait for the system metadata to spread to all nodes.
        aerospike_index_create_wait(&err, &task, 0);
    }
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
	cerr << "usage: " << argv[0] << " [options] <infile>" << endl
		 << "  options:" << endl
		 << "    -u, --usage                 	display usage" << endl
		 << "    -h, --host=HOST             	database host           [" << DEF_HOST << "]" << endl
		 << "    -p, --port=PORT             	database port           [" << DEF_PORT << "]" << endl
		 << "    -U, --user=USER             	username                [<none>]" << endl
		 << "    -P, --password=PASSWORD     	password                [<none>]" << endl
		 << "    -n, --namespace=NAMESPACE   	query namespace         [" << DEF_NAMESPACE << "]" << endl
		 << "    -s, --set=SET               	query set               [" << DEF_SET << "]" << endl
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
			{0, 0, 0, 0}
		};

	while (true)
	{
		int optndx = 0;
		int opt = getopt_long(argc, argv, "uh:p:U:P:n:s:",
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

    if (optind >= argc) {
		cerr << "missing input-file argument" << endl;
		usage(argc, argv);
		exit(1);
	}
    g_infile = argv[optind];

	// Make the index names contain the selected set name
	g_locndx = g_set + "-loc-index";
}

int
run(int & argc, char ** & argv)
{
    parse_arguments(argc, argv);

	// Open the file (fail early if we can't)
	ifstream ifstrm(g_infile.c_str());
	if (! ifstrm.good()) {
		throwstream(runtime_error, "trouble opening " << g_infile);
	}
	
    aerospike as;
    Scoped<aerospike *> asp(&as, NULL, cleanup_aerospike);
    setup_aerospike(asp);
    create_indexes(asp);
    
    uint64_t t0 = now();
	process_lines(asp, ifstrm);
    uint64_t t1 = now();

	cerr << endl;

	cerr << "Loaded " << dec << g_npoints << " points"
         << " in " << ((t1 - t0) / 1e6) << " seconds" << endl;

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
