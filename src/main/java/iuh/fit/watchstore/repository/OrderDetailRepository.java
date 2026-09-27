package iuh.fit.watchstore.repository;

import iuh.fit.watchstore.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    boolean existsByProduct_Id(Long productId);
    
    List<OrderDetail> findByOrder_Id(Long orderId);
}
