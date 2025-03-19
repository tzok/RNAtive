# Deployment Guide

This document provides instructions for deploying the RNAtive application in production environments.

## Prerequisites

- A server with Docker and Docker Compose installed
- A domain name pointing to your server
- Open ports 80 and 443 on your firewall

## Production Deployment

### Initial Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/rnative.git
   cd rnative
   ```

2. Configure your domain in `docker-compose.yml`:
   - Update the `REACT_APP_SERVER_ADDRESS` in the frontend service
   - Update the `ANALYSIS_SERVICE_URL` in the backend service
   - Ensure nginx configuration references your domain

3. Start the application in production mode:
   ```bash
   docker-compose -f docker-compose.yml up -d --build
   ```

   This command explicitly uses only the base configuration file, ignoring the development overrides in `docker-compose.override.yml`.

### SSL Certificates

The production setup includes a certbot service that automatically obtains and renews SSL certificates from Let's Encrypt:

1. The first time you start the application, certbot will attempt to obtain certificates for your domain.
2. Certificates are stored in the `./certbot/conf` directory.
3. Renewal happens automatically every 12 hours if needed.

### Monitoring and Maintenance

- View logs:
  ```bash
  docker-compose -f docker-compose.yml logs -f
  ```

- Restart services:
  ```bash
  docker-compose -f docker-compose.yml restart
  ```

- Update the application:
  ```bash
  git pull
  docker-compose -f docker-compose.yml up -d --build
  ```

## Troubleshooting

### SSL Certificate Issues

If certbot fails to obtain certificates:

1. Check that your domain correctly points to your server
2. Ensure ports 80 and 443 are open
3. Check certbot logs:
   ```bash
   docker-compose -f docker-compose.yml logs certbot
   ```

### Application Not Accessible

1. Check if all containers are running:
   ```bash
   docker-compose -f docker-compose.yml ps
   ```

2. Check nginx logs:
   ```bash
   docker-compose -f docker-compose.yml logs nginx
   ```

3. Verify that your domain resolves to your server's IP address:
   ```bash
   nslookup yourdomain.com
   ```
