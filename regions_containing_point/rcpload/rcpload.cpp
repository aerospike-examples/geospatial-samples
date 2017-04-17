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

#include <aerospike/aerospike.h>
#include <aerospike/aerospike_key.h>
#include <aerospike/aerospike_query.h>
#include <aerospike/as_hashmap.h>
#include <aerospike/as_key.h>
#include <aerospike/as_query.h>
#include <aerospike/as_record.h>

#include "blockingqueue.h"
#include "refcount.h"
#include "scoped.h"
#include "throwstream.h"

using namespace std;

namespace {

// Some things have defaults
char const * DEF_HOST       = "localhost";
int const    DEF_PORT       = 3000;
char const * DEF_NAMESPACE  = "test";
char const * DEF_SET        = "osm";
double const DEF_RADIUS     = 500.0;
char const * DEF_AMENITY    = "restaurant";

string  g_host		= DEF_HOST;
int     g_port      = DEF_PORT;
string  g_user;
string  g_pass;
string  g_namespace = DEF_NAMESPACE;
string  g_set       = DEF_SET;
string	g_infile;
string  g_amenity   = DEF_AMENITY;
double  g_radius    = DEF_RADIUS;
    
char const *        g_valbin = "val";
char const *        g_locbin = "loc";
char const *        g_mapbin = "map";
char const *        g_hshbin = "hash";
char const *		g_amenbin = "amenity";
char const *		g_cuisbin = "cuisine";
char const *		g_rgnbin = "rgn";
char const *		g_idbin = "id";
string	g_locndx;
string	g_hshndx;
string	g_amenndx;
string	g_cuisndx;
string	g_rgnndx;
	
size_t g_npoints = 0;

typedef map<string, int> valcounts_t;

valcounts_t g_amenity_counts;
valcounts_t g_cuisine_counts;

pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

typedef map<string, string> StringMap;
class NodeData : public RefCountObj
{
public:
	NodeData(int64_t i_osmid, double i_latitude, double i_longitude)
		: m_osmid(i_osmid)
		, m_latitude(i_latitude)
		, m_longitude(i_longitude)
	{}

	int64_t		m_osmid;
	double		m_latitude;
	double		m_longitude;
	StringMap	m_tags;
};
typedef RefCountPtr<NodeData> NodeDataHandle;

typedef BlockingQueue<NodeDataHandle> NodeDataQueue;

NodeDataQueue g_ndq(1000 * 1000);	

bool bycountdesc(pair<string, int> v0, pair<string, int> v1) {
    return v1.second < v0.second;
}

void
print_counts(valcounts_t const & vcounts)
{
    vector<pair<string, int> > counts(vcounts.begin(), vcounts.end());
    sort(counts.begin(), counts.end(), bycountdesc);
    for (size_t ii = 0; ii < counts.size(); ++ii)
        cout << counts[ii].second << '\t' << counts[ii].first << endl;
}
	
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

	NodeDataHandle ndh = new NodeData(node->id,
									  node->latitude,
									  node->longitude);

	// Copy the tag data into the NodeData object.
    for (int ii = 0; ii < node->tag_count; ++ii) {
		ndh->m_tags.insert(make_pair(node->tags[ii].key,
									 node->tags[ii].value));
	}

	g_ndq.push(ndh);
	return READOSM_OK;
}

