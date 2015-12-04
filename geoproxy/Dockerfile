FROM python:2.7
ADD . /code
WORKDIR /code
RUN apt-get update
RUN apt-get -y install python-dev 
RUN pip install --no-cache-dir aerospike>=1.0.54 tornado
EXPOSE 8888
ENTRYPOINT ["python","/code/geoproxy"]