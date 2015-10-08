FROM python:2.7
ADD . /code
WORKDIR /code
RUN apt-get update
RUN apt-get -y install python-dev 
RUN pip install --no-cache-dir -r requirements.txt
ENTRYPOINT ["/code/docker-entrypoint.sh"]
CMD ["python"]
