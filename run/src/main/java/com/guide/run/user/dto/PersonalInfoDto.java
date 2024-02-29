package com.guide.run.user.dto;

import com.guide.run.user.entity.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class PersonalInfoDto {
    private String role;
    private String type;
    private String gender;
    private String name;
    private String phoneNumber;
    private Boolean isOpenNumber;
    private int age;
    private String snsId;
    private Boolean isOpenSns;

    public static PersonalInfoDto userToInfoDto(User user){
        return PersonalInfoDto.builder()
                .role(user.getRole().getValue())
                .type(user.getType().getValue())
                .name(user.getName())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .age(user.getAge())
                .snsId(user.getSnsId())
                .isOpenSns(user.getIsOpenSns())
                .isOpenNumber(user.getIsOpenNumber())
                .build();
    }
}
