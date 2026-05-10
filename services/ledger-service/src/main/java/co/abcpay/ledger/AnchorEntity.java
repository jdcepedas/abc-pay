package co.abcpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "anchor", schema = "ledger")
public class AnchorEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "last_seq", nullable = false)
    private Long lastSeq;

    @Column(name = "last_record_hash", length = 64, nullable = false)
    private String lastRecordHash;

    @Column(name = "anchor_hmac", length = 64, nullable = false)
    private String anchorHmac;

    @Column(name = "updated_at_iso", length = 32, nullable = false)
    private String updatedAtIso;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getLastSeq() { return lastSeq; }
    public void setLastSeq(Long v) { this.lastSeq = v; }
    public String getLastRecordHash() { return lastRecordHash; }
    public void setLastRecordHash(String v) { this.lastRecordHash = v; }
    public String getAnchorHmac() { return anchorHmac; }
    public void setAnchorHmac(String v) { this.anchorHmac = v; }
    public String getUpdatedAtIso() { return updatedAtIso; }
    public void setUpdatedAtIso(String v) { this.updatedAtIso = v; }
}
