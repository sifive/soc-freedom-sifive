FROM sifive/environment-blockci:0.1.0

COPY ./workspace /workspace

WORKDIR /workspace
RUN wake --init .
