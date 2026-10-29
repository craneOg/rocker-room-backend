package com.crane.bandwebbackend.domain.availability.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AvailableSlotId implements Serializable {

    private Long performanceMember;
    private LocalDateTime slotStart;
}
