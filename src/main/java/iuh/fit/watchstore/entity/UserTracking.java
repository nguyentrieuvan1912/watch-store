package iuh.fit.watchstore.entity;

import iuh.fit.watchstore.entity.enums.ActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    private Long productId;

    private String searchKeyword;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
