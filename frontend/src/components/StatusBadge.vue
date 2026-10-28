<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{ value?: string | boolean; kind?: string }>();
const dict: Record<string, [string, string]> = {
  PENDING: ["待审核", "amber"],
  APPROVED: ["审核通过", "green"],
  REJECTED: ["审核拒绝", "red"],
  SELF_PAID: ["自付已扣", "blue"],
  SETTLED: ["已结算", "green"],
  PAID_IN_FULL: ["已缴清", "green"],
  PENDING_TOPUP: ["待补缴", "red"],
  RECHARGE: ["账户充值", "green"],
  SELF_PAY: ["就诊自付", "blue"],
  REJECT_DEDUCT: ["拒绝补扣", "red"],
  ARREARS_PAYMENT: ["补缴欠款", "amber"],
  HOSPITAL_SETTLEMENT: ["医院结算", "green"],
  ADMIN: ["管理员", "blue"],
  DOCTOR: ["医生", "green"],
  REVIEWER: ["审核员", "amber"],
};
const state = computed(() =>
  typeof props.value === "boolean"
    ? [props.value ? "启用" : "停用", props.value ? "green" : "gray"]
    : dict[String(props.value)] || [props.value || "—", "gray"],
);
dict.REJECT_DEDUCTION = ["拒绝补扣", "red"];
</script>
<template>
  <span class="status-badge" :class="state[1]">
    <i></i>
    {{ state[0] }}
  </span>
</template>
