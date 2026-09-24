# Polish Writing Lab

A monorepo for an evidence-grounded Polish writing coach.

## Projects

- `backend/` — Java 21 and Spring Boot API
- `frontend/` — React and TypeScript web application
- `infrastructure/` — AWS CDK deployment infrastructure

## Local development

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Infrastructure

```bash
cd infrastructure
npm install
npm run build
npm test
npx cdk synth
```

AWS resources and RunPod integration will be added in the next vertical slice.
