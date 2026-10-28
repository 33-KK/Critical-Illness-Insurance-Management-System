<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, RefreshCw, ClipboardCheck, Clock3 } from "lucide-vue-next";
import { get, post, query, money, dateTime, showError, uuid } from "../api";
import type { Row } from "../api";
import { has, auth } from "../auth";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import VisitDetail from "../components/VisitDetail.vue";
const props = defineProps<{ reviewMode?: boolean }>(),
  route = useRoute(),
  router = useRouter(),
  rows = ref<Row[]>([]),
  loading = ref(false),
  keyword = ref(""),
  status = ref(props.reviewMode ? "PENDING" : ""),
  page = ref(1),
  detail = ref<Row | null>(null),
  detailLoading = ref(false),
  actionBusy = ref(false),
  rejectOpen = ref(false),
  rejectReason = ref(""),
  requestIds = new Map<string, string>();
const paged = computed(() => rows.value.slice((page.value - 1) * 10, page.value * 10));
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/visits" + query({ keyword: keyword.value, status: status.value }));
    page.value = 1;
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function inspect(id: number) {
  detailLoading.value = true;
  try {
    detail.value = await get("/visits/" + id);
  } catch (e) {
    showError(e);
  } finally {
    detailLoading.value = false;
  }
}
function actionId(key: string) {
  if (!requestIds.has(key)) requestIds.set(key, uuid());
  return requestIds.get(key)!;
}
async function review(decision: string) {
  if (actionBusy.value || !detail.value) return;
  if (decision === "REJECT" && !rejectReason.value.trim()) {
    ElMessage.warning("请填写明确的拒绝原因");
    return;
  }
  const row = detail.value;
  const reason = decision === "REJECT" ? rejectReason.value.trim() : "";
  const key = `${row.id}/${decision}/${reason}`;
  if (decision === "APPROVE") {
    try {
      await ElMessageBox.confirm(
        `确认通过 ${row.patientName} 的报销申请？本次报销 ¥ ${money(row.claimAmount)}，将记录医院结算。`,
        "确认审核通过",
        {
          confirmButtonText: "确认通过",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return;
    }
  }
  actionBusy.value = true;
  try {
    detail.value = await post(`/visits/${row.id}/review`, {
      decision,
      reason,
      requestId: actionId(key),
    });
    requestIds.delete(key);
    rejectOpen.value = false;
    ElMessage.success(
      decision === "APPROVE" ? "审核通过，已记录医院结算" : "已拒绝报销，补扣及待补缴结果已记录",
    );
    await load();
  } catch (e) {
    showError(e);
  } finally {
    actionBusy.value = false;
  }
}
async function settle(row: Row) {
  if (actionBusy.value) return;
  try {
    await ElMessageBox.confirm(
      `确认使用 ${row.patientName} 的可用医保卡余额补缴欠款？待补缴 ¥ ${money(row.outstandingAmount)}，余额不足时将进行部分补缴。`,
      "补缴欠款",
      {
        confirmButtonText: "确认补缴",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }
  actionBusy.value = true;
  const key = `${row.id}/settle`;
  try {
    const updated = await post(`/visits/${row.id}/settle-arrears`, {
      requestId: actionId(key),
    });
    requestIds.delete(key);
    if (detail.value?.id === row.id) detail.value = updated;
    ElMessage.success(
      Number(updated.outstandingAmount) > 0
        ? `已部分补缴，剩余 ¥ ${money(updated.outstandingAmount)}`
        : "欠款已缴清",
    );
    await load();
  } catch (e) {
    showError(e);
  } finally {
    actionBusy.value = false;
  }
}
onMounted(async () => {
  await load();
  if (route.query.id) await inspect(Number(route.query.id));
});
</script>
<template>
  <PageHeader
    :title="reviewMode ? '保险审核' : '就诊记录'"
    :description="
      reviewMode
        ? '核验报销申请与费用明细，让每一次审核有据可依。'
        : '查看就诊诊断、费用核算与保险审核进度，完整追踪每次医疗服务。'
    "
  >
    <el-button
      v-if="!reviewMode && has('visit:create')"
      type="primary"
      @click="router.push('/visits/new')"
    >
      <Plus :size="16" />
      新建就诊
    </el-button>
  </PageHeader>
  <div class="visit-workflow-banner" :class="{ 'is-review': reviewMode }">
    <span class="visit-workflow-icon">
      <ClipboardCheck v-if="reviewMode" :size="23" />
      <Clock3 v-else :size="23" />
    </span>
    <div>
      <strong>{{ reviewMode ? "核验申请，完成报销决定" : "每次就诊，清晰可追溯" }}</strong>
      <p>
        {{
          reviewMode
            ? "先核对参保方案、临床诊断和费用明细，再决定通过或拒绝。审核决定将保留记录。"
            : auth.user?.role === "DOCTOR"
              ? "您可以查看本人登记的就诊单，跟进报销进度与支付状态。"
              : "集中查看参保人的诊疗费用、保险审核进度与待补缴情况。"
        }}
      </p>
    </div>
    <span class="visit-workflow-caption">
      {{ reviewMode ? "通过后结算 · 拒绝后补扣" : "就诊登记 → 保险审核 → 结算 / 补缴" }}
    </span>
  </div>
  <section class="panel visit-record-panel">
    <div class="table-tabs">
      <button
        v-for="s in [
          { value: '', name: '全部记录' },
          { value: 'PENDING', name: '待审核' },
          { value: 'APPROVED', name: '审核通过' },
          { value: 'REJECTED', name: '审核拒绝' },
        ]"
        :key="s.value"
        :class="{ active: status === s.value }"
        :aria-pressed="status === s.value"
        @click="
          status = s.value;
          load();
        "
      >
        {{ s.name }}
      </button>
    </div>
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索就诊单号、参保人或诊断"
        clearable
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-button type="primary" plain @click="load">查询</el-button>
      <el-button
        text
        @click="
          keyword = '';
          load();
        "
      >
        <RefreshCw :size="15" />
        刷新
      </el-button>
      <span class="toolbar-count">共 {{ rows.length }} 笔记录</span>
    </div>
    <el-table v-loading="loading" :data="paged">
      <el-table-column label="就诊单 / 参保人" min-width="185">
        <template #default="{ row }">
          <div class="stacked-cell">
            <strong>
              {{ row.patientName }}
              <small class="muted">{{ row.outpatientType }} 类门诊</small>
            </strong>
            <small>{{ row.visitNo }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="diagnosis" label="临床诊断" min-width="160" show-overflow-tooltip />
      <el-table-column label="总费用 / 自付" width="150" align="right">
        <template #default="{ row }">
          <div class="visit-table-amount">
            <span class="table-money">¥ {{ money(row.totalAmount) }}</span>
            <small>自付 ¥ {{ money(row.selfPayAmount) }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="申请报销" width="135" align="right">
        <template #default="{ row }">
          <span class="text-success">¥ {{ money(row.claimAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="审核状态" width="120">
        <template #default="{ row }"><StatusBadge :value="row.auditStatus" /></template>
      </el-table-column>
      <el-table-column label="支付状态" width="118">
        <template #default="{ row }"><StatusBadge :value="row.paymentStatus" /></template>
      </el-table-column>
      <el-table-column prop="doctorName" label="登记医生" width="95" />
      <el-table-column label="登记时间" width="155">
        <template #default="{ row }">{{ dateTime(row.createdAt).slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="auth.user?.role === 'ADMIN' ? 150 : 100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="inspect(row.id)">
            {{ reviewMode && row.auditStatus === "PENDING" ? "去审核" : "详情" }}
          </el-button>
          <el-button
            v-if="auth.user?.role === 'ADMIN' && Number(row.outstandingAmount) > 0"
            link
            type="warning"
            :loading="actionBusy"
            @click="settle(row)"
          >
            补缴
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="reviewMode ? '当前没有符合条件的审核任务' : '暂无就诊记录'" />
      </template>
    </el-table>
    <div class="pagination">
      <span>
        {{
          auth.user?.role === "DOCTOR"
            ? "当前显示您登记的就诊记录"
            : "价格与报销比例以登记时的快照为准"
        }}
      </span>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
  <el-drawer
    :model-value="!!detail"
    :title="reviewMode ? '报销申请审核' : '就诊单详情'"
    size="790px"
    @close="detail = null"
  >
    <div v-if="detail" v-loading="detailLoading">
      <VisitDetail :visit="detail" />
    </div>
    <template #footer>
      <div class="drawer-footer visit-action-bar">
        <div v-if="detail" class="visit-action-summary">
          <template v-if="detail.auditStatus === 'PENDING' && has('audit:review')">
            <span>本次申请报销</span>
            <strong>¥ {{ money(detail.claimAmount) }}</strong>
          </template>
          <template v-else-if="Number(detail.outstandingAmount) > 0">
            <span>待补缴金额</span>
            <strong>¥ {{ money(detail.outstandingAmount) }}</strong>
          </template>
          <template v-else>
            <span>本次诊疗总费用</span>
            <strong>¥ {{ money(detail.totalAmount) }}</strong>
          </template>
        </div>
        <div class="visit-action-buttons">
          <el-button @click="detail = null">关闭</el-button>
          <el-button
            v-if="detail && auth.user?.role === 'ADMIN' && Number(detail.outstandingAmount) > 0"
            type="warning"
            :loading="actionBusy"
            @click="settle(detail)"
          >
            补缴欠款
          </el-button>
          <template v-if="detail?.auditStatus === 'PENDING' && has('audit:review')">
            <el-button
              type="danger"
              plain
              :disabled="actionBusy"
              @click="
                rejectReason = '';
                rejectOpen = true;
              "
            >
              拒绝报销
            </el-button>
            <el-button type="primary" :loading="actionBusy" @click="review('APPROVE')">
              审核通过
            </el-button>
          </template>
        </div>
      </div>
    </template>
  </el-drawer>
  <el-dialog
    v-model="rejectOpen"
    title="拒绝报销申请"
    width="480px"
    :close-on-click-modal="!actionBusy"
  >
    <el-alert
      title="拒绝为最终决定，系统将补扣拟报销金额；余额不足时形成待补缴。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-form label-position="top" class="mt24">
      <el-form-item label="拒绝原因" required>
        <el-input
          v-model="rejectReason"
          type="textarea"
          :rows="4"
          placeholder="请填写清晰、具体的拒绝原因"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="actionBusy" @click="rejectOpen = false">取消</el-button>
      <el-button type="danger" :loading="actionBusy" @click="review('REJECT')">确认拒绝</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.visit-workflow-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 21px 24px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #eff6ff;
  margin-bottom: 24px;
}
.visit-workflow-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  color: #2563eb;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #dbeafe;
}
.visit-workflow-banner strong {
  display: block;
  color: #172b4d;
  font-size: 15px;
  font-weight: 600;
}
.visit-workflow-banner p {
  margin-top: 7px;
  color: #526987;
  font-size: 13px;
  line-height: 1.8;
}
.visit-workflow-caption {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 12px;
  color: #2563eb;
  background: #fff;
  border: 1px solid #dbeafe;
  padding: 8px 11px;
  border-radius: 6px;
}
.visit-record-panel {
  overflow: hidden;
}
.visit-table-amount {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.visit-table-amount small {
  font-size: 12px;
  color: #64748b;
}
.visit-action-bar {
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.visit-action-summary {
  text-align: left;
  flex-shrink: 0;
}
.visit-action-summary span {
  display: block;
  font-size: 12px;
  color: #64748b;
}
.visit-action-summary strong {
  display: block;
  margin-top: 5px;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #172b4d;
}
.visit-action-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}
.visit-action-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 1250px) {
  .visit-workflow-caption {
    display: none;
  }
}
@media (max-width: 700px) {
  .visit-workflow-banner {
    align-items: flex-start;
    padding: 17px 16px;
    gap: 12px;
    margin-bottom: 18px;
  }
  .visit-workflow-icon {
    width: 36px;
    height: 36px;
    border-radius: 8px;
  }
  .visit-workflow-banner strong {
    font-size: 14px;
  }
  .visit-workflow-banner p {
    font-size: 12px;
  }
  .visit-record-panel .table-tabs {
    overflow-x: auto;
  }
  .visit-record-panel .table-tabs button {
    white-space: nowrap;
    flex-shrink: 0;
    font-size: 13px;
  }
  .visit-action-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
  }
  .visit-action-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .visit-action-summary strong {
    margin-top: 0;
  }
  .visit-action-buttons {
    display: grid;
    grid-auto-flow: column;
    grid-auto-columns: minmax(0, 1fr);
    gap: 8px;
  }
  .visit-action-buttons :deep(.el-button) {
    min-width: 0;
    padding: 10px 12px;
  }
}
</style>
