package com.crane.bandwebbackend.domain.song.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SongPartRequirementId implements Serializable {

    private Long song;
    private Long part;
}
