package com.example.insurance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.insurance.Domain.Visit;
import org.apache.ibatis.annotations.Select;

public interface VisitMapper extends BaseMapper<Visit> {
  @Select("SELECT * FROM medical_visit WHERE id = #{id} FOR UPDATE")
  Visit lockById(Long id);
}
