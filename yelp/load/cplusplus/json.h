/******************************************************************************
 * Copyright 2008-2014 by Aerospike.  All rights reserved.
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE.  THE COPYRIGHT NOTICE
 * ABOVE DOES NOT EVIDENCE ANY ACTUAL OR INTENDED PUBLICATION.
 ******************************************************************************/

#include <jansson.h>

#include <aerospike/as_bin.h>
#include <aerospike/as_list.h>
#include <aerospike/as_map.h>

#ifdef __cplusplus
extern "C" {
#endif

as_list * as_json_array_to_list(json_t *);
as_map * as_json_object_to_map(json_t *);
as_string * as_json_string_to_string(json_t *);
as_integer * as_json_number_to_integer(json_t *);
as_val * as_json_to_val(json_t *);

int as_json_print(const as_val *);

as_val * as_json_arg(char const *);
// as_list * as_json_arglist(int, char **);

int record_to_json(int nbins, as_bin * bins);

#ifdef __cplusplus
} // end extern "C"
#endif