void
process_node(aerospike * asp, NodeDataHandle const & ndh)
{
	bool is_amenity_match = false;

	int64_t osmid = ndh->m_osmid;
    int64_t hshval = id_to_hash(osmid);

	size_t ntags = ndh->m_tags.size();

    char const * amenity = NULL;
    char const * cuisine = NULL;

	// We'll insert all the tags + osmid, lat and long.
	as_map * asmap = (as_map *) as_hashmap_new(ntags + 3);
    Scoped<json_t *> valobj(json_object(), NULL, json_decref);
	for (StringMap::const_iterator pos = ndh->m_tags.begin();
		 pos != ndh->m_tags.end();
		 ++pos) {
		as_map_set(asmap,
				   (as_val *) as_string_new((char *) pos->first.c_str(), false),
				   (as_val *) as_string_new((char *) pos->second.c_str(), false));
		json_object_set_new(valobj,
							pos->first.c_str(),
							json_string(pos->second.c_str()));

		if (pos->first == "amenity" && pos->second == g_amenity) {
			is_amenity_match = true;
		}

		if (pos->first == "amenity") {
            amenity = pos->second.c_str();
			pthread_mutex_lock(&g_lock);
            ++g_amenity_counts[pos->second.c_str()];
			pthread_mutex_unlock(&g_lock);
        }

		if (pos->first == "cuisine") {
            cuisine = pos->second.c_str();
			pthread_mutex_lock(&g_lock);
            ++g_cuisine_counts[pos->second.c_str()];
			pthread_mutex_unlock(&g_lock);
        }
	}
	
	// Insert osmid
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "osmid", false),
			   (as_val *) as_integer_new(osmid));
	json_object_set_new(valobj, "osmid", json_real(osmid));

	// Insert latitude and longitude
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "latitude", false),
			   (as_val *) as_double_new(ndh->m_latitude));
	as_map_set(asmap,
			   (as_val *) as_string_new((char *) "longitude", false),
			   (as_val *) as_double_new(ndh->m_longitude));
	json_object_set_new(valobj, "latitude", json_real(ndh->m_latitude));
	json_object_set_new(valobj, "longitude", json_real(ndh->m_longitude));

    Scoped<char *> valstr(json_dumps(valobj, JSON_COMPACT), NULL,
                          (void (*)(char*)) free);

    // cout << valstr << endl;

	// Construct the GeoJSON loc bin value.
    Scoped<json_t *> locobj(json_object(), NULL, json_decref);
    json_object_set_new(locobj, "type", json_string("Point"));
	json_t * coordobj = json_array();
    json_array_append_new(coordobj, json_real(ndh->m_longitude));
    json_array_append_new(coordobj, json_real(ndh->m_latitude));
    json_object_set_new(locobj, "coordinates", coordobj);
    Scoped<char *> locstr(json_dumps(locobj, JSON_COMPACT), NULL,
                          (void (*)(char*)) free);

	Scoped<char *> rgnstr(NULL, NULL, (void (*)(char*)) free);
	if (is_amenity_match) {
		// Construct the GeoJSON rgn bin value.
		Scoped<json_t *> rgnobj(json_object(), NULL, json_decref);
		json_object_set_new(rgnobj, "type", json_string("AeroCircle"));
		json_t * coordobj2 = json_array();
		json_t * lnglatobj = json_array();
		json_array_append_new(lnglatobj, json_real(ndh->m_longitude));
		json_array_append_new(lnglatobj, json_real(ndh->m_latitude));
		json_array_append_new(coordobj2, lnglatobj);
		json_array_append_new(coordobj2, json_real(g_radius));
		json_object_set_new(rgnobj, "coordinates", coordobj2);
		rgnstr = json_dumps(rgnobj, JSON_COMPACT);
	}

	as_policy_write policy;
	as_policy_write_init(&policy);
	policy.timeout = 10 * 1000;

    as_key key;
    as_key_init_int64(&key, g_namespace.c_str(), g_set.c_str(), osmid);

    uint16_t nbins = 5;

	if (is_amenity_match) {
		++nbins;
	}
    if (amenity) {
        ++nbins;
	}
    if (cuisine) {
        ++nbins;
	}
	
	as_record rec;
	as_record_inita(&rec, nbins);
	as_record_set_int64(&rec, g_idbin, osmid);
	as_record_set_geojson_str(&rec, g_locbin, locstr);
	as_record_set_str(&rec, g_valbin, valstr);
	as_record_set_map(&rec, g_mapbin, asmap);
	as_record_set_int64(&rec, g_hshbin, hshval);
    if (amenity) {
        as_record_set_str(&rec, g_amenbin, amenity);
	}
    if (cuisine) {
        as_record_set_str(&rec, g_cuisbin, cuisine);
	}
    
	if (is_amenity_match) {
		as_record_set_geojson_str(&rec, g_rgnbin, rgnstr);
	}
    
	as_error err;
	as_status rv = aerospike_key_put(asp, &err, &policy, &key, &rec);
	as_record_destroy(&rec);
	as_key_destroy(&key);
	
	if (rv != AEROSPIKE_OK)
        throwstream(runtime_error, "aerospike_key_put failed: "
                    << err.code << " - " << err.message);

	if (__sync_add_and_fetch(&g_npoints, 1) % 1000 == 0)
		cerr << '.';
}

