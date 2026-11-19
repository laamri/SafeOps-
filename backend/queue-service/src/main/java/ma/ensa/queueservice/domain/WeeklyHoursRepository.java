package ma.ensa.queueservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WeeklyHoursRepository extends JpaRepository<WeeklyHours, Long> {

    List<WeeklyHours> findByEstablishmentIdOrderByDayOfWeekAscOpensAtAsc(String establishmentId);

    @Modifying
    @Query("delete from WeeklyHours h where h.establishment.id = :establishmentId")
    void deleteByEstablishmentId(@Param("establishmentId") String establishmentId);
}
