FROM ubuntu:xenial
MAINTAINER Richard Ulrich   "richi@ulrichard.ch"
# This docker file allows to run VisibleTesla to run in docker.
# Rather than building from source, it makes use of the pre-built package.
# Also some 3rd party liraries are used in compiled form.
# Keep in mind that it is therefore less secure to use as if everything was compiled from source.

# Tell debconf to run in non-interactive mode
ENV DEBIAN_FRONTEND noninteractive

# Make sure the repository information is up to date
# We need ssh to access the docker container, wget
# manually install some dependencies
RUN apt-get update && apt-get -y dist-upgrade && apt-get -y install unzip curl bash sudo wget openssh-server software-properties-common

# install the proprietary oracle JDK
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update && apt-get -y install oracle-java7-installer
RUN update-java-alternatives -s java-7-oracle
RUN apt-get -y install oracle-java7-set-default

# Download the VisibleTesla precompiled binary
RUN curl -s -O https://dl.dropboxusercontent.com/u/7045813/VT2/RawApp/VisibleTesla.zip
RUN unzip VisibleTesla.zip
RUN rm VisibleTesla.zip

# install dependencies
RUN apt-get update && apt-get -y install libxrender1 libxtst6 libxi6 libgtk2.0-0 gtk2-engines libxtst6 libxxf86vm1 libcanberra-gtk*
RUN apt-get update && apt-get -y install libxext-dev libxrender-dev libxtst-dev fonts-droid-fallback libxslt1-dev 
 
# Set the locale
RUN locale-gen en_US.UTF-8  
ENV LANG en_US.UTF-8  
ENV LANGUAGE en_US:en  
ENV LC_ALL en_US.UTF-8  

# Create user "docker" and set the password to "docker"
RUN useradd -m -d /home/docker docker
RUN echo "docker:docker" | chpasswd

# Create OpenSSH privilege separation directory, enable X11Forwarding
RUN mkdir -p /var/run/sshd
RUN echo X11Forwarding yes >> /etc/ssh/ssh_config

# Prepare ssh config folder so we can upload SSH public key later
RUN mkdir /home/docker/.ssh
RUN chown -R docker:docker /home/docker
RUN chown -R docker:docker /home/docker/.ssh

# Set locale (fix locale warnings)
RUN localedef -v -c -i en_US -f UTF-8 en_US.UTF-8 || true
RUN echo "Europe/Zurich" > /etc/timezone

# Replace 1000 with your user / group id
RUN export uid=1000 gid=1000 && \
    echo "docker ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/docker && \
    chmod 0440 /etc/sudoers.d/docker
USER docker
ENV HOME /home/docker

# Expose the SSH port
#EXPOSE 22

# Start SSH
#ENTRYPOINT ["/usr/sbin/sshd",  "-D"]
#CMD bash
CMD cd VisibleTesla\ 0.50.08 && java -jar VisibleTesla.jar
