<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { UserRound, ShieldCheck, LockKeyhole, Save, Building2, Mail, Phone } from "lucide-vue-next";
import { put, showError } from "../api";
import { auth, loadUser, roleLabels, clearAuth } from "../auth";
import PageHeader from "../components/PageHeader.vue";
const profile = reactive({
    displayName: auth.user?.displayName || "",
    phone: auth.user?.phone || "",
    email: auth.user?.email || "",
  }),
  password = reactive({ oldPassword: "", newPassword: "", confirm: "" }),
  saving = ref(false),
  passwordBusy = ref(false),
  profileRef = ref(),
  passwordRef = ref();
const router = useRouter();
const profileRules = {
    displayName: [{ required: true, message: "请输入姓名", trigger: "blur" }],
    email: [{ type: "email", message: "请输入正确的邮箱地址", trigger: "blur" }],
  },
  passwordRules = {
    oldPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
    newPassword: [
      { required: true, message: "请输入新密码", trigger: "blur" },
      { min: 8, message: "新密码至少8位", trigger: "blur" },
    ],
    confirm: [
      { required: true, message: "请再次输入新密码", trigger: "blur" },
      {
        validator: (_r: any, v: string, cb: any) =>
          v === password.newPassword ? cb() : cb(new Error("两次输入的密码不一致")),
        trigger: "blur",
      },
    ],
  };
async function save() {
  if (saving.value) return;
  try {
    await profileRef.value.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await put("/auth/profile", profile);
    await loadUser();
    ElMessage.success("个人信息已更新");
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
async function changePassword() {
  if (passwordBusy.value) return;
  try {
    await passwordRef.value.validate();
  } catch {
    return;
  }
  passwordBusy.value = true;
  try {
    await put("/auth/password", {
      oldPassword: password.oldPassword,
      newPassword: password.newPassword,
    });
    clearAuth();
    ElMessage.success("登录密码已修改，请使用新密码重新登录");
    router.replace("/login");
  } catch (e) {
    showError(e);
  } finally {
    passwordBusy.value = false;
  }
}
</script>
<template>
  <PageHeader title="个人中心" description="维护您的个人资料与账户安全信息。" />
  <div class="profile-grid">
    <section class="panel profile-identity">
      <span class="profile-avatar">{{ auth.user?.displayName?.slice(-2) }}</span>
      <h2>{{ auth.user?.displayName }}</h2>
      <span class="subtle-pill">{{ roleLabels[auth.user?.role || ""] }}</span>
      <div class="profile-identity-info">
        <div>
          <UserRound :size="16" />
          <span>登录账号</span>
          <strong>{{ auth.user?.username }}</strong>
        </div>
        <div>
          <Building2 :size="16" />
          <span>所属组织</span>
          <strong>{{ auth.user?.orgName || "未分配" }}</strong>
        </div>
        <div>
          <ShieldCheck :size="16" />
          <span>授权权限</span>
          <strong>{{ auth.user?.permissions?.length || 0 }} 项</strong>
        </div>
      </div>
      <p class="form-help">如需调整角色与组织，请联系系统管理员。</p>
    </section>
    <div>
      <section class="panel form-panel">
        <div class="panel-heading">
          <div>
            <h3>个人资料</h3>
            <p>您的基本联系信息</p>
          </div>
          <UserRound :size="21" class="muted" />
        </div>
        <el-form ref="profileRef" :model="profile" :rules="profileRules" label-position="top">
          <div class="form-grid">
            <el-form-item label="姓名" prop="displayName">
              <el-input v-model="profile.displayName" maxlength="30" />
            </el-form-item>
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="profile.phone" maxlength="15" />
            </el-form-item>
            <el-form-item label="电子邮箱" prop="email" class="grid-full">
              <el-input v-model="profile.email" maxlength="150" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="saving" @click="save">
            <Save :size="15" />
            保存个人信息
          </el-button>
        </el-form>
      </section>
      <section class="panel form-panel mt24">
        <div class="panel-heading">
          <div>
            <h3>账户安全</h3>
            <p>定期更新密码，保障账户安全</p>
          </div>
          <LockKeyhole :size="21" class="muted" />
        </div>
        <el-form ref="passwordRef" :model="password" :rules="passwordRules" label-position="top">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="password.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="password.newPassword"
                type="password"
                show-password
                placeholder="至少8位"
                autocomplete="new-password"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirm">
              <el-input
                v-model="password.confirm"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </el-form-item>
          </div>
          <el-button type="primary" plain :loading="passwordBusy" @click="changePassword">
            修改密码
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>
