<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  ArrowLeft,
  Plus,
  Trash2,
  Calculator,
  ShieldCheck,
  UserRound,
  Check,
  Info,
  WalletCards,
  ChevronRight,
} from "lucide-vue-next";
import { get, post, money, showError, uuid } from "../api";
import type { Row } from "../api";
import PageHeader from "../components/PageHeader.vue";
const router = useRouter(),
  patients = ref<Row[]>([]),
  surgeries = ref<Row[]>([]),
  loading = ref(false),
  saving = ref(false),
  quoting = ref(false),
  quote = ref<Row | null>(null),
  quoteError = ref(""),
  formRef = ref(),
  requestId = ref(uuid());
let timer: ReturnType<typeof setTimeout> | undefined,
  quoteSequence = 0;
const form = reactive({
  patientId: undefined as number | undefined,
  outpatientType: "A",
  diagnosisFee: 0,
  diagnosis: "",
  items: [] as { surgeryId: number | undefined; quantity: number }[],
});
const patient = computed(() => patients.value.find((p) => p.id === form.patientId)),
  available = computed(() => surgeries.value.filter((s) => s.enabled)),
  blocked = computed(
    () => Number(patient.value?.outstandingAmount) > 0 || patient.value?.enabled === false,
  );
const rules = {
  patientId: [{ required: true, message: "请选择参保人", trigger: "change" }],
  diagnosis: [{ required: true, message: "请输入临床诊断", trigger: "blur" }],
  outpatientType: [{ required: true, message: "请选择门诊类型", trigger: "change" }],
};
function payload() {
  return {
    patientId: form.patientId,
    outpatientType: form.outpatientType,
    diagnosisFee: form.diagnosisFee,
    diagnosis: form.diagnosis,
    items: form.items.map((i) => ({
      surgeryId: i.surgeryId,
      quantity: i.quantity,
    })),
  };
}
const ready = computed(
  () =>
    !!form.patientId &&
    !!form.diagnosis.trim() &&
    form.items.every(
      (i) => i.surgeryId && Number.isInteger(i.quantity) && i.quantity >= 1 && i.quantity <= 100,
    ) &&
    Number.isFinite(form.diagnosisFee) &&
    form.diagnosisFee >= 0 &&
    (form.diagnosisFee > 0 || form.items.length > 0),
);
async function calculate(silent = false) {
  const sequence = ++quoteSequence;
  quote.value = null;
  quoteError.value = "";
  if (!ready.value) return false;
  quoting.value = true;
  try {
    const r = await post("/visits/quote", payload());
    if (sequence === quoteSequence) quote.value = r;
    return sequence === quoteSequence;
  } catch (e) {
    if (sequence === quoteSequence)
      quoteError.value = e instanceof Error ? e.message : "费用试算失败";
    if (!silent) showError(e);
    return false;
  } finally {
    if (sequence === quoteSequence) quoting.value = false;
  }
}
watch(
  form,
  () => {
    requestId.value = uuid();
    quote.value = null;
    quoteError.value = "";
    quoteSequence++;
    quoting.value = false;
    clearTimeout(timer);
    if (ready.value) timer = setTimeout(() => calculate(true), 420);
  },
  { deep: true },
);
function addItem() {
  form.items.push({ surgeryId: undefined, quantity: 1 });
}
async function submit() {
  if (saving.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  if (blocked.value) {
    ElMessage.warning("该参保人存在欠款或已停用，暂不能登记新就诊");
    return;
  }
  if (!ready.value) {
    ElMessage.warning("请完整选择手术项目和有效数量");
    return;
  }
  saving.value = true;
  clearTimeout(timer);
  try {
    if (!(await calculate())) return;
    await post("/visits", { ...payload(), requestId: requestId.value });
    requestId.value = uuid();
    ElMessage.success("就诊登记成功，自付金额已扣除，报销申请已进入待审核");
    router.push("/visits");
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  loading.value = true;
  try {
    const r = await Promise.all([get<Row[]>("/patients"), get<Row[]>("/surgeries")]);
    patients.value = r[0];
    surgeries.value = r[1];
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
});
onBeforeUnmount(() => clearTimeout(timer));
</script>
<template>
  <PageHeader
    title="新建就诊"
    description="确认参保信息，登记本次诊疗项目，核对费用后提交报销申请。"
  >
    <el-button @click="router.push('/visits')">
      <ArrowLeft :size="16" />
      返回就诊记录
    </el-button>
  </PageHeader>
  <ol class="registration-steps" aria-label="就诊登记流程">
    <li class="current">
      <span class="registration-step-index">
        <Check v-if="patient" :size="17" />
        <template v-else>1</template>
      </span>
      <div>
        <strong>确认参保信息</strong>
        <small>选择本次就诊的参保人</small>
      </div>
    </li>
    <ChevronRight :size="17" aria-hidden="true" />
    <li :class="{ current: !!patient }">
      <span class="registration-step-index">
        <Check v-if="ready" :size="17" />
        <template v-else>2</template>
      </span>
      <div>
        <strong>登记诊疗项目</strong>
        <small>填写诊断与费用明细</small>
      </div>
    </li>
    <ChevronRight :size="17" aria-hidden="true" />
    <li :class="{ current: !!quote }">
      <span class="registration-step-index">3</span>
      <div>
        <strong>核对并提交</strong>
        <small>确认自付金额与报销申请</small>
      </div>
    </li>
  </ol>
  <div class="visit-create-grid" v-loading="loading">
    <div>
      <section class="panel form-panel">
        <div class="panel-heading">
          <div>
            <h3>
              <span class="step-number">01</span>
              参保人与就诊信息
            </h3>
            <p>参保档案、医保卡与本次临床诊断</p>
          </div>
          <UserRound :size="21" class="muted" />
        </div>
        <el-form ref="formRef" :disabled="saving" :model="form" :rules="rules" label-position="top">
          <el-form-item label="选择参保人" prop="patientId">
            <el-select
              v-model="form.patientId"
              filterable
              placeholder="输入姓名或医保卡号选择参保人"
              size="large"
            >
              <el-option
                v-for="p in patients"
                :key="p.id"
                :label="`${p.name} · ${p.cardNo}`"
                :value="p.id"
              >
                <span>
                  {{ p.name }}
                  <small class="muted">{{ p.cardNo }}</small>
                </span>
                <span class="option-right">余额 ¥ {{ money(p.balance) }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <div v-if="patient" class="selected-patient">
            <span class="person-avatar">{{ patient.name?.slice(-1) }}</span>
            <div class="selected-patient-identity">
              <strong>
                {{ patient.name }}
                <small>{{ patient.gender }}</small>
              </strong>
              <p>{{ patient.planName }} · {{ patient.cardNo }}</p>
            </div>
            <div class="selected-patient-balance">
              <small>当前医保卡余额</small>
              <strong>¥ {{ money(patient.balance) }}</strong>
            </div>
          </div>
          <el-alert
            v-if="blocked"
            :title="
              patient?.enabled === false
                ? '参保档案已停用，请联系审核员维护。'
                : `尚有 ¥ ${money(patient?.outstandingAmount)} 待补缴，请先充值并结清欠款。`
            "
            type="warning"
            show-icon
            :closable="false"
            class="mb20"
          />
          <div class="visit-form-divider"><span>本次就诊</span></div>
          <div class="form-grid">
            <el-form-item label="门诊类型" prop="outpatientType">
              <el-radio-group v-model="form.outpatientType">
                <el-radio-button label="A" value="A">A 类门诊</el-radio-button>
                <el-radio-button label="B" value="B">B 类门诊</el-radio-button>
                <el-radio-button label="C" value="C">C 类门诊</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="独立诊断费用（元）" prop="diagnosisFee">
              <el-input-number
                v-model="form.diagnosisFee"
                :min="0"
                :max="10000000"
                :precision="2"
                :controls="false"
                class="full-width"
              />
            </el-form-item>
            <el-form-item label="临床诊断" prop="diagnosis" class="grid-full">
              <el-input
                v-model="form.diagnosis"
                type="textarea"
                :rows="3"
                placeholder="请描述本次病情、诊断结论等信息"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
          </div>
        </el-form>
      </section>
      <section class="panel form-panel mt24">
        <div class="panel-heading">
          <div>
            <h3>
              <span class="step-number">02</span>
              手术与治疗项目
            </h3>
            <p>按实际治疗项目登记，单价与数量将保存至本次就诊单。</p>
          </div>
          <el-button size="small" :disabled="saving" @click="addItem">
            <Plus :size="15" />
            添加项目
          </el-button>
        </div>
        <div class="surgery-item-heading">
          <span>手术项目</span>
          <span>治疗数量</span>
          <span>单价 / 元</span>
          <span></span>
        </div>
        <div class="surgery-item" v-for="(item, i) in form.items" :key="i">
          <el-select
            v-model="item.surgeryId"
            :disabled="saving"
            filterable
            placeholder="搜索并选择手术项目"
          >
            <el-option
              v-for="s in available"
              :key="s.id"
              :label="s.name"
              :value="s.id"
              :disabled="form.items.some((x, j) => j !== i && x.surgeryId === s.id)"
            >
              <span>{{ s.name }}</span>
              <span class="option-right">¥ {{ money(s.price) }}</span>
            </el-option>
          </el-select>
          <el-input-number
            v-model="item.quantity"
            :disabled="saving"
            :min="1"
            :max="100"
            :precision="0"
            controls-position="right"
          />
          <span class="surgery-price" data-label="单价">
            {{
              item.surgeryId ? money(surgeries.find((s) => s.id === item.surgeryId)?.price) : "—"
            }}
          </span>
          <button
            class="icon-button danger"
            title="移除手术项目"
            :disabled="saving"
            @click="form.items.splice(i, 1)"
          >
            <Trash2 :size="16" />
          </button>
        </div>
        <button
          v-if="!form.items.length"
          class="add-surgery-empty"
          :disabled="saving"
          @click="addItem"
        >
          <Plus :size="22" />
          <span>添加手术或治疗项目</span>
          <small>本次无手术时，可仅填写诊断费用</small>
        </button>
        <p class="form-help mt16">
          <Info :size="14" />
          手术价格将随单据保存，后续调整价格不会影响本次费用。
        </p>
      </section>
      <div class="flow-note">
        <strong>提交后会发生什么？</strong>
        <div>
          <span class="flow-circle">1</span>
          自付金额从医保卡余额中扣除
        </div>
        <div>
          <span class="flow-circle">2</span>
          申请进入保险审核员的待办
        </div>
        <div>
          <span class="flow-circle">3</span>
          通过后结算，拒绝后补扣报销额
        </div>
      </div>
    </div>
    <aside class="quote-sidebar">
      <section class="panel quote-panel">
        <div class="panel-heading">
          <div>
            <h3>
              <Calculator :size="18" />
              本次费用摘要
            </h3>
            <p>依据所选参保方案与门诊类型试算</p>
          </div>
          <span class="live-dot">{{ quoting ? "试算中" : quote ? "已更新" : "待试算" }}</span>
        </div>
        <div v-loading="quoting" class="quote-content">
          <template v-if="quote">
            <div class="quote-line">
              <span>诊断费用</span>
              <strong>¥ {{ money(quote.diagnosisFee) }}</strong>
            </div>
            <div class="quote-line">
              <span>手术费用</span>
              <strong>¥ {{ money(quote.surgeryFee) }}</strong>
            </div>
            <div class="quote-line quote-total">
              <span>费用总额</span>
              <strong>¥ {{ money(quote.totalAmount) }}</strong>
            </div>
            <div class="reimbursement-card">
              <div>
                <ShieldCheck :size="16" />
                <span>申请报销金额</span>
                <strong>{{ Math.round(Number(quote.reimbursementRate) * 10000) / 100 }}%</strong>
              </div>
              <p>¥ {{ money(quote.claimAmount) }}</p>
              <small>审核通过后记录医院结算</small>
            </div>
            <div class="self-pay">
              <span>提交时扣除自付</span>
              <strong>
                <small>¥</small>
                {{ money(quote.selfPayAmount) }}
              </strong>
            </div>
            <div class="quote-after">
              <WalletCards :size="15" />
              <span>预计扣款后余额</span>
              <strong
                :class="Number(patient?.balance) < Number(quote.selfPayAmount) ? 'text-danger' : ''"
              >
                ¥ {{ money(Number(patient?.balance) - Number(quote.selfPayAmount)) }}
              </strong>
            </div>
            <el-alert
              v-if="Number(patient?.balance) < Number(quote.selfPayAmount)"
              title="医保卡余额不足，请联系管理员充值。"
              type="warning"
              :closable="false"
              class="mt16"
            />
          </template>
          <div v-else class="quote-placeholder">
            <Calculator :size="42" :stroke-width="1" />
            <p>{{ quoteError || "完善就诊信息后自动计算费用" }}</p>
            <small>
              {{
                quoteError
                  ? "请检查参保信息或费用后重新试算"
                  : "选择参保人，填写诊断及有效费用后即可试算"
              }}
            </small>
          </div>
        </div>
        <div class="quote-footer">
          <el-button
            type="primary"
            size="large"
            class="full-width"
            :loading="saving"
            :disabled="
              blocked ||
              !ready ||
              (!quote && !quoteError) ||
              !!quoteError ||
              (quote && Number(patient?.balance) < Number(quote.selfPayAmount))
            "
            @click="submit"
          >
            提交就诊登记
          </el-button>
          <el-button
            text
            class="full-width"
            :loading="quoting"
            :disabled="!ready"
            @click="calculate()"
          >
            重新试算费用
          </el-button>
          <p>
            <ShieldCheck :size="13" />
            提交后扣除自付金额并进入保险审核
          </p>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.registration-steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  list-style: none;
  padding: 20px 24px;
  margin: 0 0 24px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  color: #94a3b8;
}
.registration-steps li {
  display: flex;
  align-items: center;
  gap: 12px;
}
.registration-step-index {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 600;
  background: #f8fafc;
}
.registration-steps strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}
.registration-steps small {
  display: block;
  font-size: 12px;
  margin-top: 5px;
}
.registration-steps .current .registration-step-index {
  color: #2563eb;
  background: #eff6ff;
  border-color: #bfdbfe;
}
.registration-steps .current strong {
  color: #172b4d;
}
.visit-form-divider {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 26px 0 21px;
  font-size: 13px;
  color: #64748b;
}
.visit-form-divider::after {
  content: "";
  height: 1px;
  flex: 1;
  background: #e2e8f0;
}
.selected-patient-identity {
  min-width: 0;
}
.selected-patient-identity p {
  overflow-wrap: anywhere;
}
.quote-sidebar {
  top: 20px;
}
.quote-panel {
  overflow: hidden;
}
.quote-panel .panel-heading {
  padding: 18px 20px 10px;
  margin-bottom: 0;
}
.quote-panel .panel-heading p {
  font-size: 12px;
  line-height: 1.6;
}
.quote-content {
  padding: 0 20px;
  min-height: 260px;
}
.quote-line {
  padding: 8px 0;
  font-size: 14px;
  line-height: 1.5;
}
.quote-line strong {
  font-size: 14px;
}
.quote-total {
  margin: 4px 0 10px;
  padding-top: 12px;
}
.quote-total strong {
  font-size: 18px;
}
.reimbursement-card {
  padding: 12px 14px;
  margin-bottom: 12px;
}
.reimbursement-card p {
  margin: 7px 0 3px;
  font-size: 26px;
  line-height: 1.25;
}
.quote-panel .self-pay {
  border-radius: 8px;
  margin: 12px 0;
  font-size: 14px;
}
.self-pay strong {
  font-size: 25px;
  line-height: 1.3;
}
.quote-after {
  padding: 10px;
}
.quote-footer {
  margin-top: 16px;
  padding: 16px 20px;
}
.quote-footer p {
  margin-top: 8px;
  line-height: 1.6;
}
.quote-footer :deep(.el-button) {
  margin-left: 0;
}
.flow-note {
  padding: 22px 6px 0;
  line-height: 1.7;
}
.registration-steps svg {
  flex-shrink: 0;
}
@media (max-width: 1100px) {
  .registration-steps {
    gap: 12px;
    padding: 18px;
  }
  .registration-steps small {
    display: none;
  }
  .registration-steps li {
    gap: 9px;
  }
  .registration-steps strong {
    font-size: 13px;
  }
}
@media (max-width: 700px) {
  .registration-steps {
    gap: 6px;
    padding: 16px 12px;
    margin-bottom: 18px;
  }
  .registration-steps li {
    flex-direction: column;
    gap: 8px;
    text-align: center;
    flex: 1;
  }
  .registration-steps strong {
    font-size: 12px;
    line-height: 1.5;
  }
  .registration-steps > svg {
    width: 12px;
  }
  .selected-patient {
    flex-wrap: wrap;
  }
  .selected-patient-identity {
    flex: 1;
  }
  .selected-patient-balance {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    width: 100%;
    margin-left: 0;
    padding-top: 13px;
    border-top: 1px solid #dbeafe;
    text-align: left;
  }
  .selected-patient-balance strong {
    margin-top: 0;
  }
  .surgery-item-heading {
    display: none;
  }
  .surgery-item {
    grid-template-columns: minmax(0, 1fr) 28px;
    gap: 12px;
    padding: 14px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    margin-bottom: 12px;
  }
  .surgery-item > .el-select {
    grid-column: 1 / -1;
  }
  .surgery-item > .el-input-number {
    grid-row: 2;
    width: 130px;
  }
  .surgery-item > .icon-button {
    grid-column: 2;
    grid-row: 2;
  }
  .surgery-item > .surgery-price {
    grid-column: 1 / -1;
  }
  .surgery-price::before {
    content: attr(data-label) " / 元：";
    color: #64748b;
    font-size: 12px;
  }
  .quote-sidebar {
    position: static;
  }
}
</style>
