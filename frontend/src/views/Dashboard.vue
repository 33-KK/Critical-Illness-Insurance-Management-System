<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import VChart from "vue-echarts";
import "../charts/echarts";
import { buildAuditOption, buildTrendOption } from "../charts/dashboard";
import type { DashboardSummary } from "../charts/dashboard";
import {
  Users,
  ClipboardCheck,
  ArrowRight,
  Activity,
  Plus,
  ChevronRight,
  WalletCards,
  RefreshCw,
  ShieldCheck,
  FileText,
  Stethoscope,
  CircleHelp,
  CheckCircle2,
} from "lucide-vue-next";
import { get, money, dateTime, showError } from "../api";
import { auth, has, roleLabels } from "../auth";
import StatusBadge from "../components/StatusBadge.vue";

const router = useRouter();
const data = ref<DashboardSummary | null>(null);
const loading = ref(false);
const loadFailed = ref(false);
const trendOption = computed(() => buildTrendOption(data.value?.monthly));
const auditOption = computed(() =>
  buildAuditOption(data.value?.statusCounts, data.value?.visitCount),
);
const isDoctor = computed(() => auth.user?.role === "DOCTOR");
const dateText = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(new Date());
const greeting = computed(() => {
  const h = new Date().getHours();
  return h < 12 ? "上午好" : h < 18 ? "下午好" : "晚上好";
});
const priority = computed(() => {
  if (has("audit:review"))
    return {
      label: "审核工作",
      title: "报销申请，及时处理",
      description: data.value
        ? `当前有 ${data.value.pendingCount} 笔申请等待审核，请核验诊断与费用明细。`
        : "核验诊断、费用与报销明细，完成保险审核。",
      action: "进入审核",
      path: "/reviews",
      icon: ClipboardCheck,
    };
  if (has("visit:create"))
    return {
      label: "临床工作",
      title: "开始一次就诊登记",
      description: "选择参保人，登记诊断与手术费用，试算后提交报销申请。",
      action: "新建就诊",
      path: "/visits/new",
      icon: Stethoscope,
    };
  if (has("card:recharge"))
    return {
      label: "账户工作",
      title: "医保账户，有序管理",
      description: "办理医保卡充值、核对资金流水，并跟进拒赔后的待补缴款项。",
      action: "办理充值",
      path: "/patients",
      icon: WalletCards,
    };
  return {
    label: "业务工作",
    title: "掌握每一笔业务进展",
    description: "从参保档案到就诊记录，快速找到您需要处理的业务。",
    action: "查看业务",
    path: has("visit:view") ? "/visits" : has("patient:view") ? "/patients" : "/profile",
    icon: Activity,
  };
});
const quickActions = computed(() =>
  [
    {
      permission: "patient:view",
      title: has("patient:manage") ? "参保档案维护" : "参保档案查询",
      description: "身份信息与账户余额",
      path: "/patients",
      icon: Users,
    },
    {
      permission: "plan:view",
      title: "保险方案",
      description: "保障方案与报销比例",
      path: "/plans",
      icon: ShieldCheck,
    },
    {
      permission: "card:ledger",
      title: "资金流水",
      description: "充值、扣款与结算记录",
      path: "/transactions",
      icon: WalletCards,
    },
    {
      permission: "system:user",
      title: "用户管理",
      description: "团队账户与角色分工",
      path: "/users",
      icon: Users,
    },
    {
      permission: "surgery:view",
      title: "手术项目",
      description: "手术信息与收费标准",
      path: "/surgeries",
      icon: FileText,
    },
  ]
    .filter((action) => has(action.permission))
    .slice(0, 4),
);
const cards = computed(() => [
  {
    title: "参保档案",
    value: data.value?.insuredCount.toLocaleString("zh-CN") ?? "—",
    unit: "人",
    hint: "系统已建立的参保档案",
    icon: Users,
    color: "blue",
  },
  {
    title: isDoctor.value ? "本人就诊登记" : "累计就诊登记",
    value: data.value?.visitCount.toLocaleString("zh-CN") ?? "—",
    unit: "笔",
    hint: data.value ? `今日登记 ${data.value.todayVisits} 笔` : "等待业务数据",
    icon: Activity,
    color: "blue",
  },
  {
    title: isDoctor.value ? "本人待审核申请" : "待审核申请",
    value: data.value?.pendingCount.toLocaleString("zh-CN") ?? "—",
    unit: "笔",
    hint: "已提交，等待保险审核",
    icon: ClipboardCheck,
    color: "amber",
  },
  {
    title: isDoctor.value ? "本人登记报销金额" : "累计报销金额",
    value: data.value ? money(data.value.reimbursedAmount) : "—",
    unit: "元",
    hint: "仅计审核通过的报销金额",
    icon: CheckCircle2,
    color: "green",
  },
]);
async function load() {
  if (loading.value) return;
  loading.value = true;
  try {
    data.value = await get<DashboardSummary>("/dashboard");
    loadFailed.value = false;
  } catch (error) {
    loadFailed.value = true;
    showError(error);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div class="workbench" v-loading="loading">
    <header class="wb-header">
      <div>
        <h1>工作台</h1>
        <p>{{ greeting }}，{{ auth.user?.displayName }}。欢迎回到安澜医保。</p>
      </div>
      <div class="wb-header-tools">
        <span class="wb-date">{{ dateText }}</span>
        <el-button :loading="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </el-button>
      </div>
    </header>
    <el-alert
      v-if="loadFailed"
      class="wb-error"
      type="error"
      :closable="false"
      show-icon
      :title="
        data
          ? '数据刷新失败，当前展示上一次获取的结果，请稍后重试。'
          : '工作台数据暂时无法加载，请点击刷新重试。'
      "
    />

    <div class="wb-action-row">
      <section class="wb-priority">
        <div class="wb-priority-content">
          <div class="wb-priority-label">
            <component :is="priority.icon" :size="17" />
            <span>{{ priority.label }}</span>
            <span class="wb-role">{{ roleLabels[auth.user?.role] || "业务人员" }}</span>
          </div>
          <h2>{{ priority.title }}</h2>
          <p>{{ priority.description }}</p>
        </div>
        <el-button type="primary" @click="router.push(priority.path)">
          {{ priority.action }}
          <ArrowRight :size="16" />
        </el-button>
      </section>
      <section class="wb-shortcuts wb-surface" aria-label="快捷办理">
        <h2>快捷办理</h2>
        <div class="wb-shortcut-grid">
          <button
            v-for="action in quickActions"
            :key="action.path"
            class="wb-shortcut"
            @click="router.push(action.path)"
          >
            <span class="wb-shortcut-icon"><component :is="action.icon" :size="19" /></span>
            <span>
              <strong>{{ action.title }}</strong>
              <small>{{ action.description }}</small>
            </span>
            <ChevronRight :size="16" class="wb-shortcut-arrow" />
          </button>
          <p v-if="!quickActions.length" class="wb-shortcut-empty">
            可从左侧导航查看已授权的功能。
          </p>
        </div>
      </section>
    </div>

    <div class="wb-metrics" aria-label="业务概览">
      <section v-for="card in cards" :key="card.title" class="wb-metric wb-surface">
        <div class="wb-metric-heading">
          <span>{{ card.title }}</span>
          <component :is="card.icon" :size="18" :class="`wb-color-${card.color}`" />
        </div>
        <div class="wb-metric-value" :class="{ 'wb-money-value': card.unit === '元' }">
          {{ card.value }}
          <small>{{ card.unit }}</small>
        </div>
        <p>{{ card.hint }}</p>
      </section>
    </div>

    <section class="wb-surface wb-records">
      <div class="wb-section-heading">
        <div>
          <h2>{{ isDoctor ? "我最近登记的就诊" : "最近就诊记录" }}</h2>
          <p>查看最新业务进展，快速进入单据详情。</p>
        </div>
        <el-button v-if="has('visit:view')" text type="primary" @click="router.push('/visits')">
          全部记录
          <ChevronRight :size="15" />
        </el-button>
      </div>
      <el-table :data="data?.recentVisits || []" class="wb-recent-table">
        <el-table-column label="参保人 / 就诊单号" min-width="185">
          <template #default="{ row }">
            <div class="wb-person">
              <span class="wb-avatar">{{ row.patientName?.slice(-1) }}</span>
              <span>
                <strong>{{ row.patientName }}</strong>
                <small>{{ row.visitNo }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="就诊总费用" min-width="130" align="right">
          <template #default="{ row }">
            <span class="wb-table-money">¥ {{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" min-width="125">
          <template #default="{ row }"><StatusBadge :value="row.auditStatus" /></template>
        </el-table-column>
        <el-table-column label="登记时间" min-width="160">
          <template #default="{ row }">
            <span class="wb-table-date">{{ dateTime(row.createdAt).slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="has('visit:view')"
              link
              type="primary"
              @click="router.push({ path: '/visits', query: { id: row.id } })"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="wb-empty">
            <ClipboardCheck :size="29" />
            <strong>{{ loadFailed ? "暂时无法获取就诊记录" : "暂无就诊记录" }}</strong>
            <p>
              {{
                loadFailed
                  ? "请刷新后重试"
                  : has("visit:create")
                    ? "从新建就诊开始，登记患者的诊断与费用。"
                    : "医生提交就诊后，记录将在这里展示。"
              }}
            </p>
            <el-button
              v-if="!loadFailed && has('visit:create')"
              type="primary"
              plain
              @click="router.push('/visits/new')"
            >
              <Plus :size="15" />
              新建就诊
            </el-button>
          </div>
        </template>
      </el-table>
    </section>

    <div class="wb-analysis-row">
      <section class="wb-surface wb-trend-panel">
        <div class="wb-section-heading">
          <div>
            <h2>业务运行趋势</h2>
            <p>近六个月就诊费用与登记数量</p>
          </div>
          <span class="wb-period">月度统计</span>
        </div>
        <div class="wb-trend-chart"><VChart :option="trendOption" autoresize /></div>
      </section>
      <section class="wb-surface wb-audit-panel">
        <div class="wb-section-heading">
          <div>
            <h2>审核状态分布</h2>
            <p>{{ isDoctor ? "本人登记单据的审核概况" : "全部就诊单据的审核概况" }}</p>
          </div>
        </div>
        <div class="wb-audit-chart"><VChart :option="auditOption" autoresize /></div>
      </section>
    </div>
    <div class="wb-account-bar">
      <WalletCards :size="19" />
      <span>医保卡账户总余额</span>
      <strong>{{ data ? `¥ ${money(data.totalBalance)}` : "—" }}</strong>
      <span class="wb-account-note">
        <CircleHelp :size="15" />
        系统全部参保账户余额，以资金流水为依据。
      </span>
      <el-button
        v-if="has('card:ledger')"
        link
        type="primary"
        @click="router.push('/transactions')"
      >
        查看流水
        <ArrowRight :size="15" />
      </el-button>
    </div>
    <p v-if="isDoctor" class="wb-scope-note">
      就诊、审核及报销统计仅包含本人登记的记录；参保档案数量和账户总余额为系统汇总。
    </p>
  </div>
</template>

<style scoped>
.workbench {
  color: #172b4d;
  font-size: 14px;
}
.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}
.wb-header h1 {
  font-size: 26px;
  line-height: 1.4;
  letter-spacing: 0;
}
.wb-header p {
  margin-top: 6px;
  color: #64748b;
  line-height: 1.6;
}
.wb-header-tools {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.wb-date {
  color: #64748b;
  font-size: 13px;
}
.wb-error {
  margin-bottom: 18px;
}
.wb-surface {
  min-width: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.wb-action-row {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}
.wb-priority {
  padding: 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border: 1px solid #d9e6fc;
  border-radius: 10px;
  background: #eef4ff;
}
.wb-priority-content {
  min-width: 0;
}
.wb-priority-label {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 12px;
}
.wb-role {
  margin-left: 3px;
  padding-left: 10px;
  border-left: 1px solid #c6d7f7;
  color: #526886;
  font-size: 12px;
  font-weight: 400;
}
.wb-priority h2 {
  font-size: 19px;
  line-height: 1.4;
  margin-bottom: 8px;
}
.wb-priority p {
  max-width: 370px;
  color: #526886;
  font-size: 13px;
  line-height: 1.8;
}
.wb-priority > .el-button {
  flex-shrink: 0;
}
.wb-shortcuts {
  padding: 18px 20px;
}
.wb-shortcuts > h2 {
  font-size: 15px;
  margin-bottom: 12px;
}
.wb-shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
}
.wb-shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0;
  text-align: left;
  color: #172b4d;
  background: none;
  border: 0;
  border-radius: 6px;
}
.wb-shortcut:hover strong {
  color: #2563eb;
}
.wb-shortcut-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  color: #2563eb;
  background: #f0f5ff;
  border-radius: 8px;
}
.wb-shortcut > span:nth-child(2) {
  min-width: 0;
}
.wb-shortcut strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
}
.wb-shortcut small {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-top: 2px;
  line-height: 1.5;
}
.wb-shortcut-arrow {
  margin-left: auto;
  color: #94a3b8;
}
.wb-shortcut-empty {
  grid-column: 1 / -1;
  color: #64748b;
  line-height: 1.7;
}
.wb-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.wb-metric {
  padding: 17px 20px;
}
.wb-metric-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #52647d;
  font-size: 13px;
}
.wb-color-blue {
  color: #2563eb;
}
.wb-color-amber {
  color: #b7791f;
}
.wb-color-green {
  color: #16866a;
}
.wb-metric-value {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin: 10px 0 5px;
  font-size: 29px;
  font-weight: 600;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.4px;
  overflow-wrap: anywhere;
}
.wb-metric-value.wb-money-value {
  font-size: 26px;
}
.wb-metric-value small {
  font-size: 12px;
  font-weight: 400;
  color: #64748b;
}
.wb-metric > p {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}
.wb-records {
  margin-bottom: 20px;
  overflow: hidden;
}
.wb-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 19px 22px;
}
.wb-section-heading h2 {
  font-size: 16px;
  line-height: 1.5;
}
.wb-section-heading p {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
.wb-section-heading > .el-button {
  flex-shrink: 0;
}
.wb-recent-table {
  padding: 0 12px 8px;
}
.wb-person {
  display: flex;
  align-items: center;
  gap: 11px;
}
.wb-avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #edf3ff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 500;
}
.wb-person strong {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #172b4d;
  line-height: 1.4;
}
.wb-person small {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.4;
}
.wb-table-money {
  color: #172b4d;
  font-weight: 500;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}
.wb-table-date {
  color: #52647d;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.wb-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 16px 34px;
  line-height: 1.6;
}
.wb-empty > svg {
  color: #8da7d7;
  margin-bottom: 10px;
}
.wb-empty strong {
  color: #52647d;
  font-size: 14px;
  font-weight: 500;
}
.wb-empty p {
  margin: 5px 0 12px;
  color: #64748b;
  font-size: 13px;
}
.wb-analysis-row {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(290px, 1fr);
  gap: 20px;
  margin-bottom: 18px;
}
.wb-period {
  background: #f3f6fb;
  border: 1px solid #e5ebf3;
  border-radius: 5px;
  padding: 5px 9px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}
.wb-trend-chart,
.wb-audit-chart {
  height: 245px;
  margin: 0 12px 14px;
}
.wb-account-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  padding: 16px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #52647d;
  font-size: 13px;
}
.wb-account-bar > svg {
  color: #2563eb;
}
.wb-account-bar strong {
  color: #172b4d;
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.wb-account-note {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  color: #64748b;
  font-size: 12px;
}
.wb-scope-note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
  margin: 12px 2px 0;
}
@media (max-width: 1250px) {
  .wb-priority {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }
  .wb-shortcut-grid {
    gap: 16px 12px;
  }
  .wb-shortcut-arrow {
    display: none;
  }
  .wb-metric {
    padding: 16px;
  }
  .wb-metric-value {
    font-size: 27px;
  }
  .wb-metric-value.wb-money-value {
    font-size: 24px;
  }
}
@media (max-width: 900px) {
  .wb-action-row {
    grid-template-columns: 1fr;
  }
  .wb-priority {
    flex-direction: row;
    align-items: center;
  }
  .wb-shortcut-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }
  .wb-shortcut {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .wb-shortcut small {
    display: none;
  }
  .wb-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .wb-analysis-row {
    grid-template-columns: 1fr;
  }
  .wb-account-note {
    width: 100%;
    margin: 2px 0 0;
  }
}
@media (max-width: 560px) {
  .wb-header {
    gap: 12px;
    align-items: flex-start;
    margin-bottom: 16px;
  }
  .wb-header h1 {
    font-size: 23px;
  }
  .wb-header p {
    font-size: 13px;
  }
  .wb-date {
    display: none;
  }
  .wb-priority {
    align-items: flex-start;
    flex-direction: column;
    padding: 19px;
  }
  .wb-priority h2 {
    font-size: 18px;
  }
  .wb-priority-label {
    margin-bottom: 10px;
  }
  .wb-shortcuts {
    padding: 18px;
  }
  .wb-shortcut-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 12px;
  }
  .wb-shortcut {
    flex-direction: row;
    align-items: center;
  }
  .wb-shortcut-icon {
    width: 32px;
    height: 32px;
  }
  .wb-metrics {
    gap: 12px;
    margin-bottom: 16px;
  }
  .wb-metric {
    padding: 14px;
  }
  .wb-metric-heading {
    font-size: 12px;
  }
  .wb-metric-heading > svg {
    width: 15px;
  }
  .wb-metric-value {
    font-size: 25px;
    flex-wrap: wrap;
    column-gap: 5px;
    row-gap: 0;
    margin: 9px 0 5px;
  }
  .wb-metric-value.wb-money-value {
    font-size: 22px;
  }
  .wb-section-heading {
    padding: 17px;
    align-items: flex-start;
  }
  .wb-section-heading h2 {
    font-size: 15px;
  }
  .wb-section-heading p {
    font-size: 12px;
  }
  .wb-section-heading > .el-button {
    padding: 5px 0;
  }
  .wb-recent-table {
    padding: 0 0 6px;
  }
  .wb-trend-chart,
  .wb-audit-chart {
    margin-left: 4px;
    margin-right: 4px;
  }
  .wb-account-bar {
    padding: 15px;
  }
  .wb-account-note {
    align-items: flex-start;
    line-height: 1.7;
  }
  .wb-account-note > svg {
    margin-top: 3px;
  }
}
</style>
