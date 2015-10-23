FROM ubuntu:14.04
MAINTAINER Jeff Ippolito <jippolito@enernoc.com>

# Install packages from apt-get
RUN apt-get update && apt-get install -y \
    alien \
    wget \
    libaio1 \
    unzip \
    python \
    phantomjs \
    git \
    build-essential \
    golang
	
ENV GOPATH /go

RUN go get github.com/docker/libcontainer/nsinit/nsinit && \
    cp -f $GOPATH/bin/nsinit /usr/local/bin/

RUN curl -sOL https://raw.githubusercontent.com/YungSang/docker-attach/master/docker-nsinit && \
    chmod +x docker-nsinit && \
    mv -f docker-nsinit /usr/local/bin/

ADD installer /installer

CMD /installer