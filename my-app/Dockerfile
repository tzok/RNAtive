FROM node:18.12.1-alpine

WORKDIR /my-app/
COPY public/ /my-app/public
COPY src/ /my-app/src
COPY package.json /my-app/

RUN npm install

CMD ["npm", "start"]
