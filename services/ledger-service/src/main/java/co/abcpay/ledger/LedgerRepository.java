package co.abcpay.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.stream.Stream;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("select e from LedgerEntry e order by e.seq asc")
    Stream<LedgerEntry> streamAllOrderedBySeq();

    @Query("select e from LedgerEntry e where e.seq = (select max(e2.seq) from LedgerEntry e2)")
    Optional<LedgerEntry> findLast();
}
