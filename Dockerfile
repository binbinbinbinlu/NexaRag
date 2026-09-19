FROM node:24-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
USER node
ENV HOST=0.0.0.0 PORT=3000
EXPOSE 3000
CMD ["node", "src/server.js"]
