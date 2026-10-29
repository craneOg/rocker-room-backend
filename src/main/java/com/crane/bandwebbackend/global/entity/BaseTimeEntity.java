package com.crane.bandwebbackend.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
