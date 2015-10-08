Yelp Challenge Business Dataset
================================================================

The [Yelp Dataset Challenge](http://www.yelp.com/dataset_challenge)
provides a sample set of data for research purposes.

One of the components of the dataset is a collection of information
about ~60k businesses, including latitude and longitude.

Here is a sample yelp business record:
```
{
    "business_id": "e_U_FnpdKVgNb4mUN2cU_Q",
    "full_address": "2323 Greentree Rd\nCarnegie, PA 15106",
    "hours": {},
    "open": true,
    "categories": ["Health & Medical", "Dentists", "General Dentistry"],
    "city": "Carnegie",
    "review_count": 6,
    "name": "Weinberg Lisa, DMD",
    "neighborhoods": [],
    "longitude": -80.078657000000007,
    "state": "PA",
    "stars": 2.0,
    "latitude": 40.39076,
    "attributes": {"By Appointment Only": true},
    "type": "business"
}
```


Loading OpenStreetMap Data
----------------------------------------------------------------

Using loader programs available in this tree the business records can
be loaded into an Aerospike cluster and then queried by geographical
location.

#### Acquiring Yelp Data

The Yelp Challenge Business Data can be downloaded directly from Yelp
at this URL:

[https://www.yelp.com/dataset_challenge/dataset](https://www.yelp.com/dataset_challenge/dataset)

After downloading the data, untar the data file and extract the
business data file called "yelp_academic_dataset_business.json".

This file can be directly loaded by the sample loader programs.

#### Sample Loader Programs

* [Python Loader](load/python)

* [Node.js Loader](load/nodejs)


Querying Yelp Data
----------------------------------------------------------------

Once the Yelp Challenge Business data has been loaded into an
Aerospike cluster geospatial queries may be made on the data.  The
following sample programs all return any items in the dataase which
are inside a circle around a chosen point:

#### Sample Query Programs

* [Python Query](around/python)

* [Java Query](around/java)

* [Node.js Query](around/nodejs)

* [C++ Query](around/cplusplus)
