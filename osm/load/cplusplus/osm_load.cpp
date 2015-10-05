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
#include <iomanip>
#include <iostream>
#include <map>
#include <stdexcept>
#include <vector>

#include <openssl/sha.h>

#include <jansson.h>

#include <readosm.h>

#include "throwstream.h"
#include "scoped.h"

#include <aerospike/aerospike.h>
#include <aerospike/aerospike_key.h>
#include <aerospike/aerospike_query.h>
#include <aerospike/as_hashmap.h>
#include <aerospike/as_key.h>
#include <aerospike/as_query.h>
#include <aerospike/as_record.h>

using namespace std;

namespace {

// Some things have defaults
char const * DEF_HOST       = "localhost";
int const    DEF_PORT       = 3000;
char const * DEF_NAMESPACE  = "test";
char const * DEF_SET        = "osm";

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
char const *        g_hshbin = "hash";
string	g_locndx;
string	g_hshndx;	
	
size_t g_npoints = 0;

int64_t id_to_hash(int64_t const & id)
{
    uint8_t obuf[32];
    SHA256((uint8_t *) &id, sizeof(id), obuf);
    int64_t hshval;
    memcpy((void *) &hshval, obuf, sizeof(hshval));
    hshval &= 0x7fffffffffffffff;	// Don't be negative
    return hshval;
}

int
handle_node(void const * user_data, readosm_node const * node)
{
    aerospike * asp = (aerospike *) user_data;

	// First scan the tags to see if there is a name.
    char const * name = NULL;
    for (int ii = 0; ii < node->tag_count; ++ii) {
        if (strcmp(node->tags[ii].key, "name") == 0) {
            name = node->tags[ii].value;
            break;
        }
    }
    if (!name)
        return READOSM_OK;

    int64_t hshval = id_to_hash(node->id);

	size_t ntags = node->tag_count;

	// We'll insert all the tags + osmid, lat and long.
	as_map * asmap = (as_map *) as_hashmap_new(ntags + 3);
    Scoped<json_t *> valobj(json_object(), NULL, json_decref);
    for (int ii = 0; ii < node->tag_count; ++ii) {
		as_map_set(asmap,
				   (as_val *) as_string_new((char *) node->tags[ii].key, false),
				   (as_val *) as_string_new((char *) node->tags[ii].value, false));
		json_object_set_new(valobj,
							node->tags[ii].key,
							json_string(node->tags[ii].value));
    }
	// Insert osmid
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "osmid", false),
			   (as_val *) as_integer_new(node->id));
	json_object_set_new(valobj, "osmid", json_real(node->id));

	// Insert latitude and longitude
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "latitude", false),
			   (as_val *) as_double_new(node->latitude));
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "longitude", false),
			   (as_val *) as_double_new(node->longitude));
	json_object_set_new(valobj, "latitude", json_real(node->latitude));
	json_object_set_new(valobj, "longitude", json_real(node->longitude));

    Scoped<char *> valstr(json_dumps(valobj, JSON_COMPACT), NULL,
                          (void (*)(char*)) free);

    // cout << valstr << endl;

	// Construct the GeoJSON loc bin value.
    Scoped<json_t *> locobj(json_object(), NULL, json_decref);
    json_object_set_new(locobj, "type", json_string("Point"));
    Scoped<json_t *> coordobj(json_array(), NULL, json_decref);
    json_array_append_new(coordobj, json_real(node->longitude));
    json_array_append_new(coordobj, json_real(node->latitude));
    json_object_set(locobj, "coordinates", coordobj);
    Scoped<char *> locstr(json_dumps(locobj, JSON_COMPACT), NULL,
                          (void (*)(char*)) free);

    as_key key;
    as_key_init_int64(&key, g_namespace.c_str(), g_set.c_str(), node->id);

    uint16_t nbins = 4;
    
	as_record rec;
	as_record_inita(&rec, nbins);
	as_record_set_geojson_str(&rec, g_locbin, locstr);
	as_record_set_str(&rec, g_valbin, valstr);
	as_record_set_map(&rec, g_mapbin, asmap);
	as_record_set_int64(&rec, g_hshbin, hshval);
    
	as_error err;
	as_status rv = aerospike_key_put(asp, &err, NULL, &key, &rec);
	as_record_destroy(&rec);
	as_key_destroy(&key);
	
	if (rv != AEROSPIKE_OK)
        throwstream(runtime_error, "aerospike_key_put failed: "
                    << err.code << " - " << err.message);

    ++g_npoints;

	if (g_npoints % 1000 == 0)
		cerr << '.';
	
    // cout << json_dumps(rootobj, JSON_COMPACT) << endl;

    return READOSM_OK;
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

    {
        as_error err;
        as_index_task task;
        if (aerospike_index_create(asp, &err, &task, NULL,
                                   g_namespace.c_str(), g_set.c_str(),
								   g_hshbin, g_hshndx.c_str(),
                                   AS_INDEX_NUMERIC) != AEROSPIKE_OK)
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
	g_hshndx = g_set + "-hsh-index";
}

int
run(int & argc, char ** & argv)
{
    parse_arguments(argc, argv);

    const void * osm_handle;
    int ret;
    ret = readosm_open (g_infile.c_str(), &osm_handle);
    if (ret != READOSM_OK)
        throwstream(runtime_error, "OPEN error: " <<  ret);
    Scoped<void const *> osm(osm_handle, NULL,
                             (void (*)(const void*)) readosm_close);
    
    uint64_t t0 = now();

    aerospike as;
    Scoped<aerospike *> asp(&as, NULL, cleanup_aerospike);
    setup_aerospike(asp);
    create_indexes(asp);
    
    ret = readosm_parse(osm_handle,
                        (const void *) asp,
                        handle_node,
                        NULL,
                        NULL);
    if (ret != READOSM_OK)
        throwstream(runtime_error, "PARSE error: " << ret);

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
