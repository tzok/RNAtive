FROM node:18.12.1-alpine

WORKDIR /my-app/

COPY package.json /my-app/

RUN npm install

COPY public/ /my-app/public
COPY src/ /my-app/src

EXPOSE 3000
CMD ["npm", "start"]
