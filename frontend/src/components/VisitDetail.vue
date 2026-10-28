<script setup lang="ts">
import { money, dateTime } from "../api";
import { UserRound, FileText, ShieldCheck, Clock3 } from "lucide-vue-next";
import StatusBadge from "./StatusBadge.vue";
defineProps<{ visit: Record<string, any> }>();
</script>
<template>
  <div class="detail-hero visit-detail-heading">
    <div>
      <span class="eyebrow">就诊单号</span>
      <h2>{{ visit.visitNo }}</h2>
      <span class="muted">{{ dateTime(visit.createdAt) }} · 登记医生 {{ visit.doctorName }}</span>
    </div>
    <StatusBadge :value="visit.auditStatus" />
  </div>
  <div class="visit-amount-overview">
    <div class="amount-total">
      <span>诊疗总费用</span>
      <strong>
        <small>¥</small>
        {{ money(visit.totalAmount) }}
      </strong>
    </div>
    <div>
      <span>登记时自付</span>
      <strong>
        <small>¥</small>
        {{ money(visit.selfPayAmount) }}
      </strong>
    </div>
    <div class="amount-claim">
      <span>申请报销</span>
      <strong>
        <small>¥</small>
        {{ money(visit.claimAmount) }}
      </strong>
    </div>
    <div :class="{ 'amount-arrears': Number(visit.outstandingAmount) > 0 }">
      <span>待补缴金额</span>
      <strong>
        <small>¥</small>
        {{ money(visit.outstandingAmount) }}
      </strong>
    </div>
  </div>
  <div class="visit-payment-status">
    <span>支付状态</span>
    <StatusBadge :value="visit.paymentStatus" />
    <small v-if="visit.auditStatus === 'APPROVED'">审核已通过，已记录医院结算。</small>
    <small v-else-if="visit.auditStatus === 'PENDING'">
      自付金额已扣除，申请报销金额等待审核。
    </small>
    <small v-else>拒赔后的补扣与补缴结果以资金流水为准。</small>
  </div>
  <el-alert
    v-if="Number(visit.outstandingAmount) > 0"
    :title="`待补缴金额 ¥ ${money(visit.outstandingAmount)}，充值后由管理员发起补缴。`"
    type="warning"
    :closable="false"
    show-icon
  />
  <el-alert
    v-if="visit.rejectReason"
    :title="`拒绝原因：${visit.rejectReason}`"
    type="error"
    :closable="false"
    show-icon
    class="mt16"
  />

  <section class="visit-detail-section">
    <div class="visit-section-heading">
      <UserRound :size="18" />
      <h3>参保与就诊信息</h3>
    </div>
    <dl class="visit-information-grid">
      <div>
        <dt>参保人</dt>
        <dd>{{ visit.patientName }}</dd>
      </div>
      <div>
        <dt>医保卡号</dt>
        <dd>{{ visit.cardNo }}</dd>
      </div>
      <div>
        <dt>保险方案</dt>
        <dd>{{ visit.planName }}</dd>
      </div>
      <div>
        <dt>门诊类型</dt>
        <dd>{{ visit.outpatientType }} 类门诊</dd>
      </div>
      <div class="diagnosis-information">
        <dt>临床诊断</dt>
        <dd>{{ visit.diagnosis || "—" }}</dd>
      </div>
    </dl>
  </section>

  <section class="visit-detail-section">
    <div class="visit-section-heading">
      <FileText :size="18" />
      <h3>费用与项目明细</h3>
      <span>登记时快照</span>
    </div>
    <div class="visit-snapshot-note">
      <ShieldCheck :size="16" />
      <span>
        报销比例 {{ Math.round(Number(visit.reimbursementRate || 0) * 10000) / 100 }}% ·
        采用登记时的项目价格与费率，后续调整不影响本单。
      </span>
    </div>
    <el-table :data="visit.items || []">
      <el-table-column prop="surgeryName" label="手术 / 治疗项目" min-width="170" />
      <el-table-column label="单价" width="115" align="right">
        <template #default="{ row }">¥ {{ money(row.unitPrice) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="70" align="center" />
      <el-table-column label="项目小计" width="125" align="right">
        <template #default="{ row }">
          <span class="table-money">¥ {{ money(row.subtotal) }}</span>
        </template>
      </el-table-column>
      <template #empty><div class="no-surgery-note">本次未登记手术或治疗项目</div></template>
    </el-table>
    <div class="visit-fee-breakdown">
      <div>
        <span>独立诊断费用</span>
        <strong>¥ {{ money(visit.diagnosisFee) }}</strong>
      </div>
      <div>
        <span>手术与治疗费用</span>
        <strong>¥ {{ money(visit.surgeryFee) }}</strong>
      </div>
      <div class="breakdown-total">
        <span>费用合计</span>
        <strong>¥ {{ money(visit.totalAmount) }}</strong>
      </div>
    </div>
  </section>

  <section class="visit-detail-section">
    <div class="visit-section-heading">
      <Clock3 :size="18" />
      <h3>办理与审核轨迹</h3>
    </div>
    <el-timeline class="visit-audit-timeline">
      <el-timeline-item :timestamp="dateTime(visit.createdAt)" type="primary">
        <strong>已登记就诊 · {{ visit.doctorName }}</strong>
        <p class="muted">已扣除自付金额 ¥ {{ money(visit.selfPayAmount) }}，提交保险报销申请。</p>
      </el-timeline-item>
      <el-timeline-item
        v-for="(r, i) in visit.auditRecords || []"
        :key="i"
        :timestamp="dateTime(r.createdAt)"
        :type="r.decision === 'APPROVE' ? 'success' : 'danger'"
      >
        <strong>
          {{ r.decision === "APPROVE" ? "审核通过" : "拒绝报销" }} · {{ r.reviewerName }}
        </strong>
        <p class="muted">
          {{
            r.reason ||
            (r.decision === "APPROVE"
              ? "审核完成，已记录医院结算。"
              : "已拒绝报销，补扣与待补缴结果已记录。")
          }}
        </p>
      </el-timeline-item>
      <el-timeline-item v-if="visit.auditStatus === 'PENDING'" type="warning" hollow>
        <strong>等待保险审核</strong>
        <p class="muted">审核员将核验诊断、费用项目与参保方案。</p>
      </el-timeline-item>
    </el-timeline>
  </section>
