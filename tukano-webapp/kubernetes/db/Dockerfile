FROM postgres:17
ENV POSTGRES_USER=citus
ENV POSTGRES_PASSWORD=Sigma!!!
ENV POSTGRES_DB=tukano-db
COPY ./schema.sql /docker-entrypoint-initdb.d/
EXPOSE 5432