void *
queue_consumer(void * argp)
{
	aerospike * asp = (aerospike *) argp;
	
	try {
		while (true) {
			NodeDataHandle ndh = g_ndq.pop();
			process_node(asp, ndh);
		}
	}
	catch (out_of_range const & ex) {
		// All good, we're just done.
		return NULL;
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

    {
        as_error err;
        as_index_task task;
        if (aerospike_index_create(asp, &err, &task, NULL,
                                   g_namespace.c_str(), g_set.c_str(),
								   g_amenbin, g_amenndx.c_str(),
                                   AS_INDEX_STRING) != AEROSPIKE_OK)
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
								   g_cuisbin, g_cuisndx.c_str(),
                                   AS_INDEX_STRING) != AEROSPIKE_OK)
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
								   g_rgnbin, g_rgnndx.c_str(),
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
         << "    -u, --usage                    display usage" << endl
         << "    -h, --host=HOST                database host           [" << DEF_HOST << "]" << endl
         << "    -p, --port=PORT                database port           [" << DEF_PORT << "]" << endl
         << "    -U, --user=USER                username                [<none>]" << endl
         << "    -P, --password=PASSWORD        password                [<none>]" << endl
         << "    -n, --namespace=NAMESPACE      query namespace         [" << DEF_NAMESPACE << "]" << endl
         << "    -s, --set=SET                  query set               [" << DEF_SET << "]" << endl
         << "    -a, --amenity=AMENITY          region amenity          [" << DEF_AMENITY << "]" << endl
         << "    -r, --radius=RADIUS            amenity radius          [" << DEF_RADIUS << "]" << endl
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
			{"amenity",                 required_argument,  0, 'a'},
			{"radius",                  required_argument,  0, 'r'},
			{0, 0, 0, 0}
		};

	while (true)
	{
		int optndx = 0;
		int opt = getopt_long(argc, argv, "uh:p:U:P:n:s:a:r:",
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

		case 'a':
			g_amenity = optarg;
			break;

		case 'r':
			g_radius = strtod(optarg, &endp);
			if (*endp != '\0')
				throwstream(runtime_error,
							"invalid radius-meters value: " << optarg);
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
	g_locndx =	g_set + "-loc-index";
	g_hshndx =	g_set + "-hsh-index";
	g_amenndx =	g_set + "-amen-index";
	g_cuisndx =	g_set + "-cuis-index";
	g_rgnndx =	g_set + "-rgn-index";
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
    
	size_t nthreads = 100;
	vector<pthread_t> threads(nthreads);
	for (size_t ndx = 0; ndx < nthreads; ++ndx) {
		pthread_create(&threads[ndx], NULL, queue_consumer, asp);
	}

    ret = readosm_parse(osm_handle,
						NULL,
                        handle_node,
                        NULL,
                        NULL);
    if (ret != READOSM_OK)
        throwstream(runtime_error, "PARSE error: " << ret);

	// Mark the queue as complete.
	g_ndq.terminate();

	for (size_t ndx = 0; ndx < nthreads; ++ndx) {
		pthread_join(threads[ndx], NULL);
	}

    uint64_t t1 = now();

	cerr << endl;

	cerr << "Loaded " << dec << g_npoints << " points"
         << " in " << ((t1 - t0) / 1e6) << " seconds" << endl;

    cout << "Amenities:" << endl;
    print_counts(g_amenity_counts);

    cout << endl << endl;

    cout << "Cuisines:" << endl;
    print_counts(g_cuisine_counts);

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
