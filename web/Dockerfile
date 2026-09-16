# Development image for the web application.
# Vite serves the sources that docker compose mounts and reloads the browser on
# every change, so nobody needs Node installed on their machine.

FROM node:22-alpine

WORKDIR /app

COPY package.json ./
RUN npm install

COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev"]
