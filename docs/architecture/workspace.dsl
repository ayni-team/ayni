workspace "Ayni" "Academic time bank where university students exchange tutoring hours as credits." {

    !identifiers hierarchical

    model {
        student = person "Student" "Learns and teaches in the same account. Books tutoring with credits, offers the courses and tools they are enabled for, and requests recognition for the hours taught."
        coordinator = person "University Coordinator" "Configures their university's credit policy, reviews skill evidence and decides recognition requests."
        admin = person "Ayni Platform Administrator" "Registers universities and invites their coordinators. Sees aggregates only, never academic or personal data."

        ayni = softwareSystem "Ayni" "Multi-tenant platform where students exchange tutoring hours as credits. One credit equals one hour, and credits earned by teaching back recognition requests." {
            tags "Ayni"
        }

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

        student -> ayni "Searches and books tutoring, teaches sessions, checks credits and requests recognition" "HTTPS"
        coordinator -> ayni "Configures credit policy, reviews skill evidence and recognition requests" "HTTPS"
        admin -> ayni "Registers universities, invites coordinators and reads platform indicators" "HTTPS"

        ayni -> academicSystem "Imports academic profile and approved courses by student code" "HTTPS/JSON"
        ayni -> jitsi "Creates a private room per session" "HTTPS"
        student -> jitsi "Joins the live session with video and audio" "WebRTC"
        ayni -> emailService "Sends access links, presence codes and notices" "SMTP"
        emailService -> student "Delivers access links, presence codes and notices"
        emailService -> coordinator "Delivers access links and pending reviews"
        ayni -> paymentProvider "Requests credit purchase" "HTTPS/JSON"
        paymentProvider -> ayni "Confirms or rejects the purchase" "HTTPS webhook"
    }

    views {
        systemContext ayni "SystemContext" "System Context diagram for Ayni." {
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
        }
    }
}
