FROM python:2.7
RUN apt-get update
RUN apt-get -y install python-dev 
RUN pip install --no-cache-dir aerospike>=1.0.56
ADD . /code
WORKDIR /code
ENTRYPOINT ["python","/code/yelp_around"]
