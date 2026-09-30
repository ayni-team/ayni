workspace "Ayni" "Academic time bank where university students exchange tutoring hours as credits." {

    !identifiers hierarchical

    model {
        student = person "Student" "Learns and teaches in the same account. Books tutoring with credits, offers the courses and tools they are enabled for, and requests recognition for the hours taught."
        coordinator = person "University Coordinator" "Configures their university's credit policy, reviews skill evidence and decides recognition requests."
        admin = person "Ayni Platform Administrator" "Registers universities and invites their coordinators. Sees aggregates only, never academic or personal data."

        academicSystem = softwareSystem "University Academic System" "Source of truth for each student's name, career, current term and approved courses with grades. Simulated during development." {
            tags "External"
        }
        jitsi = softwareSystem "Jitsi Meet" "Video, audio and screen sharing for the live tutoring session, embedded in Ayni." {
            tags "External"
        }
        emailService = softwareSystem "Email Service" "Delivers single use access links, presence codes and notices to institutional mailboxes." {
            tags "External"
        }
        paymentProvider = softwareSystem "Payment Provider" "Processes credit purchases and confirms them back to Ayni. Simulated during development." {
            tags "External"
        }

        ayni = softwareSystem "Ayni" "Multi-tenant platform where students exchange tutoring hours as credits. One credit equals one hour, and credits earned by teaching back recognition requests." {
            tags "Ayni"

            webApp = container "Web Application" "Single page application for students, coordinators and administrators. Independent of the API, which any future mobile client can consume the same way." "React 18, TypeScript, Vite" {
                tags "WebApp"
            }

            database = container "Database" "One schema per module, twelve schemas and forty one tables. No foreign key crosses a schema; every tenant scoped table carries tenant_id." "PostgreSQL 16" {
                tags "Database"
            }

            api = container "API Application" "Modular monolith. Exposes the whole business as a RESTful API under /api/v1, documented with OpenAPI. Twelve modules whose boundaries ModularityTest verifies on every build." "Java 21, Spring Boot 3.4, Spring Modulith" {

                requestFilters = component "Request Filters" "Bind the university and the user of each request to TenantContext and CurrentUser. Today they read the X-Tenant-Id and X-User-Id headers; with TS03 they read the session token." "Spring Servlet Filter (config)" {
                    tags "CrossCutting"
                }
                eventBus = component "Domain Event Bus" "Delivers the 24 events of pe.ayni.shared.events to the subscribed modules after the publisher commits. In memory, not persisted yet (TS04)." "Spring Application Events" {
                    tags "CrossCutting"
                }

                identity = component "identity" "Universities and their configuration, students, coordinators, access by single use link and the academic profile." "Spring Modulith module · IdentityApi" {
                    tags "Supporting"
                }
                skills = component "skills" "Course and tool catalogue, offered skills and the two accreditation paths: by grade and by reviewed evidence." "Spring Modulith module · SkillsApi" {
                    tags "Supporting"
                }
                booking = component "booking" "Tutor availability, generated hour blocks, holds, bookings and cancellations." "Spring Modulith module · BookingApi" {
                    tags "Core"
                }
                matching = component "matching" "Tutoring search answered from its own read projection, available_offers." "Spring Modulith module" {
                    tags "Supporting"
                }
                sessions = component "sessions" "Live session lifecycle, attendance, presence check and whiteboard." "Spring Modulith module · SessionsApi" {
                    tags "Core"
                }
                wallet = component "wallet" "Credit groups by origin and expiry, and the append only ledger. The only module that writes credit movements." "Spring Modulith module · WalletApi" {
                    tags "Core"
                }
                recognition = component "recognition" "Recognition requests, their frozen evidence and the university's decision." "Spring Modulith module" {
                    tags "Core"
                }
                reputation = component "reputation" "Ratings, tags and tutor standing per skill." "Spring Modulith module · ReputationApi" {
                    tags "Supporting"
                }
                payments = component "payments" "Credit purchases with an idempotency key. First candidate for extraction to a service." "Spring Modulith module" {
                    tags "Generic"
                }
                audit = component "audit" "Append only activity log and detection of anomalies shown to the coordinator." "Spring Modulith module" {
                    tags "Supporting"
                }
                analytics = component "analytics" "Usage and uncovered demand indicators, aggregates only." "Spring Modulith module" {
                    tags "Generic"
                }
                notifications = component "notifications" "Delivery of every notice. Listens to events only; nothing depends on it." "Spring Modulith module" {
                    tags "Generic"
                }
            }
        }

        # ---------------------------------------------------------------- context
        student -> ayni.webApp "Searches and books tutoring, teaches sessions, checks credits and requests recognition" "HTTPS"
        coordinator -> ayni.webApp "Configures credit policy, reviews skill evidence and recognition requests" "HTTPS"
        admin -> ayni.webApp "Registers universities, invites coordinators and reads platform indicators" "HTTPS"
        student -> jitsi "Joins the live session with video and audio" "WebRTC"
        emailService -> student "Delivers access links, presence codes and notices"
        emailService -> coordinator "Delivers access links and pending reviews"

        # ------------------------------------------------------------- containers
        ayni.webApp -> ayni.api.requestFilters "Calls /api/v1" "HTTPS/JSON"
        ayni.webApp -> jitsi "Embeds the room of the session" "Jitsi IFrame API"
        ayni.api -> ayni.database "Reads and writes, each module only in its own schema" "JDBC, Spring Data JPA, Flyway"

        # ------------------------------------ commands: calls to a published interface
        ayni.api.booking -> ayni.api.skills "Checks the tutor is enabled for the course" "SkillsApi" "Sync"
        ayni.api.booking -> ayni.api.wallet "Charges credits when booking, refunds them when cancelling" "WalletApi" "Sync"
        ayni.api.booking -> ayni.api.identity "Checks the student is active" "IdentityApi" "Sync"
        ayni.api.skills -> ayni.api.identity "Reads approved courses and the grade threshold" "IdentityApi" "Sync"
        ayni.api.matching -> ayni.api.booking "Reads a tutor's open hours while updating the projection" "BookingApi" "Sync"
        ayni.api.matching -> ayni.api.skills "Reads a tutor's enabled skills" "SkillsApi" "Sync"
        ayni.api.matching -> ayni.api.identity "Reads the tutor's name and the university's time zone" "IdentityApi" "Sync"
        ayni.api.matching -> ayni.api.reputation "Reads the tutor's standing per skill" "ReputationApi" "Sync"
        ayni.api.sessions -> ayni.api.booking "Reads the need description of the booking" "BookingApi" "Sync"
        ayni.api.sessions -> ayni.api.identity "Lists active universities for scheduled jobs" "IdentityApi" "Sync"
        ayni.api.recognition -> ayni.api.sessions "Reads the completed sessions that back a request" "SessionsApi" "Sync"
        ayni.api.recognition -> ayni.api.wallet "Reads the credits earned by teaching" "WalletApi" "Sync"
        ayni.api.recognition -> ayni.api.reputation "Reads the tutor's standing" "ReputationApi" "Sync"
        ayni.api.reputation -> ayni.api.skills "Tells a new tutor from one who does not teach the skill" "SkillsApi" "Sync"
        ayni.api.notifications -> ayni.api.identity "Reads the address of the person an event names" "IdentityApi" "Sync"

        # ------------------------------------------ external systems, behind ports
        ayni.api.identity -> academicSystem "Imports the academic profile and approved courses" "AcademicSystemPort, HTTPS/JSON" "External"
        ayni.api.sessions -> jitsi "Creates a private room per session" "VideoRoomPort" "External"
        ayni.api.notifications -> emailService "Sends access links, presence codes and notices" "EmailSenderPort, SMTP" "External"
        ayni.api.payments -> paymentProvider "Requests credit purchases" "PaymentProviderPort, HTTPS/JSON" "External"
        paymentProvider -> ayni.api.payments "Confirms or rejects the purchase" "HTTPS webhook" "External"

        # ------------------------------------------- facts: domain events published
        ayni.api.identity -> ayni.api.eventBus "Publishes AccessRequested, StudentActivated, CoordinatorActivated, UniversityRegistered, UniversitySuspended" "" "Event"
        ayni.api.skills -> ayni.api.eventBus "Publishes SkillEnabled, SkillWithdrawn, ValidationResolved" "" "Event"
        ayni.api.booking -> ayni.api.eventBus "Publishes BookingConfirmed, BookingCancelled, AvailabilityPublished, HoursGenerated, HoursWithdrawn" "" "Event"
        ayni.api.sessions -> ayni.api.eventBus "Publishes SessionStarted, SessionCompleted, SessionUnverified, PresenceCodeIssued" "" "Event"
        ayni.api.wallet -> ayni.api.eventBus "Publishes CreditsGranted, CreditsExpiring, CreditsExpired" "" "Event"
        ayni.api.recognition -> ayni.api.eventBus "Publishes RecognitionRequested, RecognitionResolved" "" "Event"
        ayni.api.reputation -> ayni.api.eventBus "Publishes SessionRated" "" "Event"
        ayni.api.payments -> ayni.api.eventBus "Publishes PurchaseConfirmed" "" "Event"

        # ------------------------------------------ facts: domain events delivered
        ayni.api.eventBus -> ayni.api.skills "Delivers StudentActivated" "" "Event"
        ayni.api.eventBus -> ayni.api.wallet "Delivers StudentActivated, SessionCompleted, SessionUnverified, BookingCancelled, PurchaseConfirmed" "" "Event"
        ayni.api.eventBus -> ayni.api.matching "Delivers HoursGenerated, HoursWithdrawn, BookingConfirmed, BookingCancelled, SkillEnabled, SkillWithdrawn, SessionRated" "" "Event"
        ayni.api.eventBus -> ayni.api.sessions "Delivers BookingConfirmed" "" "Event"
        ayni.api.eventBus -> ayni.api.reputation "Delivers SessionCompleted" "" "Event"
        ayni.api.eventBus -> ayni.api.recognition "Delivers SessionCompleted" "" "Event"
        ayni.api.eventBus -> ayni.api.audit "Delivers SessionCompleted, SessionUnverified" "" "Event"
        ayni.api.eventBus -> ayni.api.analytics "Delivers the facts it aggregates into indicators" "" "Event"
        ayni.api.eventBus -> ayni.api.notifications "Delivers AccessRequested, BookingConfirmed, BookingCancelled, PresenceCodeIssued, CreditsExpiring, RecognitionResolved, ValidationResolved" "" "Event"

        # ------------------------------------------------------------- deployment
        development = deploymentEnvironment "Development" {
            workstation = deploymentNode "Developer workstation" "Each member runs the whole system with a single command, without installing Java, Maven, Node or PostgreSQL." "Windows, macOS or Linux" {
                browser = deploymentNode "Web browser" "" "Chrome, Edge or Firefox" {
                    webInstance = containerInstance ayni.webApp
                }
                docker = deploymentNode "Docker Engine" "Started with docker compose up. Sources are mounted into the containers." "Docker Compose" {
                    webNode = deploymentNode "ayni-web" "" "node:22-alpine · port 5173" {
                        viteServer = infrastructureNode "Vite Dev Server" "Serves the web application and reloads the browser on every change." "Vite 5"
                    }
                    backendNode = deploymentNode "ayni-backend" "" "maven:3.9-eclipse-temurin-21 · port 8080" {
                        apiInstance = containerInstance ayni.api
                    }
                    postgresNode = deploymentNode "ayni-postgres" "" "postgres:16-alpine · port 5432" {
                        dbInstance = containerInstance ayni.database
                    }
                    mailpitNode = deploymentNode "ayni-mailpit" "" "axllent/mailpit:v1.27 · ports 1025, 8025" {
                        mailpit = infrastructureNode "Mailpit" "Catches every email the backend sends and shows it at localhost:8025. Nothing leaves the machine." "SMTP catcher"
                    }
                }
            }
            jitsiNode = deploymentNode "Jitsi public service" "" "meet.jit.si" {
                softwareSystemInstance jitsi
            }

            development.workstation.docker.webNode.viteServer -> development.workstation.browser.webInstance "Serves the application" "HTTP"
            development.workstation.docker.backendNode.apiInstance -> development.workstation.docker.mailpitNode.mailpit "Sends access links and presence codes" "SMTP"
        }
    }

    views {
        systemContext ayni "SystemContext" "System Context diagram for Ayni." {
            include *
            autoLayout lr
        }

        container ayni "Containers" "Container diagram for Ayni." {
            include *
            autoLayout lr
        }

        component ayni.api "Components" "The modules of the API Application and the commands between them, through published interfaces." {
            include *
            exclude ayni.api.eventBus
            exclude "relationship.tag==Event"
            autoLayout tb
        }

        component ayni.api "ComponentsEvents" "The domain events between modules, delivered by the in memory event bus." {
            include ayni.api.identity ayni.api.skills ayni.api.booking ayni.api.matching ayni.api.sessions ayni.api.wallet ayni.api.recognition ayni.api.reputation ayni.api.payments ayni.api.audit ayni.api.analytics ayni.api.notifications ayni.api.eventBus
            exclude "relationship.tag==Sync"
            autoLayout lr
        }

        deployment ayni development "DeploymentDevelopment" "Deployment of Ayni on a developer workstation." {
            include *
            autoLayout lr
        }

        styles {
            element "Element" {
                fontSize 22
            }
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                shape RoundedBox
            }
            element "Ayni" {
                background #1168bd
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "WebApp" {
                shape WebBrowser
            }
            element "Database" {
                shape Cylinder
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            element "Core" {
                background #08427b
                color #ffffff
            }
            element "Supporting" {
                background #1168bd
                color #ffffff
            }
            element "Generic" {
                background #85bbf0
                color #000000
            }
            element "CrossCutting" {
                background #dddddd
                color #000000
                shape Pipe
            }
            element "Infrastructure Node" {
                background #ffffff
                color #000000
            }
            relationship "Relationship" {
                thickness 2
            }
            relationship "Event" {
                dashed true
                color #707070
            }
            relationship "External" {
                color #999999
            }
        }
    }
}
