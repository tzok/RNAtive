FROM node:18.12.1-alpine AS build

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
# Build stage
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
ARG REACT_APP_SERVER_ADDRESS
ENV REACT_APP_SERVER_ADDRESS=$REACT_APP_SERVER_ADDRESS
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
