# Development image for the web application.
# Vite serves the sources that docker compose mounts and reloads the browser on
# every change, so nobody needs Node installed on their machine.

FROM node:22-alpine

WORKDIR /app

# The lock file fixes every version, so the container runs exactly what CI built.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev"]
