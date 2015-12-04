FROM ubuntu:14.04
RUN apt-get update
RUN apt-get -y install build-essential curl libssl-dev libjansson-dev liblua50-dev
RUN curl -sSL http://www.aerospike.com/download/client/c/3.1.24/artifact/ubuntu12 > aerospike-client-c.tgz
RUN tar xzf aerospike-client-c.tgz
RUN dpkg -i aerospike-client-c-3.1.24.ubuntu12.04.x86_64/aerospike-client-c-devel-3.1.24.ubuntu12.04.x86_64.deb
RUN ln -s /usr/lib/liblua50.a /usr/lib/liblua.a
ADD . /code
WORKDIR /code
RUN make
ENTRYPOINT ["/code/docker-entrypoint.sh"]