FROM mcr.microsoft.com/playwright:v1.49.1-jammy

WORKDIR /e2e
COPY package.json package-lock.json ./
RUN npm ci
COPY playwright.config.ts ./
COPY tests ./tests

CMD ["npx", "playwright", "test"]
