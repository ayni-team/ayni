package pe.ayni.sessions.domain.model;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_materials")
public class SupportMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    private String title;

    private String url;

    @Enumerated(EnumType.STRING)
    private SupportMaterialStatus status;

    public SupportMaterial() {
    }

    public SupportMaterial(Long sessionId, String title, String url, SupportMaterialStatus status) {
        this.sessionId = sessionId;
        this.title = title;
        this.url = url;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public SupportMaterialStatus getStatus() {
        return status;
    }

    public void setStatus(SupportMaterialStatus status) {
        this.status = status;
    }
}