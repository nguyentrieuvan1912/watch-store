package iuh.fit.watchstore.repository;

import iuh.fit.watchstore.entity.Order;
import iuh.fit.watchstore.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_IdOrderByCreatedAtDesc(Long userId);
    
    List<Order> findByStatus(OrderStatus status);
    
    boolean existsByUser_Id(Long userId);
}
