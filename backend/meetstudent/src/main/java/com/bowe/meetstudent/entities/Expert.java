package com.bowe.meetstudent.entities;

import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
//@Data
//@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
//@Table(name = "experts")
public class Expert extends User{

}
