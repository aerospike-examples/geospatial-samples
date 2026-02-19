FROM python:2.7@sha256:cfa62318c459b1fde9e0841c619906d15ada5910d625176e24bf692cf8a2601d
RUN apt-get update
RUN apt-get -y install python-dev 
RUN pip install --no-cache-dir aerospike>=1.0.56
ADD . /code
WORKDIR /code
ENTRYPOINT ["python","/code/yelp_load"]
