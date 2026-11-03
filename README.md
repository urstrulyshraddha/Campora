# Campora

A lightweight, role-based campus grievance redressal and feedback tracking system designed for university environments. Built with a raw multithreaded Core Java backend, MySQL for persistent storage, and a responsive frontend interface.

---

## Overview

Campora streamlines reporting and administrative resolution across campus tracks (Safety Hazards, Facilities Maintenance, and Academic/Student Suggestions). It removes administrative friction by separating workflows into gated portals while allowing peer-supported verification through upvoting.

### Core Features

- **Role-Based Portals**:
  - **Student & Faculty Hub**: Public feed to log complaints (verified or anonymous), upvote peer issues, and add witness notes.
  - **Management Triage**: Protected by an access gate for estate, IT, and warden teams to filter queues by status, prioritize by urgency index, and commit resolutions with audit proof.
  - **Admin / System Console**: Live diagnostic inspector monitoring backend state, connection pools, and persistence routines.
- **Dynamic Urgency Scoring**: Prioritizes tickets automatically based on track severity multipliers and peer endorsements.
- **Zero Heavy Frameworks**: Core Java `HttpServer` with an `ExecutorService` thread pool and JDBC prepared statements—no Spring Boot or ORM overhead.
- **Dual Persistence Architecture**: Real-time ACID writes to MySQL alongside a scheduled background daemon writing periodic snapshot backups.

---

## System Architecture

```text
       [Terminal Console CLI]    OR    [Browser Web Client (Port 8000 / 8080)]
                                   │
                                   ▼
                 [com.sun.net.httpserver.HttpServer]
                                   │
             [ExecutorService: 10 Worker Threads (Fixed Pool)]
                                   │
                                   ▼
                           [REST Handlers]
                                   │
                    [backend.storage.report_repository]
                                   │
                     [JDBC Connector: Driver 8.4]
                                   │
                                   ▼
                        [MySQL DB: campora_db]
                         ├── reports (Table)
                         └── report_notes (Table)
