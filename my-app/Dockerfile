FROM node:18.12.1-alpine as build

WORKDIR /my-app/

COPY package.json /my-app/

RUN npm install

COPY public/ /my-app/public
COPY src/ /my-app/src

RUN npm run build

FROM nginx:alpine
COPY --from=build /my-app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
