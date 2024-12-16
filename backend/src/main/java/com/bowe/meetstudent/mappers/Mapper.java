package com.bowe.meetstudent.mappers;

public interface Mapper<A, B>{

    /**
     * Get a UserDTO from a UserEntity
     * @param a the user entity to map from
     * @return UserDTO that have been mapped
     */
    B toDTO(A a);


    /**
     * Get a UserEntity from a UserDTO
     * @param b the user DTO to map from
     * @return UserEntity that have been mapped
     */
    A toEntity(B b);
}
