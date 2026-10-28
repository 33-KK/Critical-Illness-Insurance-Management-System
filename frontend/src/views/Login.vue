<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ShieldPlus,
  ArrowRight,
  RefreshCw,
  LockKeyhole,
  UserRound,
  ShieldCheck,
  Stethoscope,
  ClipboardCheck,
  Check,
} from "lucide-vue-next";
import { get, showError } from "../api";
import { login } from "../auth";
const router = useRouter(),
  route = useRoute(),
  formRef = ref(),
  busy = ref(false),
  captchaBusy = ref(false);
const form = reactive({
    username: "admin",
    password: "Demo@2026",
    captchaId: "",
    captchaCode: "",
  }),
  captcha = ref("");
const captchaSrc = computed(() =>
  captcha.value.startsWith("<")
    ? "data:image/svg+xml;charset=utf-8," + encodeURIComponent(captcha.value)
    : captcha.value,
);
const rules = {
  username: [{ required: true, message: "请输入登录账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入登录密码", trigger: "blur" }],
  captchaCode: [{ required: true, message: "请输入图片验证码", trigger: "blur" }],
};
const demoAccounts = [
  { id: "admin", name: "系统管理员", description: "账户与资金管理" },
  { id: "doctor", name: "医生", description: "就诊与费用登记" },
  { id: "reviewer", name: "保险审核员", description: "参保与报销审核" },
];
async function refresh() {
  captchaBusy.value = true;
  try {
    const r = await get("/auth/captcha");
    form.captchaId = r.captchaId;
    captcha.value = r.svg;
    form.captchaCode = "";
  } catch (e) {
    showError(e);
  } finally {
    captchaBusy.value = false;
  }
}
function choose(account: string) {
  form.username = account;
  form.password = "Demo@2026";
  formRef.value?.clearValidate();
}
async function submit() {
  if (busy.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  busy.value = true;
  try {
    await login(form);
    router.replace(typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard");
  } catch (e) {
    showError(e);
    await refresh();
  } finally {
    busy.value = false;
  }
}
onMounted(refresh);
</script>
<template>
  <div class="al-login">
    <header class="al-login-header">
      <a class="al-login-brand" href="/" aria-label="安澜医保首页">
        <span class="al-login-brand-mark"><ShieldPlus :size="25" :stroke-width="1.8" /></span>
        <span>
          <strong>安澜医保</strong>
          <small>大病保险管理平台</small>
        </span>
      </a>
      <span class="al-login-header-note">
        <ShieldCheck :size="16" />
        医保业务协同工作台
      </span>
    </header>

    <main class="al-login-main">
      <section class="al-login-overview" aria-labelledby="platform-heading">
        <div class="al-login-overline">参保 · 就诊 · 审核</div>
        <h1 id="platform-heading">
          大病保险业务，
          <br />
          在这里有序协同。
        </h1>
        <p class="al-login-intro">
          从参保建档到报销审核，让每笔费用清楚可查，让每一步处理有据可依。
        </p>

        <div class="al-login-process" aria-label="平台业务流程">
          <div class="al-login-process-heading">
            <strong>清晰的业务流程</strong>
            <span>全程记录 · 分工协作</span>
          </div>
          <div class="al-login-step">
            <span class="al-login-step-icon"><UserRound :size="20" /></span>
            <div>
              <strong>参保建档</strong>
              <p>维护参保资料，关联保险方案</p>
            </div>
            <span class="al-login-step-number">01</span>
          </div>
          <div class="al-login-step">
            <span class="al-login-step-icon"><Stethoscope :size="20" /></span>
            <div>
              <strong>就诊计费</strong>
              <p>登记诊疗项目，试算报销与自付费用</p>
            </div>
            <span class="al-login-step-number">02</span>
          </div>
          <div class="al-login-step">
            <span class="al-login-step-icon"><ClipboardCheck :size="20" /></span>
            <div>
              <strong>保险审核</strong>
              <p>核查报销申请，跟踪审核与资金流水</p>
            </div>
            <span class="al-login-step-number">03</span>
          </div>
        </div>
        <p class="al-login-overview-note">
          <ShieldCheck :size="17" />
          按角色分配权限，保留业务操作记录
        </p>
      </section>

      <section class="al-login-card" aria-labelledby="login-heading">
        <div class="al-login-card-heading">
          <span class="al-login-card-eyebrow">业务人员入口</span>
          <h2 id="login-heading">登录工作台</h2>
          <p>使用你的工作账号，进入对应业务空间。</p>
        </div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="al-login-form"
          @submit.prevent="submit"
        >
          <el-form-item label="登录账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入登录账号"
              size="large"
              autocomplete="username"
            >
              <template #prefix><UserRound :size="17" /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="登录密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入登录密码"
              size="large"
              autocomplete="current-password"
            >
              <template #prefix><LockKeyhole :size="17" /></template>
            </el-input>
          </el-form-item>
          <el-form-item label="安全验证码" prop="captchaCode">
            <div class="al-login-captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="输入右侧验证码"
                size="large"
                maxlength="8"
                autocomplete="off"
                @keyup.enter="submit"
              />
              <button
                class="al-login-captcha-image"
                type="button"
                title="点击更换验证码"
                :disabled="captchaBusy"
                @click="refresh"
              >
                <img v-if="captchaSrc" :src="captchaSrc" alt="登录验证码" />
                <RefreshCw v-else :size="18" />
              </button>
              <button
                type="button"
                class="al-login-captcha-refresh"
                title="刷新验证码"
                aria-label="刷新验证码"
                :disabled="captchaBusy"
                @click="refresh"
              >
                <RefreshCw :size="17" :class="{ 'al-login-spinning': captchaBusy }" />
              </button>
            </div>
          </el-form-item>
          <el-button
            class="al-login-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="busy"
          >
            登录工作台
            <ArrowRight v-if="!busy" :size="18" />
          </el-button>
        </el-form>
        <div class="al-login-demo">
          <div class="al-login-demo-heading">
            <strong>演示账号</strong>
            <span>点击选择角色</span>
          </div>
          <div class="al-login-demo-options">
            <button
              v-for="a in demoAccounts"
              :key="a.id"
              type="button"
              :class="{ 'al-login-selected': form.username === a.id }"
              :aria-pressed="form.username === a.id"
              :title="`${a.name}：${a.description}`"
              @click="choose(a.id)"
            >
              <strong>{{ a.name }}</strong>
              <span>{{ a.id }}</span>
              <Check v-if="form.username === a.id" :size="13" />
            </button>
          </div>
          <p>
            统一密码
            <code>Demo@2026</code>
            <span>验证码请按图片填写</span>
          </p>
        </div>
      </section>
    </main>

    <footer class="al-login-footer">
      <span>© 2026 安澜医保</span>
      <span>
        <ShieldCheck :size="14" />
        模拟医保业务，仅用于学习与项目展示
      </span>
    </footer>
  </div>
</template>

<style scoped>
.al-login {
  min-height: 100vh;
  color: #172e50;
  background: #f3f7fc;
  font-size: 14px;
  --el-font-size-base: 14px;
}
.al-login-header {
  height: 82px;
  padding: 0 max(40px, calc((100% - 1160px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e3ebf5;
  background: #fff;
}
.al-login-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.al-login-brand-mark {
  display: grid;
  place-items: center;
  height: 43px;
  width: 43px;
  border-radius: 11px;
  background: var(--primary);
  color: #fff;
}
.al-login-brand > span:last-child {
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.al-login-brand strong {
  font-size: 22px;
  letter-spacing: 1px;
}
.al-login-brand small {
  border-left: 1px solid #d9e2ef;
  padding-left: 16px;
  color: #5d6d85;
  font-size: 14px;
}
.al-login-header-note,
.al-login-overview-note,
.al-login-footer > span:last-child {
  display: flex;
  align-items: center;
  gap: 7px;
}
.al-login-header-note {
  color: #65758c;
}
.al-login-header-note > svg {
  color: #3972bc;
}
.al-login-main {
  max-width: 1160px;
  margin: 0 auto;
  min-height: calc(100vh - 136px);
  padding: 36px 24px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: center;
  gap: 90px;
}
.al-login-overview {
  padding: 14px 0 14px 4px;
}
.al-login-overline {
  margin-bottom: 17px;
  color: var(--primary);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 2px;
}
.al-login-overview h1 {
  font-size: 36px;
  line-height: 1.48;
  font-weight: 650;
  letter-spacing: 0;
}
.al-login-intro {
  max-width: 410px;
  margin-top: 17px;
  color: #5a6d87;
  font-size: 15px;
  line-height: 1.9;
}
.al-login-process {
  max-width: 450px;
  margin-top: 27px;
  border: 1px solid #dbe7f7;
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}
.al-login-process-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 17px 20px;
  border-bottom: 1px solid #e9eff7;
  background: #f9fbff;
}
.al-login-process-heading > strong {
  font-size: 14px;
  font-weight: 600;
}
.al-login-process-heading > span {
  color: #6d7f97;
  font-size: 12px;
}
.al-login-step {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 20px;
  padding: 17px 0;
}
.al-login-step + .al-login-step {
  border-top: 1px solid #edf2f8;
}
.al-login-step-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 9px;
  background: #edf4ff;
  color: var(--primary);
  flex-shrink: 0;
}
.al-login-step strong {
  display: block;
  margin-bottom: 5px;
  font-weight: 600;
}
.al-login-step p {
  color: #6c7c92;
  font-size: 14px;
  line-height: 1.5;
}
.al-login-step-number {
  margin-left: auto;
  color: #bdcce0;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.al-login-overview-note {
  margin-top: 20px;
  color: #6a7d96;
  font-size: 13px;
}
.al-login-overview-note > svg {
  color: #4b80c6;
}
.al-login-card {
  padding: 29px 30px 24px;
  border: 1px solid #e0e8f4;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 40px #1f467513;
}
.al-login-card-eyebrow {
  color: var(--primary);
  font-size: 13px;
  font-weight: 600;
}
.al-login-card-heading h2 {
  margin-top: 8px;
  font-size: 26px;
  line-height: 1.4;
  font-weight: 650;
}
.al-login-card-heading p {
  margin-top: 9px;
  color: #6e7d91;
  font-size: 14px;
  line-height: 1.6;
}
.al-login-form {
  margin-top: 25px;
}
.al-login-form :deep(.el-form-item) {
  margin-bottom: 19px;
}
.al-login-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 8px;
  padding: 0;
  color: #334862;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}
.al-login-form :deep(.el-input__wrapper) {
  min-height: 43px;
  padding: 1px 12px;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 0 0 1px #d6e0ed inset;
}
.al-login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #97b8e1 inset;
}
.al-login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}
.al-login-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}
.al-login-form :deep(.el-input__prefix) {
  color: #7e90a8;
}
.al-login-form :deep(.el-input__inner) {
  color: #253e5e;
  font-size: 14px;
}
.al-login-form :deep(.el-input__inner::placeholder) {
  color: #8b9ab0;
}
.al-login-captcha-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 9px;
}
.al-login-captcha-row > .el-input {
  min-width: 0;
  flex: 1;
}
.al-login-captcha-image {
  width: 111px;
  height: 43px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 1px solid #d6e0ed;
  border-radius: 7px;
  color: #4a74aa;
  background: #f6f9ff;
  overflow: hidden;
  flex-shrink: 0;
}
.al-login-captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.al-login-captcha-refresh {
  display: grid;
  place-items: center;
  padding: 0;
  height: 36px;
  width: 26px;
  border: 0;
  background: transparent;
  color: #577aa8;
  flex-shrink: 0;
}
.al-login-captcha-refresh:hover {
  color: var(--primary);
}
.al-login-captcha-refresh:disabled,
.al-login-captcha-image:disabled {
  cursor: wait;
  opacity: 0.65;
}
.al-login-submit.el-button {
  width: 100%;
  height: 44px;
  margin-top: 3px;
  border-radius: 7px;
  font-size: 15px;
  font-weight: 600;
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary-dark);
  --el-button-hover-border-color: var(--primary-dark);
  --el-button-active-bg-color: var(--primary-dark);
  --el-button-active-border-color: var(--primary-dark);
}
.al-login-submit :deep(span) {
  gap: 10px;
}
.al-login-demo {
  margin-top: 25px;
  padding-top: 19px;
  border-top: 1px solid #e8eef6;
}
.al-login-demo-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;
}
.al-login-demo-heading strong {
  font-size: 13px;
  font-weight: 600;
  color: #4a5e7a;
}
.al-login-demo-heading > span {
  color: #7a8aa1;
  font-size: 12px;
}
.al-login-demo-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.al-login-demo-options button {
  position: relative;
  padding: 11px 5px 10px;
  border: 1px solid #dce5f1;
  border-radius: 7px;
  color: #4a5f7b;
  background: #fff;
  transition:
    border-color 0.15s,
    background 0.15s;
  text-align: center;
}
.al-login-demo-options button:hover {
  border-color: #8ab1e5;
  background: #f5f9ff;
}
.al-login-demo-options button.al-login-selected {
  color: var(--primary-dark);
  border-color: #72a4e5;
  background: var(--primary-soft);
}
.al-login-demo-options strong {
  display: block;
  font-size: 14px;
  font-weight: 500;
}
.al-login-demo-options button > span {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  color: #72849d;
}
.al-login-demo-options button > svg {
  position: absolute;
  right: 4px;
  bottom: 6px;
  color: var(--primary);
}
.al-login-demo > p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 12px;
  color: #6c7d94;
  font-size: 12px;
  line-height: 1.6;
}
.al-login-demo code {
  color: #3a587e;
  font-family: Consolas, monospace;
  font-size: 12px;
}
.al-login-demo > p > span {
  margin-left: auto;
}
.al-login-footer {
  min-height: 54px;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 25px;
  padding: 15px 24px;
  color: #75869d;
  font-size: 12px;
}
.al-login button:focus-visible,
.al-login a:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
.al-login-spinning {
  animation: al-login-spin 1s linear infinite;
}
@keyframes al-login-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (min-width: 901px) and (max-height: 800px) {
  .al-login-header {
    height: 72px;
  }
  .al-login-main {
    min-height: calc(100vh - 120px);
    padding-top: 22px;
    padding-bottom: 22px;
  }
  .al-login-card {
    padding-top: 22px;
    padding-bottom: 18px;
  }
  .al-login-card-heading h2 {
    margin-top: 6px;
    font-size: 24px;
  }
  .al-login-card-heading p {
    margin-top: 6px;
    font-size: 13px;
  }
  .al-login-form {
    margin-top: 20px;
  }
  .al-login-form :deep(.el-form-item) {
    margin-bottom: 16px;
  }
  .al-login-form :deep(.el-form-item__label) {
    margin-bottom: 5px;
  }
  .al-login-form :deep(.el-input__wrapper) {
    min-height: 42px;
  }
  .al-login-demo {
    margin-top: 18px;
    padding-top: 14px;
  }
  .al-login-demo-options button {
    padding-top: 9px;
    padding-bottom: 8px;
  }
  .al-login-demo-options button > span {
    margin-top: 4px;
  }
  .al-login-footer {
    min-height: 48px;
    padding-top: 12px;
    padding-bottom: 12px;
  }
}
@media (max-width: 1100px) {
  .al-login-main {
    gap: 45px;
    padding-left: 35px;
    padding-right: 35px;
    grid-template-columns: minmax(0, 1fr) 400px;
  }
  .al-login-overview h1 {
    font-size: 31px;
  }
  .al-login-process-heading {
    padding-left: 16px;
    padding-right: 16px;
  }
  .al-login-process-heading > span {
    display: none;
  }
  .al-login-step {
    margin-left: 16px;
    margin-right: 16px;
    gap: 10px;
  }
  .al-login-step-number {
    display: none;
  }
}
@media (max-width: 900px) {
  .al-login-header {
    padding: 0 28px;
    height: 76px;
  }
  .al-login-header-note {
    display: none;
  }
  .al-login-main {
    min-height: calc(100vh - 130px);
    grid-template-columns: minmax(0, 420px);
    justify-content: center;
    gap: 24px;
    padding: 30px 24px;
  }
  .al-login-overview {
    padding: 0;
  }
  .al-login-overline {
    font-size: 12px;
    margin-bottom: 7px;
  }
  .al-login-overview h1 {
    font-size: 24px;
    line-height: 1.5;
  }
  .al-login-overview h1 br {
    display: none;
  }
  .al-login-intro {
    font-size: 14px;
    margin-top: 7px;
  }
  .al-login-process,
  .al-login-overview-note {
    display: none;
  }
}
@media (max-width: 480px) {
  .al-login-header {
    height: 72px;
    padding: 0 22px;
  }
  .al-login-brand {
    gap: 10px;
  }
  .al-login-brand-mark {
    width: 38px;
    height: 38px;
  }
  .al-login-brand > span:last-child {
    display: block;
  }
  .al-login-brand strong {
    display: block;
    font-size: 19px;
  }
  .al-login-brand small {
    display: block;
    margin-top: 3px;
    padding: 0;
    border: 0;
    font-size: 12px;
  }
  .al-login-main {
    display: block;
    padding: 25px 20px 12px;
  }
  .al-login-overview {
    margin-bottom: 22px;
  }
  .al-login-overview h1 {
    font-size: 23px;
  }
  .al-login-intro {
    line-height: 1.75;
  }
  .al-login-card {
    padding: 25px 21px 22px;
    border-radius: 12px;
  }
  .al-login-card-heading h2 {
    font-size: 24px;
  }
  .al-login-card-heading p {
    line-height: 1.7;
  }
  .al-login-captcha-row {
    gap: 7px;
  }
  .al-login-captcha-image {
    width: 97px;
  }
  .al-login-captcha-refresh {
    width: 22px;
  }
  .al-login-demo-options {
    gap: 6px;
  }
  .al-login-demo-options strong {
    font-size: 14px;
  }
  .al-login-demo-options button > svg {
    right: 2px;
    bottom: 4px;
    width: 11px;
  }
  .al-login-demo > p {
    row-gap: 5px;
  }
  .al-login-demo > p > span {
    margin-left: 0;
    width: 100%;
  }
  .al-login-footer {
    gap: 7px;
    flex-direction: column;
    line-height: 1.5;
    padding: 18px 16px 22px;
  }
}
@media (max-width: 600px) {
  .al-login-overview {
    display: none;
  }
  .al-login-main {
    display: block;
    max-width: 440px;
    min-height: 0;
    padding: 20px 20px 12px;
  }
  .al-login-card {
    padding: 23px 20px 21px;
  }
  .al-login-card-eyebrow {
    font-size: 14px;
  }
  .al-login-card-heading h2 {
    margin-top: 5px;
  }
  .al-login-card-heading p {
    margin-top: 7px;
    font-size: 14px;
  }
  .al-login-form {
    margin-top: 21px;
  }
  .al-login-form :deep(.el-form-item) {
    margin-bottom: 18px;
  }
  .al-login-form :deep(.el-form-item__label) {
    margin-bottom: 6px;
  }
  .al-login-demo {
    margin-top: 20px;
    padding-top: 16px;
  }
  .al-login-demo-heading {
    margin-bottom: 10px;
  }
  .al-login-demo-heading strong,
  .al-login-demo-heading > span,
  .al-login-demo-options strong,
  .al-login-demo > p,
  .al-login-demo code {
    font-size: 14px;
  }
  .al-login-demo-options button {
    padding-top: 9px;
    padding-bottom: 9px;
  }
  .al-login-demo > p {
    row-gap: 3px;
    margin-top: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .al-login-spinning {
    animation: none;
  }
  .al-login-demo-options button {
    transition: none;
  }
}
</style>
