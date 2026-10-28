package com.example.insurance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.insurance.Domain.Patient;
import org.apache.ibatis.annotations.Select;

public interface PatientMapper extends BaseMapper<Patient> {
  @Select("SELECT * FROM patient WHERE id = #{id} FOR UPDATE")
  Patient lockById(Long id);
}
