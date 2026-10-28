<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search, RefreshCw, Users, WalletCards, ArrowUpRight } from "lucide-vue-next";
import { get, post, put, remove, money, dateTime, showError, uuid, query } from "../api";
import type { Row } from "../api";
import { has } from "../auth";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
const rows = ref<Row[]>([]),
  plans = ref<Row[]>([]),
  loading = ref(false),
  keyword = ref(""),
  page = ref(1),
  size = ref(10),
  dialog = ref(false),
  detail = ref<Row | null>(null),
  ledger = ref<Row[]>([]),
  detailLoading = ref(false),
  saving = ref(false),
  formRef = ref(),
  rechargeOpen = ref(false),
  rechargePatient = ref<Row>(),
  rechargeAmount = ref<number | undefined>(),
  rechargeNote = ref(""),
  rechargeBusy = ref(false),
  rechargeId = ref("");
const form = reactive<Row>({}),
  paged = computed(() => rows.value.slice((page.value - 1) * size.value, page.value * size.value));
const rules = {
  name: [{ required: true, message: "请输入姓名", trigger: "blur" }],
  gender: [{ required: true, message: "请选择性别", trigger: "change" }],
  idNumber: [{ required: true, message: "请输入证件号码", trigger: "blur" }],
  cardNo: [{ required: true, message: "请输入医保卡号", trigger: "blur" }],
  planId: [{ required: true, message: "请选择保险方案", trigger: "change" }],
  phone: [
    {
      pattern: /^\d{7,15}$/,
      message: "请输入7至15位联系电话",
      trigger: "blur",
    },
  ],
};
async function load() {
  loading.value = true;
  try {
    rows.value = await get("/patients" + query({ keyword: keyword.value }));
    page.value = 1;
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
function edit(row?: Row) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    row
      ? { ...row }
      : {
          name: "",
          gender: "男",
          birthDate: "",
          phone: "",
          idNumber: "",
          cardNo: "",
          address: "",
          planId: undefined,
          enabled: true,
        },
  );
  dialog.value = true;
  setTimeout(() => formRef.value?.clearValidate(), 0);
}
async function save() {
  if (saving.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name,
      gender: form.gender,
      birthDate: form.birthDate || null,
      phone: form.phone,
      idNumber: form.idNumber,
      cardNo: form.cardNo,
      address: form.address,
      planId: form.planId,
      enabled: form.enabled,
    };
    if (form.id) await put("/patients/" + form.id, payload);
    else await post("/patients", payload);
    ElMessage.success("参保档案已保存");
    dialog.value = false;
    await load();
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
async function del(row: Row) {
  try {
    await ElMessageBox.confirm(
      `确认停用“${row.name}”的参保档案？停用后不能登记新就诊，历史记录和账户流水仍将保留。`,
      "停用参保人",
      {
        type: "warning",
        confirmButtonText: "确认停用",
        cancelButtonText: "取消",
      },
    );
    await remove("/patients/" + row.id);
    ElMessage.success("参保档案已停用");
    await load();
  } catch (e) {
    if (e instanceof Error) showError(e);
  }
}
async function inspect(row: Row) {
  detail.value = row;
  ledger.value = [];
  detailLoading.value = true;
  try {
    detail.value = await get("/patients/" + row.id);
    if (has("card:ledger")) ledger.value = await get(`/patients/${row.id}/transactions`);
  } catch (e) {
    showError(e);
  } finally {
    detailLoading.value = false;
  }
}
function recharge(row: Row) {
  rechargePatient.value = row;
  rechargeAmount.value = undefined;
  rechargeNote.value = "";
  rechargeId.value = uuid();
  rechargeOpen.value = true;
}
// Retrying an unchanged command reuses its ID. Editing creates a distinct command.
watch([rechargeAmount, rechargeNote], () => {
  if (!rechargeBusy.value) rechargeId.value = uuid();
});
async function submitRecharge() {
  if (rechargeBusy.value) return;
  const amount = Number(rechargeAmount.value);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 50000) {
    ElMessage.warning("单次充值金额须大于0且不超过50,000元");
    return;
  }
  rechargeBusy.value = true;
  try {
    await post(`/patients/${rechargePatient.value?.id}/recharges`, {
      amount,
      requestId: rechargeId.value,
      note: rechargeNote.value,
    });
    rechargeId.value = uuid();
    rechargeOpen.value = false;
    ElMessage.success("充值成功，资金流水已生成");
    await load();
    if (detail.value?.id === rechargePatient.value?.id) await inspect(detail.value!);
  } catch (e) {
    showError(e);
  } finally {
    rechargeBusy.value = false;
  }
}
onMounted(async () => {
  await load();
  if (has("plan:view") || has("patient:manage"))
    try {
      plans.value = await get("/plans");
    } catch (e) {
      showError(e);
    }
});
</script>
<template>
  <PageHeader
    title="参保人档案"
    description="管理参保信息、保险关联与医保卡账户，让每一份保障清晰可查。"
  >
    <el-button v-if="has('patient:manage')" type="primary" @click="edit()">
      <Plus :size="16" />
      新增参保人
    </el-button>
  </PageHeader>
  <div class="mini-stats">
    <div>
      <span class="mini-stat-icon teal"><Users :size="20" /></span>
      <div>
        <small>当前查询人数</small>
        <strong>
          {{ rows.length }}
          <em>人</em>
        </strong>
      </div>
    </div>
    <div>
      <span class="mini-stat-icon blue"><WalletCards :size="20" /></span>
      <div>
        <small>当前查询账户余额</small>
        <strong>¥ {{ money(rows.reduce((s, r) => s + Number(r.balance || 0), 0)) }}</strong>
      </div>
    </div>
    <div>
      <span class="mini-stat-icon amber"><ArrowUpRight :size="20" /></span>
      <div>
        <small>待补缴金额</small>
        <strong>
          ¥ {{ money(rows.reduce((s, r) => s + Number(r.outstandingAmount || 0), 0)) }}
        </strong>
      </div>
    </div>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索姓名、证件号码、医保卡号"
        clearable
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-button @click="load">查询</el-button>
      <el-button
        text
        @click="
          keyword = '';
          load();
        "
      >
        <RefreshCw :size="15" />
        重置
      </el-button>
      <span class="toolbar-count">共 {{ rows.length }} 位参保人</span>
    </div>
    <el-table v-loading="loading" :data="paged" row-key="id">
      <el-table-column label="参保人" min-width="140">
        <template #default="{ row }">
          <div class="person-cell">
            <span class="person-avatar">{{ row.name?.slice(-1) }}</span>
            <div>
              <strong>{{ row.name }}</strong>
              <small>{{ row.gender }} · {{ row.phone || "未留电话" }}</small>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="cardNo" label="医保卡号" min-width="145" />
      <el-table-column prop="planName" label="保险方案" min-width="150" />
      <el-table-column label="账户余额" min-width="125">
        <template #default="{ row }">
          <span class="table-money">¥ {{ money(row.balance) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="待补缴" width="110">
        <template #default="{ row }">
          <span :class="Number(row.outstandingAmount) > 0 ? 'text-danger' : 'muted'">
            {{ Number(row.outstandingAmount) > 0 ? "¥ " + money(row.outstandingAmount) : "—" }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusBadge :value="row.enabled" /></template>
      </el-table-column>
      <el-table-column
        label="操作"
        :width="has('patient:manage') ? 230 : has('card:recharge') ? 140 : 80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button link type="primary" @click="inspect(row)">详情</el-button>
          <el-button v-if="has('card:recharge')" link type="primary" @click="recharge(row)">
            充值
          </el-button>
          <el-button v-if="has('patient:manage')" link type="primary" @click="edit(row)">
            编辑
          </el-button>
          <el-button v-if="has('patient:manage')" link type="danger" @click="del(row)">
            停用
          </el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="没有符合条件的参保档案" /></template>
    </el-table>
    <div class="pagination">
      <span>档案信息与资金账户独立维护</span>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
  <el-dialog
    v-model="dialog"
    :title="form.id ? '编辑参保档案' : '新增参保人'"
    width="660px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="30" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker
            v-model="form.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择出生日期"
            :disabled-date="(d: Date) => d.getTime() > Date.now()"
          />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" maxlength="15" />
        </el-form-item>
        <el-form-item label="证件号码" prop="idNumber">
          <el-input v-model="form.idNumber" maxlength="25" />
        </el-form-item>
        <el-form-item label="医保卡号" prop="cardNo">
          <el-input v-model="form.cardNo" maxlength="30" />
        </el-form-item>
        <el-form-item label="保险方案" prop="planId">
          <el-select v-model="form.planId" filterable placeholder="请选择保险方案">
            <el-option
              v-for="p in plans.filter((p) => p.enabled || p.id === form.planId)"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="档案状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="联系地址" class="grid-full">
          <el-input v-model="form.address" maxlength="200" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存档案</el-button>
    </template>
  </el-dialog>
  <el-drawer :model-value="!!detail" title="参保档案详情" size="760px" @close="detail = null">
    <div v-if="detail" v-loading="detailLoading">
      <div class="patient-detail-card">
        <span class="patient-big-avatar">{{ detail.name?.slice(-1) }}</span>
        <div>
          <h2>{{ detail.name }}</h2>
          <p>
            {{ detail.planName }}
            <StatusBadge :value="detail.enabled" />
          </p>
        </div>
        <div class="patient-detail-balance">
          <small>医保卡余额</small>
          <strong>¥ {{ money(detail.balance) }}</strong>
        </div>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="医保卡号">{{ detail.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="证件号码">{{ detail.idNumber }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ detail.birthDate || "—" }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.phone || "—" }}</el-descriptions-item>
        <el-descriptions-item label="联系地址" :span="2">
          {{ detail.address || "—" }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="Number(detail.outstandingAmount) > 0"
        type="warning"
        :title="`待补缴 ¥ ${money(detail.outstandingAmount)}。请先充值并在就诊记录中完成补缴，再登记新就诊。`"
        :closable="false"
        show-icon
        class="mt16"
      />
      <div v-if="has('card:recharge')" class="detail-action">
        <el-button type="primary" @click="recharge(detail)">医保卡充值</el-button>
      </div>
      <template v-if="has('card:ledger')">
        <div class="section-title">
          资金流水
          <span>记录每一次账户变动</span>
        </div>
        <el-table :data="ledger">
          <el-table-column label="类型" width="120">
            <template #default="{ row }"><StatusBadge :value="row.type" /></template>
          </el-table-column>
          <el-table-column label="金额" width="125">
            <template #default="{ row }">
              <span :class="Number(row.amount) > 0 ? 'text-success' : 'text-danger'">
                {{ Number(row.amount) > 0 ? "+" : "" }}{{ money(row.amount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">¥ {{ money(row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </el-drawer>
  <el-dialog
    v-model="rechargeOpen"
    title="医保卡充值"
    width="460px"
    :close-on-click-modal="!rechargeBusy"
  >
    <div class="recharge-summary">
      <span>{{ rechargePatient?.name }} · {{ rechargePatient?.cardNo }}</span>
      <small>当前余额</small>
      <strong>¥ {{ money(rechargePatient?.balance) }}</strong>
    </div>
    <el-form label-position="top" :disabled="rechargeBusy">
      <el-form-item label="充值金额（元）" required>
        <el-input-number
          v-model="rechargeAmount"
          :min="0.01"
          :max="50000"
          :precision="2"
          :controls="false"
          placeholder="请输入充值金额"
          class="full-width"
        />
      </el-form-item>
      <div class="amount-options">
        <el-button v-for="a in [1000, 5000, 10000]" :key="a" @click="rechargeAmount = a">
          ¥ {{ a.toLocaleString() }}
        </el-button>
      </div>
      <el-form-item label="充值备注">
        <el-input v-model="rechargeNote" placeholder="可填写充值说明" maxlength="200" />
      </el-form-item>
    </el-form>
    <p class="form-help">单次充值不超过 50,000 元。充值成功后生成资金流水；欠款需单独发起补缴。</p>
    <template #footer>
      <el-button @click="rechargeOpen = false" :disabled="rechargeBusy">取消</el-button>
      <el-button type="primary" :loading="rechargeBusy" @click="submitRecharge">确认充值</el-button>
    </template>
  </el-dialog>
</template>
