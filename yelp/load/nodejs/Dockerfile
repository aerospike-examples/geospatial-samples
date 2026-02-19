FROM node:onbuild@sha256:e73333af1408fde6fa77cdd8f4e1fb82539c34fe7081ddce3682efd900421261
ADD . /code
WORKDIR /code
RUN npm install .
ENTRYPOINT ["/code/yelp_load"]