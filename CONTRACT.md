# API Contract: job-finder-gateway ↔ job-service

## Overview
This document defines the API contract between the **job-finder-gateway** and the downstream **job-service**. The gateway acts as a secure orchestrator that authenticates requests, fetches user profiles, and triggers job searches.

---

## 1. Gateway → job-service Request

### Endpoint
```
POST /api/jobs/search
```

### Headers
| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-User-Id` | String | Yes | The authenticated user's ID extracted from JWT claims |

### Request Body
```json
{
  "aspirations": {
    "desiredRoles": ["Senior Backend Engineer", "Tech Lead"],
    "industries": ["FinTech", "SaaS"],
    "salaryExpectation": {
      "min": 120000,
      "max": 180000,
      "currency": "USD"
    },
    "locations": ["Remote", "London", "New York"]
  },
  "workPreferences": {
    "employmentType": ["FULL_TIME", "CONTRACT"],
    "remotePreference": "HYBRID",
    "companySize": ["50-200", "200-1000"],
    "culture": ["Innovative", "Collaborative"]
  }
}
```

### Field Descriptions

#### `aspirations`
| Field | Type | Description |
|-------|------|-------------|
| `desiredRoles` | Array<String> | Job titles the user is targeting |
| `industries` | Array<String> | Preferred industries |
| `salaryExpectation` | Object | Salary range with min, max, and currency |
| `locations` | Array<String> | Preferred work locations |

#### `workPreferences`
| Field | Type | Description |
|-------|------|-------------|
| `employmentType` | Array<String> | Accepted employment types (e.g., FULL_TIME, PART_TIME, CONTRACT) |
| `remotePreference` | String | Remote work preference (REMOTE, HYBRID, ONSITE) |
| `companySize` | Array<String> | Preferred company sizes |
| `culture` | Array<String> | Desired company culture attributes |

---

## 2. User Profile Service Response (JSON String Fields)

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

### Field Descriptions - UserProfile from user-profile-service

#### Core Fields
| Field | Type | Description |
|-------|------|-------------|
| `userId` | String | Unique user identifier |
| `skills` | String (TEXT) | User's skills (stored as text) |
| `experience` | String (TEXT) | User's experience (stored as text) |
| `aspirations` | String (TEXT) | JSON string containing aspirations data |
| `workPrefs` | String (TEXT) | JSON string containing work preferences data |

#### Aspirations JSON Structure (parsed from `aspirations` field)
```json
{
  "desiredRoles": ["Senior Backend Engineer", "Tech Lead"],
  "industries": ["FinTech", "SaaS"],
  "salaryExpectation": {
    "min": 120000,
    "max": 180000,
    "currency": "USD"
  },
  "locations": ["Remote", "London", "New York"]
}
```

#### Work Preferences JSON Structure (parsed from `workPrefs` field)
```json
{
  "employmentType": ["FULL_TIME", "CONTRACT"],
  "remotePreference": "HYBRID",
  "companySize": ["50-200", "200-1000"],
  "culture": ["Innovative", "Collaborative"]
}
```

---

## 3. job-service → Gateway Response

### Success Response (200 OK)
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

### Error Responses

#### 400 Bad Request
```json
{
  "error": "INVALID_REQUEST",
  "message": "Missing required field: aspirations.desiredRoles"
}
```

#### 401 Unauthorized
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid or missing X-User-Id"
}
```

#### 503 Service Unavailable
```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Job search service is temporarily unavailable"
}
```

---

## 4. Data Flow

```
Client Request
    ↓
[Gateway] JwtTokenFilter extracts USER_ID from JWT
    ↓
[Gateway] UserProfileClient fetches profile from user-profile-service
    ↓
[Gateway] Profile contains JSON strings: aspirations & workPrefs
    ↓
[Gateway] JsonParser parses JSON strings into flattened fields
    ↓
[Gateway] Transforms flat fields into nested JobSearchRequest format
    ↓
[Gateway] JobServiceClient calls job-service with X-User-Id header
    ↓
[Gateway] Returns job results to client
```

---

## 5. Error Handling

The gateway implements **defensive programming**:

- **User Profile Service Unreachable**: Returns `503 SERVICE_UNAVAILABLE` with message: "User profile service is currently unavailable"
- **Job Service Unreachable**: Returns `503 SERVICE_UNAVAILABLE` with message: "Job search service is currently unavailable"
- **Invalid JWT**: Returns `401 UNAUTHORIZED`
- **Missing Profile Data**: Returns `400 BAD_REQUEST` if required fields are missing
- **JSON Parse Errors**: Returns `400 BAD_REQUEST` if aspirations/workPrefs JSON is malformed

---

## 6. Gateway Constraints

- **Thin Gateway**: No business logic for job matching or scoring
- **Transform Only**: Parses JSON strings and maps to job-service request format
- **Relay**: Passes through responses with minimal transformation
- **Aggregate**: Combines profile data with job search results

---

## 7. Key Design Decision: JSON String Storage

The user-profile-service stores complex data as JSON strings in TEXT columns:

**Database Storage:**
```java
@Column(columnDefinition = "TEXT")
private String aspirations;  // JSON string

@Column(columnDefinition = "TEXT")
private String workPrefs;    // JSON string
```

**Gateway Processing:**
1. Receives UserProfile with JSON strings
2. Uses `JsonParser` to parse `aspirations` and `workPrefs` fields
3. Extracts flattened fields (desiredRoles, industries, salaryMin, etc.)
4. Transforms to nested JobSearchRequest for job-service

**Benefits:**
- Flexible schema without complex migrations
- Simple database structure
- Gateway handles all parsing/transformation
- Easy to evolve JSON structure independently

---

## Version History
- v1.2.0 (2024-01-15): Updated to parse JSON string fields from user-profile-service
- v1.1.0 (2024-01-15): Flattened UserProfile DTO structure
- v1.0.0 (2024-01-15): Initial contract definition