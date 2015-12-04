FROM node:onbuild
ADD . /code
WORKDIR /code
RUN npm install .
ENTRYPOINT ["/code/osm_around"]