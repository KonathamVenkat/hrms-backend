package com.hrms.recruitment.repository;

import com.hrms.recruitment.entity.RecruitmentStage;
import com.hrms.recruitment.enums.StageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruitmentStageRepository extends JpaRepository<RecruitmentStage, Long> {

    List<RecruitmentStage> findAllByOrderBySortOrderAscNameAsc();

    List<RecruitmentStage> findByIsActiveOrderBySortOrderAscNameAsc(Boolean isActive);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /** Used to keep exactly one active stage per terminal type. */
    boolean existsByStageTypeAndIsActive(StageType stageType, Boolean isActive);
}
