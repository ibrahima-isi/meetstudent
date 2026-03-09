package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.TagDTO;
import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.mappers.Mapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagMapper implements Mapper<Tag, TagDTO> {

    private final ModelMapper modelMapper;

    @Override
    public TagDTO toDTO(Tag tag) {
        return modelMapper.map(tag, TagDTO.class);
    }

    @Override
    public Tag toEntity(TagDTO tagDTO) {
        return modelMapper.map(tagDTO, Tag.class);
    }
}
