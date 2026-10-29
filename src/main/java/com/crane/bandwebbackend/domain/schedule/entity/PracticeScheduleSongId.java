package com.crane.bandwebbackend.domain.schedule.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PracticeScheduleSongId implements Serializable {

    private Long practiceSchedule;
    private Long song;
}
