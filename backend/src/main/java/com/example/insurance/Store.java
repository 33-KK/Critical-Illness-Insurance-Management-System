package com.example.insurance;

import com.example.insurance.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Store {
  final UserMapper users;
  final RoleMapper roles;
  final OrgMapper orgs;
  final DictMapper dicts;
  final MenuMapper menus;
  final PlanMapper plans;
  final SurgeryMapper surgeries;
  final PatientMapper patients;
  final VisitMapper visits;
  final VisitItemMapper items;
  final AuditRecordMapper audits;
  final CardTransactionMapper transactions;
  final RequestRecordMapper requests;
  final SessionMapper sessions;
  final LogMapper logs;
  final StoredFileMapper files;
}
