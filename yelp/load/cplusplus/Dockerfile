FROM ubuntu:14.04@sha256:64483f3496c1373bfd55348e88694d1c4d0c9b660dee6bfef5e12f43b9933b30
RUN apt-get update
RUN apt-get -y install libssl-dev curl build-essential libjansson-dev
RUN curl -sSL http://www.aerospike.com/download/client/c/3.1.24/artifact/ubuntu12 > aerospike-client-c.tgz
RUN tar xzf aerospike-client-c.tgz
RUN dpkg -i aerospike-client-c-3.1.24.ubuntu12.04.x86_64/aerospike-client-c-devel-3.1.24.ubuntu12.04.x86_64.deb
ENV CFLAGS -std=gnu99
ADD . /code
WORKDIR /code
RUN make
ENTRYPOINT ["/code/OBJS/yelp_load"]