package iuh.fit.watchstore.repository;

import iuh.fit.watchstore.entity.UserTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserTrackingRepository extends JpaRepository<UserTracking, Long> {

    List<UserTracking> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);

    List<UserTracking> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
