package com.crane.bandwebbackend.domain.part.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MembershipPartId implements Serializable {

    private Long membership;
    private Long part;
}
