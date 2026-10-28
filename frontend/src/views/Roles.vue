<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { ShieldCheck, KeyRound, Save, Users, Stethoscope, ClipboardCheck } from "lucide-vue-next";
import { get, put, showError } from "../api";
import type { Row } from "../api";
import { loadUser } from "../auth";
import PageHeader from "../components/PageHeader.vue";
const roles = ref<Row[]>([]),
  permissions = ref<Row[]>([]),
  selected = ref<Row>(),
  selectedCodes = ref<string[]>([]),
  name = ref(""),
  loading = ref(false),
  saving = ref(false);
const groups = computed(() => {
  const m: Record<string, Row[]> = {};
  permissions.value.forEach((p) => (m[p.group || "其他权限"] ??= []).push(p));
  return Object.entries(m);
});
function select(role: Row) {
  selected.value = role;
  name.value = role.name;
  selectedCodes.value = [...(role.permissions || [])];
}
function all(group: Row[]) {
  return group.every((p) => selectedCodes.value.includes(p.code));
}
function indeterminate(group: Row[]) {
  return !all(group) && group.some((p) => selectedCodes.value.includes(p.code));
}
function toggle(group: Row[], checked: boolean) {
  const values = selectedCodes.value.filter((c) => !group.some((p) => p.code === c));
  selectedCodes.value = checked ? [...values, ...group.map((p) => p.code)] : values;
}
function toggleValue(group: Row[], value: unknown) {
  toggle(group, !!value);
}
async function load() {
  loading.value = true;
  try {
    [roles.value, permissions.value] = await Promise.all([
      get("/admin/roles"),
      get("/admin/permissions"),
    ]);
    if (roles.value.length) select(roles.value[0]);
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (saving.value || !selected.value) return;
  if (!name.value.trim()) {
    ElMessage.warning("请填写角色名称");
    return;
  }
  saving.value = true;
  try {
    await put("/admin/roles/" + selected.value.id, {
      name: name.value,
      permissions: selectedCodes.value,
    });
    selected.value.name = name.value;
    selected.value.permissions = [...selectedCodes.value];
    ElMessage.success("角色权限已保存");
    await loadUser();
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <PageHeader
    title="角色权限"
    description="按岗位职责配置访问权限，统一管理菜单可见性与接口操作权限。"
  />
  <div class="roles-grid" v-loading="loading">
    <section class="panel role-list">
      <div class="panel-heading">
        <h3>系统角色</h3>
        <span class="muted">{{ roles.length }} 个</span>
      </div>
      <button
        v-for="role in roles"
        :key="role.id"
        class="role-list-item"
        :class="{ active: selected?.id === role.id }"
        @click="select(role)"
      >
        <span class="role-icon">
          <Users v-if="role.code === 'ADMIN'" :size="21" />
          <Stethoscope v-else-if="role.code === 'DOCTOR'" :size="21" />
          <ClipboardCheck v-else :size="21" />
        </span>
        <div>
          <strong>{{ role.name }}</strong>
          <small>{{ role.code }}</small>
        </div>
        <span class="role-count">{{ role.permissions?.length || 0 }}</span>
      </button>
      <p class="role-hint">
        <ShieldCheck :size="16" />
        角色是权限的集合，账户通过角色获得访问能力。
      </p>
    </section>
    <section v-if="selected" class="panel permission-panel">
      <div class="panel-heading">
        <div>
          <h3>{{ selected.name }} · 权限配置</h3>
          <p>已选择 {{ selectedCodes.length }} 项权限</p>
        </div>
        <el-button type="primary" :loading="saving" @click="save">
          <Save :size="15" />
          保存权限
        </el-button>
      </div>
      <el-form label-position="top">
        <el-form-item label="角色名称">
          <el-input v-model="name" style="max-width: 360px" maxlength="50" />
        </el-form-item>
      </el-form>
      <div v-for="[group, items] in groups" :key="group" class="permission-group">
        <div class="permission-group-heading">
          <el-checkbox
            :model-value="all(items)"
            :indeterminate="indeterminate(items)"
            @change="(v: unknown) => toggleValue(items, v)"
          >
            {{ group }}
          </el-checkbox>
          <span>{{ items.length }} 项权限</span>
        </div>
        <el-checkbox-group v-model="selectedCodes" class="permission-checkboxes">
          <el-checkbox v-for="p in items" :key="p.code" :value="p.code">
            <span>
              {{ p.name }}
              <small>{{ p.code }}</small>
            </span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
    </section>
  </div>
</template>
