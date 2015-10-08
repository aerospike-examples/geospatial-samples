FROM python:2.7
ADD . /code
WORKDIR /code
RUN apt-get update
RUn apt-get -y install python-dev 
RUN apt-get -y install protobuf-compiler libprotobuf-dev
RUN pip install --no-cache-dir -r requirements.txt
ENTRYPOINT ["/code/docker-entrypoint.sh"]
CMD ["python"]
