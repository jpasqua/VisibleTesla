#! /bin/sh
# run VisibleTesla within a docker container

docker build -t visibletesla .
docker run -ti --rm \
    -e QT_X11_NO_MITSHM=1 \
    -e DISPLAY=$DISPLAY \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -v $HOME/.VisibleTesla:/home/docker/.VisibleTesla \
    visibletesla    
