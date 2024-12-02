# Base image
FROM node:18

RUN npm install -g artillery
RUN apt-get update && apt-get install -y openssh-server && mkdir /var/run/sshd
RUN echo 'root:password' | chpasswd
RUN sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config \
    && sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config
EXPOSE 22
WORKDIR /app

COPY ./scripts /app
EXPOSE 8080

CMD ["/usr/sbin/sshd", "-D"]
