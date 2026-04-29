FROM ghcr.io/puppeteer/puppeteer:24.8.2

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install

COPY src ./src

ENV PORT=8080
EXPOSE 8080

CMD ["npm", "start"]
