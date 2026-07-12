FROM node:20-alpine
WORKDIR /workspace
COPY package.json ./
RUN npm install --omit=dev
COPY runner.js ./
USER node
CMD ["node", "runner.js"]
