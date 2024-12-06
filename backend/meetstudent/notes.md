# Relationship Between rate and rated entities:
```@ManyToOne - @OneToMany```

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "course")
    private Set<CourseRate> courseRates;
}

package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "schools")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "school")
    private Set<SchoolRate> schoolRates;
}

package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "program")
    private Set<ProgramRate> programRates;
}

# Relationship between Program and Accreditation 
```@ManyToMany - @ManyToMany```
Pour une relation @ManyToMany entre les entités Accreditation et Program, vous pouvez laisser JPA créer la table de jointure automatiquement en utilisant les annotations.  
Cependant, si vous utilisez Flyway pour la gestion des migrations de base de données, il est recommandé de créer explicitement la table de jointure dans un script de migration.  
Voici comment configurer la relation @ManyToMany avec les annotations JPA :  
package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accreditations")
public class Accreditation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(mappedBy = "accreditations")
    private Set<Program> programs;
}

package com.bowe.meetstudent.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany
    @JoinTable(
        name = "program_accreditation",
        joinColumns = @JoinColumn(name = "program_id"),
        inverseJoinColumns = @JoinColumn(name = "accreditation_id")
    )
    private Set<Accreditation> accreditations;
}  
Pour Flyway, vous devez créer un script de migration pour créer la table de jointure explicitement. Voici un exemple de script SQL pour créer la table de jointure program_accreditation :
```
-- V1__create_program_accreditation_table.sql
CREATE TABLE program_accreditation (
program_id BIGINT NOT NULL,
accreditation_id BIGINT NOT NULL,
PRIMARY KEY (program_id, accreditation_id),
FOREIGN KEY (program_id) REFERENCES programs(id),
FOREIGN KEY (accreditation_id) REFERENCES accreditations(id)
);
```
