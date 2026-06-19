# Job Finder Gateway - Developer Guide

## Overview
The **job-finder-gateway** is a Spring Boot microservice that acts as a secure orchestrator between clients and downstream services. It authenticates requests via JWT, fetches user profiles, and triggers job searches.

---

## Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ Authorization: Bearer <JWT>
       ▼
┌─────────────────────────────┐
│   job-finder-gateway        │
│  ┌───────────────────────┐  │
│  │  JwtTokenFilter       │  │
│  └───────────┬───────────┘  │
│              │               │
│  ┌───────────▼───────────┐  │
│  │  JobSearchController  │  │
│  └───────────┬───────────┘  │
│              │               │
│  ┌───────────▼───────────┐  │
│  │  UserProfileClient    │  │
│  └───────────┬───────────┘  │
│              │               │
│  ┌───────────▼───────────┐  │
│  │  JsonParser           │  │
│  └───────────┬───────────┘  │
│              │               │
│  ┌───────────▼───────────┐  │
│  │  JobServiceClient     │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
       │           │           │
       ▼           ▼           ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│ user-    │ │   job-   │ │   JWT    │
│ profile- │ │ service  │ │  Secret  │
│ service  │ │          │ │          │
└──────────┘ └──────────┘ └──────────┘
```

---

## Authentication

### JWT Token Structure
The gateway expects a JWT token in the `Authorization` header:
```
Authorization: Bearer <JWT_TOKEN>
```

### JWT Claims
The JWT must contain a `USER_ID` claim:
```json
{
  "sub": "user@example.com",
  "USER_ID": "uuid-of-user",
  "iat": 1705312800,
  "exp": 1705316400
}
```

### JwtTokenFilter Implementation
The `JwtTokenFilter` extracts the `USER_ID` claim from the JWT and makes it available to controllers via request attributes.

**Key Implementation Details:**
1. Filter intercepts all requests to `/api/jobs/**`
2. Validates JWT signature using the configured `JWT_SECRET`
3. Extracts `USER_ID` claim and stores it as a request attribute
4. Rejects requests with invalid/expired tokens with `401 UNAUTHORIZED`

---

## User Profile Service Integration

### Endpoint Called by Gateway
```
GET http://user-profile-service/api/profiles/me
```

### Headers Sent
| Header | Value | Description |
|--------|-------|-------------|
| `X-User-Id` | `{USER_ID from JWT}` | Identifies the user whose profile to fetch |

### Expected Response (200 OK) - JSON String Structure
```json
{
  "userId": "uuid-of-user",
  "skills": "Java, Spring Boot, Microservices",
  "experience": "10+ years in software engineering",
  "aspirations": "{\"desiredRoles\":[\"Senior Backend Engineer\",\"Tech Lead\"],\"industries\":[\"FinTech\",\"SaaS\"],\"salaryExpectation\":{\"min\":120000,\"max\":180000,\"currency\":\"USD\"},\"locations\":[\"Remote\",\"London\",\"New York\"]}",
  "workPrefs": "{\"employmentType\":[\"FULL_TIME\",\"CONTRACT\"],\"remotePreference\":\"HYBRID\",\"companySize\":[\"50-200\",\"200-1000\"],\"culture\":[\"Innovative\",\"Collaborative\"]}"
}
```

### Important: JSON String Fields
The user-profile-service stores complex data as JSON strings in TEXT columns:

- **`aspirations`**: JSON string containing desired roles, industries, salary expectations, and locations
- **`workPrefs`**: JSON string containing employment type, remote preference, company size, and culture preferences

### Gateway Processing
The gateway uses `JsonParser` to:
1. Parse the `aspirations` JSON string into flattened fields
2. Parse the `workPrefs` JSON string into flattened fields
3. Make data available for transformation to job-service format

### Field Descriptions - Parsed Aspirations

| Field | Type | Description |
|-------|------|-------------|
| `desiredRoles` | Array<String> | Job titles the user is targeting |
| `industries` | Array<String> | Preferred industries |
| `salaryMin` | Integer | Minimum salary expectation |
| `salaryMax` | Integer | Maximum salary expectation |
| `salaryCurrency` | String | Currency code (e.g., USD, EUR, GBP) |
| `locations` | Array<String> | Preferred work locations |

### Field Descriptions - Parsed Work Preferences

| Field | Type | Description |
|-------|------|-------------|
| `employmentType` | Array<String> | Accepted employment types (e.g., FULL_TIME, PART_TIME, CONTRACT) |
| `remotePreference` | String | Remote work preference (REMOTE, HYBRID, ONSITE) |
| `companySize` | Array<String> | Preferred company sizes |
| `culture` | Array<String> | Desired company culture attributes |

### Error Responses from User Profile Service

#### 404 Not Found
```json
{
  "error": "PROFILE_NOT_FOUND",
  "message": "User profile does not exist"
}
```

#### 503 Service Unavailable
Returned when the user-profile-service is unreachable. The gateway will propagate this as:
```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "User profile service is currently unavailable"
}
```

---

## Job Service Integration

### Endpoint Called by Gateway
```
POST http://job-service/api/jobs/search
```

### Headers Sent
| Header | Value | Description |
|--------|-------|-------------|
| `X-User-Id` | `{USER_ID from JWT}` | Identifies the user for job search context |

### Request Body
The gateway transforms the parsed flat profile into this nested format:
```json
{
  "aspirations": {
    "desiredRoles": ["Senior Backend Engineer"],
    "industries": ["FinTech"],
    "salaryExpectation": {
      "min": 120000,
      "max": 180000,
      "currency": "USD"
    },
    "locations": ["Remote"]
  },
  "workPreferences": {
    "employmentType": ["FULL_TIME"],
    "remotePreference": "HYBRID",
    "companySize": ["50-200"],
    "culture": ["Innovative"]
  }
}
```

### Expected Response (200 OK)
```json
{
  "jobs": [
    {
      "id": "job-123",
      "title": "Senior Backend Engineer",
      "company": "TechCorp Inc.",
      "location": "Remote",
      "salary": {
        "min": 140000,
        "max": 170000,
        "currency": "USD"
      },
      "employmentType": "FULL_TIME",
      "postedDate": "2024-01-15T10:30:00Z",
      "matchScore": 0.92
    }
  ],
  "totalResults": 42,
  "page": 1,
  "pageSize": 20
}
```

### Error Responses from Job Service

#### 400 Bad Request
```json
{
  "error": "INVALID_REQUEST",
  "message": "Missing required field"
}
```

#### 503 Service Unavailable
Returned when the job-service is unreachable. The gateway will propagate this as:
```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Job search service is currently unavailable"
}
```

---

## Gateway Endpoint

### GET /api/jobs/search

**Authentication:** Required (JWT Bearer token)

**Flow:**
1. `JwtTokenFilter` validates JWT and extracts `USER_ID`
2. `JobSearchController` receives request
3. `UserProfileClient` fetches profile from user-profile-service (with JSON string fields)
4. `JsonParser` parses `aspirations` and `workPrefs` JSON strings into flattened fields
5. Controller validates profile completeness
6. `JobServiceClient` transforms flat profile to nested format and calls job-service
7. Gateway returns job results to client

**Success Response (200 OK):**
Returns the job-service response directly.

**Error Responses:**
- `401 UNAUTHORIZED` - Invalid or missing JWT
- `503 SERVICE_UNAVAILABLE` - Downstream service unreachable

---

## Configuration

### application.yml
```yaml
server:
  port: ${SERVER_PORT:8080}

jwt:
  secret: ${JWT_SECRET}

services:
  user-profile:
    url: ${USER_PROFILE_SERVICE_URL:http://localhost:8081}
  job-service:
    url: ${JOB_SERVICE_URL:http://localhost:8082}
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8080 | Gateway server port |
| `JWT_SECRET` | change-me | Secret key for JWT validation |
| `USER_PROFILE_SERVICE_URL` | http://localhost:8081 | User profile service base URL |
| `JOB_SERVICE_URL` | http://localhost:8082 | Job service base URL |

---

## Gateway Principles

### Thin Gateway
- **No business logic** for job matching or scoring
- **Transform only**: Parses JSON strings and maps to job-service request format
- **Relay**: Passes through responses with minimal transformation
- **Aggregate**: Combines profile data with job search results

### Defensive Programming
- Always handle downstream service failures gracefully
- Return clear error messages with appropriate HTTP status codes
- Validate inputs before passing to downstream services
- Handle JSON parsing errors gracefully

---

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Running instances of user-profile-service and job-service

### Build
```bash
mvn clean install
```

### Run
```bash
mvn spring-boot:run
```

### Test
```bash
mvn test
```

---

## Key Classes

| Class | Responsibility |
|-------|---------------|
| `JwtTokenFilter` | Extracts USER_ID from JWT claims |
| `JwtUtil` | JWT validation and parsing utilities |
| `UserProfileClient` | Calls user-profile-service and triggers JSON parsing |
| `JsonParser` | Parses JSON strings from user-profile into flattened fields |
| `JobServiceClient` | Calls job-service (transforms flat to nested) |
| `JobSearchController` | Orchestrates the job search flow |
| `SecurityConfig` | Configures security filter chain |

---

## Data Transformation Pipeline

### Step 1: Receive from user-profile-service
```json
{
  "userId": "uuid-of-user",
  "aspirations": "{\"desiredRoles\":[\"Senior Backend Engineer\"],\"industries\":[\"FinTech\"],\"salaryExpectation\":{\"min\":120000,\"max\":180000,\"currency\":\"USD\"}}",
  "workPrefs": "{\"employmentType\":[\"FULL_TIME\"],\"remotePreference\":\"HYBRID\"}"
}
```

### Step 2: After JsonParser (flattened fields populated)
```java
userProfile.getDesiredRoles() // ["Senior Backend Engineer"]
userProfile.getIndustries()    // ["FinTech"]
userProfile.getSalaryMin()     // 120000
userProfile.getSalaryMax()     // 180000
userProfile.getEmploymentType() // ["FULL_TIME"]
userProfile.getRemotePreference() // "HYBRID"
```

### Step 3: Transform to job-service format (nested)
```json
{
  "aspirations": {
    "desiredRoles": ["Senior Backend Engineer"],
    "industries": ["FinTech"],
    "salaryExpectation": {
      "min": 120000,
      "max": 180000,
      "currency": "USD"
    }
  },
  "workPreferences": {
    "employmentType": ["FULL_TIME"],
    "remotePreference": "HYBRID"
  }
}
```

---

## Error Handling Strategy

1. **JWT Validation Failures**: Return `401 UNAUTHORIZED`
2. **User Profile Service Down**: Return `503 SERVICE_UNAVAILABLE`
3. **JSON Parse Errors**: Return `400 BAD_REQUEST` with parse error details
4. **Job Service Down**: Return `503 SERVICE_UNAVAILABLE`
5. **Missing Profile Data**: Return `400 BAD_REQUEST`
6. **Unexpected Errors**: Return `500 INTERNAL_SERVER_ERROR`

---

## Version History
- v1.2.0 (2024-01-15): Updated to parse JSON string fields from user-profile-service
- v1.1.0 (2024-01-15): Flattened UserProfile DTO structure
- v1.0.0 (2024-01-15): Initial implementation