</template>

<style scoped>
.visit-detail-heading {
  align-items: flex-start;
  padding-bottom: 22px;
}
.visit-detail-heading h2 {
  color: #172b4d;
  font-size: 22px;
  overflow-wrap: anywhere;
}
.visit-detail-heading .muted {
  display: block;
  font-size: 13px;
  line-height: 1.7;
}
.visit-detail-heading > :last-child {
  flex-shrink: 0;
  margin-top: 25px;
}
.visit-amount-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.visit-amount-overview > div {
  padding: 17px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
}
.visit-amount-overview span {
  display: block;
  font-size: 12px;
  color: #64748b;
}
.visit-amount-overview strong {
  display: block;
  margin-top: 10px;
  color: #172b4d;
  font-size: 21px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  letter-spacing: -0.5px;
}
.visit-amount-overview strong small {
  font-size: 13px;
  font-weight: 400;
}
.visit-amount-overview .amount-total {
  background: #eff6ff;
  border-color: #dbeafe;
}
.amount-total strong {
  color: #2563eb;
}
.amount-claim strong {
  color: #16866a;
}
.visit-amount-overview .amount-arrears {
  background: #fff7ed;
  border-color: #ffedd5;
}
.amount-arrears strong {
  color: #b45309;
}
.visit-payment-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-bottom: 18px;
  font-size: 13px;
  color: #64748b;
}
.visit-payment-status small {
  font-size: 12px;
  line-height: 1.7;
}
.visit-detail-section {
  margin-top: 28px;
}
.visit-section-heading {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 17px;
  color: #2563eb;
}
.visit-section-heading h3 {
  font-size: 15px;
  color: #172b4d;
  font-weight: 600;
  margin: 0;
}
.visit-section-heading > span {
  margin-left: auto;
  color: #64748b;
  font-size: 12px;
}
.visit-information-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  padding: 20px;
  margin: 0;
}
.visit-information-grid dt {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 9px;
}
.visit-information-grid dd {
  color: #172b4d;
  font-size: 14px;
  margin: 0;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.diagnosis-information {
  grid-column: 1 / -1;
  padding-top: 18px;
  border-top: 1px solid #e2e8f0;
}
.visit-snapshot-note {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 12px 14px;
  color: #526987;
  background: #f3f6fb;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
  margin-bottom: 16px;
}
.visit-snapshot-note svg {
  margin-top: 3px;
  flex-shrink: 0;
  color: #2563eb;
}
.no-surgery-note {
  padding: 16px 0;
  font-size: 13px;
  color: #64748b;
}
.visit-fee-breakdown {
  padding: 12px 16px 0;
  background: #f8fafc;
  border-radius: 8px;
  margin-top: 16px;
}
.visit-fee-breakdown > div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  font-size: 13px;
  color: #64748b;
  padding: 10px 0;
}
.visit-fee-breakdown strong {
  font-weight: 500;
  color: #172b4d;
  font-variant-numeric: tabular-nums;
}
.visit-fee-breakdown .breakdown-total {
  border-top: 1px solid #e2e8f0;
  margin-top: 5px;
  padding: 15px 0;
  color: #172b4d;
}
.breakdown-total strong {
  font-weight: 600;
}
.visit-audit-timeline {
  padding-left: 5px;
  padding-top: 4px;
  margin-bottom: 0;
}
.visit-audit-timeline :deep(.el-timeline-item__content strong) {
  font-size: 13px;
  font-weight: 500;
  color: #172b4d;
}
.visit-audit-timeline :deep(.el-timeline-item__content p) {
  font-size: 12px;
  color: #64748b;
  line-height: 1.8;
}
.visit-audit-timeline :deep(.el-timeline-item__timestamp) {
  font-size: 12px;
}
@media (max-width: 600px) {
  .visit-amount-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
  .visit-amount-overview > div {
    padding: 15px 12px;
  }
  .visit-amount-overview strong {
    font-size: 23px;
  }
  .visit-detail-heading {
    flex-wrap: wrap;
    gap: 12px;
  }
  .visit-detail-heading > :last-child {
    margin-top: 0;
  }
  .visit-detail-heading h2 {
    font-size: 19px;
  }
  .visit-information-grid {
    padding: 16px;
    gap: 20px 14px;
  }
  .visit-information-grid dd {
    font-size: 13px;
  }
  .visit-section-heading h3 {
    font-size: 14px;
  }
  .visit-payment-status small {
    flex-basis: 100%;
  }
}
</style>
