# Home Finance App - Docker Deployment

This Docker Compose setup deploys the complete Home Finance application including the database, backend API, and frontend React application.

## Architecture

- **Database**: MySQL 8.0
- **Backend**: Spring Boot Java application
- **Frontend**: React + TypeScript + Tailwind CSS (served via Nginx)

## Services

### 1. Database (MySQL)
- **Port**: 3306
- **Container**: `homefinance-mysql`
- **Database**: `homefinancedb`
- **Credentials**: 
  - Username: `admin`
  - Password: `password`
  - Root Password: `rootpassword`

### 2. Backend (Spring Boot)
- **Port**: 8585 (API), 5005 (Debug)
- **Container**: `homefinance-backend`
- **Dependencies**: Database must be healthy before starting

### 3. Frontend (React)
- **Port**: 3001
- **Container**: `homefinance-frontend`
- **Served via**: Nginx
- **Dependencies**: Backend service

## Quick Start

### Prerequisites
- Docker
- Docker Compose

### Deployment Steps

1. **Clone and navigate to the project root**:
   ```bash
   cd homefinance-app
   ```

2. **Build and start all services**:
   ```bash
   docker-compose up --build
   ```

3. **Access the application**:
   - Frontend: http://localhost:3001
   - Backend API: http://localhost:8585
   - Database: localhost:3306

### Alternative Commands

**Start in detached mode**:
```bash
docker-compose up -d --build
```

**View logs**:
```bash
docker-compose logs -f
```

**Stop all services**:
```bash
docker-compose down
```

**Stop and remove volumes**:
```bash
docker-compose down -v
```

## Development

### Frontend Development
For frontend development, you can still run the React app locally:
```bash
cd frontend
npm install
npm run dev
```

### Backend Development
For backend development, you can run the Spring Boot app locally:
```bash
cd backend
./mvnw spring-boot:run
```

## Configuration

### Environment Variables
The backend service uses the following environment variables:
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `SPRING_PROFILES_ACTIVE`: Spring profile (dev/prod)

### Ports
- **3001**: Frontend (React app)
- **8585**: Backend API
- **5005**: Backend debug port
- **3306**: MySQL database

## Troubleshooting

### Common Issues

1. **Port conflicts**: Ensure ports 3001, 8585, 5005, and 3306 are available
2. **Database connection**: Wait for the database health check to pass
3. **Build failures**: Check Docker logs for specific error messages

### Useful Commands

**Check service status**:
```bash
docker-compose ps
```

**View specific service logs**:
```bash
docker-compose logs backend
docker-compose logs frontend
docker-compose logs db
```

**Rebuild specific service**:
```bash
docker-compose build backend
docker-compose build frontend
```

**Access database**:
```bash
docker-compose exec db mysql -u admin -p homefinancedb
```

## Production Deployment

For production deployment, consider:
1. Using environment-specific Docker Compose files
2. Setting up proper SSL/TLS certificates
3. Configuring external database instances
4. Setting up proper backup strategies
5. Using Docker secrets for sensitive data

## Network

All services are connected via a custom bridge network `homefinance-network` for secure inter-service communication.
