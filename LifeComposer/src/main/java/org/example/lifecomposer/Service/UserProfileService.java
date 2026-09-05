package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.UserProfile;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.dto.UserProfileDto;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfileDto getProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId);
        if (profile == null) {
            return null;
        }
        return toDto(profile);
    }

    public UserProfileDto saveOrUpdate(Long userId, UserProfileDto dto) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setCollege(dto.getCollege());
        profile.setMajor(dto.getMajor());
        profile.setGrade(dto.getGrade());
        profile.setStudentId(dto.getStudentId());
        profile.setSkillsJson(dto.getSkillsJson());
        profile.setInterestsJson(dto.getInterestsJson());
        profile.setExperiencesJson(dto.getExperiencesJson());
        profile.setPreferencesJson(dto.getPreferencesJson());
        profile.setAvailableTime(dto.getAvailableTime());
        profile.setGoals(dto.getGoals());

        boolean upserted = userProfileRepository.upsert(profile);
        if (!upserted) {
            throw new IllegalStateException("保存用户档案失败");
        }

        UserProfile saved = userProfileRepository.findByUserId(userId);
        if (saved == null) {
            throw new IllegalStateException("保存后读取用户档案失败");
        }
        return toDto(saved);
    }

    private UserProfileDto toDto(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserProfileDto dto = new UserProfileDto();
        dto.setCollege(profile.getCollege());
        dto.setMajor(profile.getMajor());
        dto.setGrade(profile.getGrade());
        dto.setStudentId(profile.getStudentId());
        dto.setSkillsJson(profile.getSkillsJson());
        dto.setInterestsJson(profile.getInterestsJson());
        dto.setExperiencesJson(profile.getExperiencesJson());
        dto.setPreferencesJson(profile.getPreferencesJson());
        dto.setAvailableTime(profile.getAvailableTime());
        dto.setGoals(profile.getGoals());
        return dto;
    }
}
