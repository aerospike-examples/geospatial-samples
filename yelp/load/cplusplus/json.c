/******************************************************************************
 * Copyright 2008-2014 by Aerospike.  All rights reserved.
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE.  THE COPYRIGHT NOTICE
 * ABOVE DOES NOT EVIDENCE ANY ACTUAL OR INTENDED PUBLICATION.
 ******************************************************************************/

#include <stdint.h>
#include <inttypes.h>

#include "json.h"

#include <aerospike/as_arraylist.h>
#include <aerospike/as_boolean.h>
#include <aerospike/as_hashmap.h>
#include <aerospike/as_pair.h>


#define LOG(msg, ...) \
    // { printf("%s:%d - ", __FILE__, __LINE__); printf(msg, ##__VA_ARGS__ ); printf("\n"); }


as_list * as_json_array_to_list(json_t * a) {

    int size = (int)json_array_size(a);
    as_list * l = (as_list *)as_arraylist_new(size,0);

    for (int i = 0; i < json_array_size(a); i++) {
        as_val * v = as_json_to_val(json_array_get(a,i));
        as_list_append(l, v);
    }

    return l;
}

as_map * as_json_object_to_map(json_t * o) {

    int             n = (int)json_object_size(o);
    as_map *        m = (as_map *)as_hashmap_new(n);
    const char *    k = NULL;
    json_t *        v = NULL;

    json_object_foreach(o, k, v) {
        as_val * key = (as_val *) as_string_new(strdup(k),true);
        as_val * val = as_json_to_val(v);
        as_map_set(m, key, val);

    }

    return m;
}

as_string * as_json_string_to_string(json_t * s) {
    const char * str = json_string_value(s);
    return as_string_new(strdup(str),true);
}

as_integer * as_json_number_to_integer(json_t * n) {
    return as_integer_new((int64_t) json_integer_value(n));
}

as_boolean * as_json_boolean_to_boolean(json_t * n) {
	return as_boolean_new(json_is_true(n));
}

as_val * as_json_to_val(json_t * j) {
    if ( json_is_array(j) )  return (as_val *) as_json_array_to_list(j);
    if ( json_is_object(j) ) return (as_val *) as_json_object_to_map(j);
    if ( json_is_boolean(j) ) return (as_val *) as_json_boolean_to_boolean(j);
    if ( json_is_string(j) ) return (as_val *) as_json_string_to_string(j);
    if ( json_is_integer(j) ) return (as_val *) as_json_number_to_integer(j);
    if ( json_is_real(j) ) return (as_val *) as_double_new((double)json_real_value(j));
    return (as_val *) NULL;//&as_nil;
}

as_val * as_json_arg(char const * arg) {

    as_val * val = NULL;
    json_t * root = NULL;
    json_error_t error;

    root = json_loads(arg, 0, &error);

    if ( !root ) {
		// Let asql deal with base type. JSON only to pass nested types
		return NULL;
    }
    else {
        val = as_json_to_val(root);
        json_decref(root);
    }

    return val;
}

/**
 * as_linkedlist had been removed.
 */
/*
as_list * as_json_arglist(int argc, char ** argv) {
    if ( argc == 0 || argv == NULL ) return NULL;//cons(NULL,NULL);
    return (as_list *)as_linkedlist_new(as_json_arg(argv[0]), (as_linkedlist *)as_json_arglist(argc-1, argv+1));
}
*/

int as_json_print(const as_val * val) {
    if ( !val ) {
        printf("null");
        return 1;
    }
    switch( val->type ) {
        case AS_NIL: {
            printf("null");
            break;
        }
        case AS_BOOLEAN: {
            printf("%s", as_boolean_tobool((as_boolean *) val) ? "true" : "false");
            break;
        }
        case AS_INTEGER: {
            printf("%" PRId64, as_integer_toint((as_integer *) val));
            break;
        }
        case AS_DOUBLE: {
            printf("%.16g", as_double_get((as_double*)val));
            break;
        }
        case AS_STRING: {
            printf("\"%s\"", as_string_tostring((as_string *) val));
            break;
        }
        case AS_LIST: {
            as_iterator * i = (as_iterator *)as_list_iterator_new((as_list *) val);
            bool delim = false;
            printf("[");
            while ( as_iterator_has_next(i) ) {
                if ( delim ) printf(",");
                printf(" ");
                as_json_print(as_iterator_next(i));
                delim = true;
            }
            printf(" ");
            printf("]");
            break;
        }
        case AS_MAP: {
            as_iterator * i = (as_iterator *)as_map_iterator_new((as_map *) val);
            bool delim = false;
            printf("{");
            while ( as_iterator_has_next(i) ) {
                as_pair * kv = (as_pair *) as_iterator_next(i);
                if ( delim ) printf(",");
                printf(" ");
                as_json_print(as_pair_1(kv));
                printf(": ");
                as_json_print(as_pair_2(kv));
                delim = true;
            }
            printf(" ");
            printf("}");
            break;
        }
        default: {
            printf("~~<%d>", val->type);
        }
    }
    return 0;
}

int record_to_json(int nbins, as_bin * bins) {
    printf("{");
    for (int i = 0; i < nbins; i++, bins++) {
		as_bin* bin = bins;
        printf("\"%s\": ", bins->name);
		as_val* val = (as_val*)bin->valuep;

        switch (val->type) {
			case AS_INTEGER: {
				as_integer* v = as_integer_fromval(val);
                printf("%"PRId64"", v->value);
				break;
			}
			case AS_DOUBLE: {
				as_double* v = as_double_fromval(val);
                printf("%.16g", v->value);
				break;
			}
			case AS_STRING: {
				as_string* v = as_string_fromval(val);
				printf("\"%s\"", v->value);
				break;
			}
			case AS_LIST:
			case AS_MAP: {
                as_json_print(val);
				break;
			}
			default: {
                printf("<%d>",(int)val->type);
				break;
			}
        }
        if ( i < nbins-1 ) {
            printf(", ");
        }
    }
    printf("}");
    return 0;
}
