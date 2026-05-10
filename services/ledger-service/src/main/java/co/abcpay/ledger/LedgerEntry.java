package co.abcpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entry", schema = "ledger")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;

    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "text", nullable = false)
    private String payloadJson;

    @Column(name = "payload_hash", length = 64, nullable = false)
    private String payloadHash;

    @Column(name = "prev_hash", length = 64, nullable = false)
    private String prevHash;

    @Column(name = "record_hash", length = 64)
    private String recordHash;

    @Column(name = "created_at_iso", length = 32, nullable = false)
    private String createdAtIso;

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getEventId() { return eventId; }
    public void setEventId(String v) { this.eventId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String v) { this.payloadHash = v; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String v) { this.prevHash = v; }
    public String getRecordHash() { return recordHash; }
    public void setRecordHash(String v) { this.recordHash = v; }
    public String getCreatedAtIso() { return createdAtIso; }
    public void setCreatedAtIso(String v) { this.createdAtIso = v; }
}
